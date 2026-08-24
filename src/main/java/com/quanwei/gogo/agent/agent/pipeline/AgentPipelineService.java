package com.quanwei.gogo.agent.agent.pipeline;

import com.alibaba.fastjson2.JSON;
import com.quanwei.gogo.agent.agent.baseagent.IntentRecognitionAgent;
import com.quanwei.gogo.agent.agent.baseagent.QueryRewritingAgent;
import com.quanwei.gogo.agent.agent.core.AgentContext;
import com.quanwei.gogo.agent.agent.core.AgentRegistry;
import com.quanwei.gogo.agent.agent.core.AgentResult;
import com.quanwei.gogo.agent.agent.core.MasterAgentContextHolder;
import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;
import com.quanwei.gogo.agent.agent.enums.IntentCategory;
import com.quanwei.gogo.agent.agent.enums.IntentLevelEnum;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionResult;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionRouter;
import com.quanwei.gogo.agent.agent.intent.IntentResultParser;
import com.quanwei.gogo.agent.agent.prompt.PromptLoader;
import com.quanwei.gogo.agent.agent.rewrite.QueryRewriteParser;
import com.quanwei.gogo.agent.agent.rewrite.QueryRewriteResult;
import com.quanwei.gogo.agent.entity.ChatMessage;
import com.quanwei.gogo.agent.service.ChatHistoryService;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 问题改写 + 意图识别的流水线编排。
 *
 * <p><b>核心设计：把改写放在意图识别之后，而不是之前。</b>
 *
 * <pre>
 * ① 原始问题走 L0→L2 快路径（不碰 L3）
 *      命中 ──→ 直接调度，改写和 L3 两次模型调用都省了
 *      未命中 ↓
 * ② 调改写模型，把指代、省略补全（无历史时跳过 —— 没有上文就没有指代可消解）
 * ③ 拿改写后的问题走完整识别（智能体内部再跑一次 L1/L2，不行才 L3）
 * ④ 调度给 MasterAgent（预留，bean 尚未存在时到此为止）
 * </pre>
 *
 * <p>直觉上「先改写再识别」更顺，但那样每一句话都要先付一次改写的模型调用 ——
 * 而「我的报销进度到哪了」这类高频、表达清晰的问题，L1 一条正则 1ms 就判完了，
 * 它根本不需要改写。顺序反过来之后，这部分流量的模型调用次数从 2 次降到 0 次。
 *
 * <p>代价是：带指代的追问（「那个多少钱」）会先白跑一次快路径。但快路径的成本是
 * L1 的正则加 L2 的一次 embedding，和一次大模型调用比可以忽略，这笔账是划算的。
 *
 * <p>全链路非阻塞：快路径里的 embedding 调用是同步 HTTP，用 {@code boundedElastic} 挪出去，
 * 不占 Netty 的事件循环线程。
 *
 * <p>全流程不抛异常。改写失败退回原文继续走，识别失败返回 unknown 交给 MasterAgent 追问 ——
 * 单个环节挂掉不能让整轮对话失败。
 */
@Slf4j
@Service
public class AgentPipelineService {

    /**
     * 智能体的 <b>bean 名</b>，不是 {@link AgentNameEnum} 里的展示名。
     * 两者刻意不复用：那个是落 chat_message.agent_name 的人类可读名（PascalCase），
     * 这个是 {@code @Component("...")} 声明的容器标识（camelCase），拿错了 getBean 直接抛。
     */
    private static final String QUERY_REWRITING_AGENT = "queryRewritingAgent";

    private static final String INTENT_RECOGNITION_AGENT = "intentRecognitionAgent";

    /** 预留：MasterAgent 还没实现，容器里没有这个 bean 时流水线到意图识别为止 */
    private static final String MASTER_AGENT = "masterAgent";

    /** 改写用户提示词模板，占位符 {{history}} / {{query}} 由本类填充 */
    private static final String REWRITE_USER_PROMPT = "query-rewriting-agent-user.md";

    /** 进提示词的历史轮数。太多会稀释指代信号，也拉高首字延迟 */
    private static final int RECENT_MESSAGE_LIMIT = 10;

    /** 每条历史的截断长度，防止一条长回复把提示词撑爆 */
    private static final int HISTORY_MAX_CHARS = 200;

    /** AgentScope 被中断后返回的默认英文恢复文本，当前版本不一定带 INTERRUPTED 标记，只能兜底认文本 */
    private static final String DEFAULT_INTERRUPT_RECOVERY_TEXT =
            "I noticed that you have interrupted me. What can I do for you?";

    @Autowired
    private AgentRegistry agentRegistry;

    @Autowired
    private IntentRecognitionRouter intentRecognitionRouter;

    @Autowired
    private PromptLoader promptLoader;

    @Autowired
    private ChatHistoryService chatHistoryService;

    /**
     * 跑一遍流水线。
     *
     * <p>入参 {@link AgentContext} 会被就地修改：写回 {@code rewrittenQuery}，
     * 并往 {@code agentTrace} 里追加各环节的执行记录（供落 chat_message.extra 排查）。
     *
     * @param context 至少要有 rawQuery；history 为空时会按 conversationId 去 chat_message 捞
     * @return 永不为 null，永不抛异常
     */
    public Mono<PipelineResult> run(AgentContext context) {
        long start = System.currentTimeMillis();

        String raw = context == null ? null : context.getRawQuery();
        if (raw == null || raw.isBlank()) {
            return Mono.just(PipelineResult.of(
                    IntentRecognitionResult.unknown("用户输入为空"),
                    QueryRewriteResult.unchanged("", "输入为空，无需改写"),
                    false, 0, 0L));
        }
        String question = raw.trim();
        // 先把改写结果置成原文：后面任何一步提前返回，下游读 effectiveQuery 都拿得到东西
        context.setRewrittenQuery(question);

        // ---------- ① 原始问题走快路径（L0→L2），不触发 L3 ----------
        // route 里 L2 会发一次同步 HTTP，必须挪到 boundedElastic，别占事件循环线程
        return Mono.fromCallable(() -> safeRoute(question))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(fastHit -> {
                    if (fastHit.isPresent()) {
                        IntentRecognitionResult intent = fastHit.get();
                        log.info("[PIPELINE] L1/L2 命中，跳过问题改写，直接调度：source={} intent={}",
                                intent.getSource().getCode(), intent.getPrimaryIntent());
                        context.addTrace(AgentResult.skip(AgentNameEnum.QUERY_REWRITE, "快路径已定论，无需改写"));
                        context.addTrace(AgentResult.ok(AgentNameEnum.INTENT_RECOGNITION, intent,
                                System.currentTimeMillis() - start));
                        PipelineResult result = PipelineResult.of(intent,
                                QueryRewriteResult.unchanged(question, "快路径已定论，未触发改写"),
                                false, 0, System.currentTimeMillis() - start);
                        return dispatchToMaster(result, context);
                    }
                    log.info("[PIPELINE] L1/L2 未命中，进入问题改写后重新识别流程");
                    return rewriteThenRecognize(context, question, start);
                })
                .onErrorResume(e -> {
                    log.error("[PIPELINE] 流水线异常，返回 unknown。原因：{}", e.getMessage(), e);
                    return Mono.just(PipelineResult.of(
                            IntentRecognitionResult.unknown("流水线异常：" + e.getMessage()),
                            QueryRewriteResult.unchanged(question, "流水线异常"),
                            false, 0, System.currentTimeMillis() - start));
                });
    }

    /**
     * L1/L2 未命中时的流程：问题改写 → 重新意图识别（此时走完整 L1/L2/L3）→ 调度。
     */
    private Mono<PipelineResult> rewriteThenRecognize(AgentContext context, String question, long start) {
        List<Msg> history = loadHistory(context);

        // 无历史直接跳过改写：改写解决的是「那个多少钱」里的指代，
        // 首轮没有上文可指，调了也只是原样返回，白花一次模型调用
        if (history.isEmpty()) {
            log.debug("[PIPELINE] 无历史对话，跳过改写");
            context.addTrace(AgentResult.skip(AgentNameEnum.QUERY_REWRITE, "首轮对话，无指代可消解"));
            QueryRewriteResult unchanged = QueryRewriteResult.unchanged(question, "首轮对话，未触发改写");
            return recognizeThenDispatch(context, unchanged, 0, start);
        }

        long rewriteStart = System.currentTimeMillis();
        QueryRewritingAgent rewritingAgent =
                agentRegistry.getAgent(QUERY_REWRITING_AGENT, QueryRewritingAgent.class);

        return rewritingAgent.call(buildRewriteInput(history, question))
                .flatMap(rewriteMsg -> {
                    if (isInterrupted(rewriteMsg)) {
                        log.info("[PIPELINE] 问题改写被中断，终止流水线");
                        return Mono.just(PipelineResult.interrupted(
                                QueryRewriteResult.unchanged(question, "用户中断"),
                                1, System.currentTimeMillis() - start));
                    }
                    QueryRewriteResult rewrite =
                            QueryRewriteParser.parse(rewriteMsg.getTextContent(), question);
                    long cost = System.currentTimeMillis() - rewriteStart;
                    log.info("[PIPELINE] 问题改写完成，changed={} cost={}ms「{}」→「{}」",
                            rewrite.rewritten(), cost, question, rewrite.rewrittenQuery());
                    context.setRewrittenQuery(rewrite.rewrittenQuery());
                    context.addTrace(AgentResult.ok(AgentNameEnum.QUERY_REWRITE, rewrite, cost));
                    return recognizeThenDispatch(context, rewrite, 1, start);
                })
                .onErrorResume(e -> {
                    long cost = System.currentTimeMillis() - rewriteStart;
                    log.warn("[PIPELINE] 问题改写失败，退回原文继续识别。原因：{}", e.getMessage());
                    context.addTrace(AgentResult.fail(AgentNameEnum.QUERY_REWRITE, e.getMessage(), cost));
                    QueryRewriteResult fallback =
                            QueryRewriteResult.unchanged(question, "改写失败，使用原问题：" + e.getMessage());
                    return recognizeThenDispatch(context, fallback, 1, start);
                });
    }

    /**
     * 意图识别 + 调度。
     *
     * <p>改写<b>真的改了文本</b>时走智能体的 {@code call}：它内部会先拿新文本再跑一遍 L1/L2，
     * 表达变清晰之后很可能就命中了，省掉 L3。
     *
     * <p>改写没改动文本时直接走 {@code callL3}：同一段文本在第 ① 步已经跑过快路径且未命中，
     * 再跑一遍结果必然一样，纯属白花一次 embedding 调用。
     */
    private Mono<PipelineResult> recognizeThenDispatch(AgentContext context, QueryRewriteResult rewrite,
                                                       int llmCalls, long start) {
        String question = rewrite.rewrittenQuery();
        long recognizeStart = System.currentTimeMillis();
        IntentRecognitionAgent recognitionAgent =
                agentRegistry.getAgent(INTENT_RECOGNITION_AGENT, IntentRecognitionAgent.class);
        List<Msg> intentInput = buildIntentInput(question);

        Mono<Msg> recognition = rewrite.rewritten()
                ? recognitionAgent.call(intentInput)
                : recognitionAgent.callL3(intentInput);
        if (!rewrite.rewritten()) {
            log.debug("[PIPELINE] 改写未改变文本，跳过重复的快路径，直接进 L3");
        }

        return recognition
                .flatMap(intentMsg -> {
                    if (isInterrupted(intentMsg)) {
                        log.info("[PIPELINE] 意图识别被中断，终止流水线");
                        return Mono.just(PipelineResult.interrupted(rewrite, llmCalls,
                                System.currentTimeMillis() - start));
                    }
                    IntentRecognitionResult intent =
                            IntentResultParser.parse(intentMsg.getTextContent(), IntentLevelEnum.L3);
                    long cost = System.currentTimeMillis() - recognizeStart;
                    log.info("[PIPELINE] 意图识别完成：source={} intent={} multi={} target={} cost={}ms",
                            intent.getSource().getCode(), intent.getPrimaryIntent(),
                            intent.isMultiIntent(), intent.getTargetAgent(), cost);

                    context.addTrace(intent.getPrimary() == IntentCategory.UNKNOWN
                            ? AgentResult.fail(AgentNameEnum.INTENT_RECOGNITION, intent.getOverallReason(), cost)
                            : AgentResult.ok(AgentNameEnum.INTENT_RECOGNITION, intent, cost));

                    // 识别真走到 L3 才算多一次模型调用；命中 L1/L2 时智能体没调模型
                    int totalCalls = intent.getSource() == IntentLevelEnum.L3 ? llmCalls + 1 : llmCalls;
                    PipelineResult result = PipelineResult.of(intent, rewrite,
                            llmCalls > 0, totalCalls, System.currentTimeMillis() - start);
                    return dispatchToMaster(result, context);
                })
                .onErrorResume(e -> {
                    long cost = System.currentTimeMillis() - recognizeStart;
                    log.warn("[PIPELINE] 意图识别失败，返回 unknown 交给主智能体追问。原因：{}", e.getMessage());
                    context.addTrace(AgentResult.fail(AgentNameEnum.INTENT_RECOGNITION, e.getMessage(), cost));
                    return Mono.just(PipelineResult.of(
                            IntentRecognitionResult.unknown("意图识别失败：" + e.getMessage()),
                            rewrite, llmCalls > 0, llmCalls, System.currentTimeMillis() - start));
                });
    }

    /**
     * 调度给 MasterAgent。
     *
     * <p>按 bean 名查，容器里没有就把识别结果原样返回，流水线到意图识别为止 ——
     * 这条降级留着，让 MasterAgent 可以被单独摘掉而不影响前面几步的调试。
     *
     * <p>MasterAgent 由 {@code MasterAgentConfig} 的 {@code @Bean} 工厂方法产出，
     * 不是 {@code @Component}：它的构造器要一个非 bean 的 {@link AgentContext}，
     * 只能靠下面那段 ThreadLocal 投影递进去。
     *
     * <p>入参形态也一并定死了：改写结果和意图 JSON 各作为一条 SYSTEM 消息垫在原始对话之前。
     * 意图给的是 {@code toJsonMap()} 的产出，和 L3 提示词约定的 schema 一致 ——
     * MasterAgent 不需要知道这份 JSON 是规则、向量还是模型给的。
     */
    private Mono<PipelineResult> dispatchToMaster(PipelineResult result, AgentContext context) {
        if (!agentRegistry.contains(MASTER_AGENT)) {
            log.debug("[PIPELINE] masterAgent 尚未接入，流水线到意图识别为止，intent={}",
                    result.intent().getPrimaryIntent());
            return Mono.just(result);
        }

        // 上下文投影：MasterAgent 的构造器要 AgentContext，但它不是 bean，递不进 getBean。
        // set → getBean → clear 三步必须同步、同线程、clear 放 finally，
        // 三条约束的来由见 MasterAgentContextHolder 的类注释。
        // 注意 clear 必须在 getBean 之后立刻做，而不是等 call() 的 Mono 完成 ——
        // 那个 Mono 由 Reactor 调度，回调时早就换线程了，届时清的是别人的槽位。
        AgentBase masterAgent;
        MasterAgentContextHolder.set(context);
        try {
            masterAgent = agentRegistry.getAgent(MASTER_AGENT, AgentBase.class);
        } finally {
            MasterAgentContextHolder.clear();
        }

        return masterAgent.call(buildMasterInput(context, result))
                .map(masterReply -> {
                    if (isInterrupted(masterReply)) {
                        log.info("[PIPELINE] MasterAgent 被中断");
                        return result.withMasterReply(masterReply);
                    }
                    if (masterReply.getGenerateReason() == GenerateReason.TOOL_SUSPENDED) {
                        log.info("[PIPELINE] MasterAgent 进入等待用户输入状态，conversationId={}",
                                context.getConversationId());
                    }
                    return result.withMasterReply(masterReply);
                })
                .onErrorResume(e -> {
                    log.warn("[PIPELINE] MasterAgent 调用失败，返回识别结果。原因：{}", e.getMessage());
                    return Mono.just(result);
                });
    }

    /** 快路径（L0→L2）。路由器自己已经吞了分类器异常，这里再兜一层防它初始化阶段就出问题 */
    private Optional<IntentRecognitionResult> safeRoute(String question) {
        try {
            return intentRecognitionRouter.route(question);
        } catch (Exception e) {
            log.warn("[PIPELINE] 快路径异常，降级到改写 + L3。原因：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 加载历史。优先用上下文里已经带的，没有再按 conversationId 去 chat_message 捞 ——
     * 那张表由对话入口统一写入，包含所有角色的消息，是跨智能体最完整的一份上下文。
     */
    private List<Msg> loadHistory(AgentContext context) {
        List<AgentContext.HistoryTurn> turns = context.getHistory();
        if (turns != null && !turns.isEmpty()) {
            return toMsgs(turns);
        }
        if (context.getConversationId() == null || context.getUserId() == null) {
            return List.of();
        }
        try {
            List<ChatMessage> messages =
                    chatHistoryService.listMessages(context.getConversationId(), context.getUserId());
            if (messages == null || messages.isEmpty()) {
                return List.of();
            }
            // listMessages 返回整个会话，这里只取最近 N 条
            List<ChatMessage> recent = messages.size() <= RECENT_MESSAGE_LIMIT
                    ? messages
                    : messages.subList(messages.size() - RECENT_MESSAGE_LIMIT, messages.size());
            List<Msg> msgs = new ArrayList<>(recent.size());
            for (ChatMessage message : recent) {
                if (message.getContent() == null || message.getContent().isBlank()) {
                    continue;
                }
                boolean isUser = "user".equalsIgnoreCase(message.getRole());
                msgs.add(msg(isUser ? MsgRole.USER : MsgRole.ASSISTANT,
                        isUser ? "user" : "assistant", truncate(message.getContent())));
            }
            log.debug("[PIPELINE] 已加载 {} 条历史用于问题改写，conversationId={}",
                    msgs.size(), context.getConversationId());
            return msgs;
        } catch (Exception e) {
            log.warn("[PIPELINE] 加载历史失败，仅用本轮输入。conversationId={}，原因：{}",
                    context.getConversationId(), e.getMessage());
            return List.of();
        }
    }

    private List<Msg> toMsgs(List<AgentContext.HistoryTurn> turns) {
        List<AgentContext.HistoryTurn> recent = turns.size() <= RECENT_MESSAGE_LIMIT
                ? turns
                : turns.subList(turns.size() - RECENT_MESSAGE_LIMIT, turns.size());
        List<Msg> msgs = new ArrayList<>(recent.size());
        for (AgentContext.HistoryTurn turn : recent) {
            boolean isUser = "user".equalsIgnoreCase(turn.role());
            msgs.add(msg(isUser ? MsgRole.USER : MsgRole.ASSISTANT,
                    isUser ? "user" : "assistant", truncate(turn.content())));
        }
        return msgs;
    }

    /**
     * 构建改写智能体的输入：把历史和本轮问题填进用户提示词模板。
     *
     * <p>用模板而不是直接把历史当消息列表传：改写的系统提示词和示例都是按
     * 「历史对话：… 最新问题：…」这个形态写的，两边形态一致，模型照着示例做就行。
     */
    private List<Msg> buildRewriteInput(List<Msg> history, String question) {
        StringBuilder rendered = new StringBuilder();
        for (Msg one : history) {
            rendered.append(one.getRole() == MsgRole.USER ? "用户：" : "助手：")
                    .append(one.getTextContent())
                    .append('\n');
        }
        String userPrompt = promptLoader.load(REWRITE_USER_PROMPT)
                .replace("{{history}}", rendered.toString().trim())
                .replace("{{query}}", question);
        return List.of(msg(MsgRole.USER, "user", userPrompt));
    }

    /**
     * 构建意图识别的输入：只有改写后的问题一条，不带历史也不套模板。
     *
     * <p>两个原因：智能体会拿最后一条用户消息去跑 L1/L2，套上模板会让正则和向量都吃到模板噪音；
     * 而 L3 的系统提示词里写明「rewritten_question 已作为当前用户消息传入」，
     * 传原文本身正是它期待的形态。
     */
    private List<Msg> buildIntentInput(String question) {
        return List.of(msg(MsgRole.USER, "user", question));
    }

    /** 构建 MasterAgent 的输入：改写结果和意图 JSON 作为 SYSTEM 上下文垫在用户问题之前 */
    private List<Msg> buildMasterInput(AgentContext context, PipelineResult result) {
        List<Msg> messages = new ArrayList<>();
        messages.add(msg(MsgRole.SYSTEM, "system", "问题改写结果：\n" + result.effectiveQuery()));
        messages.add(msg(MsgRole.SYSTEM, "system",
                "意图识别结果：\n" + JSON.toJSONString(result.intent().toJsonMap())));
        messages.add(msg(MsgRole.USER, "user", context.getRawQuery()));
        return messages;
    }

    /**
     * 是否被用户中断。
     *
     * <p>除了看标记还要兜底认文本：AgentScope 当前版本的优雅中断走的是 {@code onErrorResume}，
     * 返回的是一句固定英文提示，不一定带 {@link GenerateReason#INTERRUPTED}。
     */
    private boolean isInterrupted(Msg msg) {
        if (msg == null) {
            return false;
        }
        if (msg.getGenerateReason() == GenerateReason.INTERRUPTED) {
            return true;
        }
        String text = msg.getTextContent();
        return text != null && DEFAULT_INTERRUPT_RECOVERY_TEXT.equals(text.trim());
    }

    private static Msg msg(MsgRole role, String name, String text) {
        return Msg.builder()
                .role(role)
                .name(name)
                .content(TextBlock.builder().text(text == null ? "" : text).build())
                .build();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= HISTORY_MAX_CHARS ? text : text.substring(0, HISTORY_MAX_CHARS) + "...";
    }
}
