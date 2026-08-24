package com.quanwei.gogo.agent.agent.pipeline;

import com.quanwei.gogo.agent.agent.baseagent.IntentRecognitionAgent;
import com.quanwei.gogo.agent.agent.baseagent.QueryRewritingAgent;
import com.quanwei.gogo.agent.agent.core.AgentContext;
import com.quanwei.gogo.agent.agent.core.AgentResult;
import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;
import com.quanwei.gogo.agent.agent.enums.IntentCategory;
import com.quanwei.gogo.agent.agent.enums.IntentLevelEnum;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionResult;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionRouter;
import com.quanwei.gogo.agent.agent.intent.IntentResultParser;
import com.quanwei.gogo.agent.agent.prompt.PromptLoader;
import com.quanwei.gogo.agent.agent.rewrite.QueryRewriteParser;
import com.quanwei.gogo.agent.agent.rewrite.QueryRewriteResult;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
 *      命中 ──→ 直接返回，改写和 L3 两次模型调用都省了
 *      未命中 ↓
 * ② 调改写模型，把指代、省略补全（历史为空时跳过 —— 没有上文就没有指代可消解）
 * ③ 改写后的问题<b>真的变了</b>才重跑一次快路径
 *      命中 ──→ 返回，省掉 L3
 *      未命中 ↓
 * ④ L3 大模型兜底，负责复杂意图和多意图
 * </pre>
 *
 * <p>直觉上「先改写再识别」更顺，但那样每一句话都要先付一次改写的模型调用 ——
 * 而「我的报销进度到哪了」这类高频、表达清晰的问题，L1 一条正则 1ms 就判完了，
 * 它根本不需要改写。顺序反过来之后，这部分流量的模型调用次数从 2 次降到 0 次。
 *
 * <p>代价是：带指代的追问（「那个多少钱」）会先白跑一次快路径。但快路径的成本是
 * L1 的正则加 L2 的一次 embedding，和一次大模型调用比可以忽略，这笔账是划算的。
 *
 * <p>第 ③ 步的「真的变了才重跑」很关键：改写模型判定与历史无关时会原样返回，
 * 这时拿同一段文本再走一遍快路径，结果必然还是未命中，纯属白花一次 embedding 调用。
 *
 * <p>全流程不抛异常。改写失败退回原文继续走，L3 失败返回 unknown 交给 masterAgent 追问 ——
 * 单个环节挂掉不能让整轮对话失败。
 */
@Slf4j
@Service
public class AgentPipelineService {

    /** 改写用户提示词模板，占位符 {{history}} / {{query}} 由本类填充 */
    private static final String REWRITE_USER_PROMPT = "query-rewriting-agent-user.md";

    /** 进提示词的历史轮数。太多会稀释指代信号，也拉高首字延迟 */
    private static final int HISTORY_MAX_TURNS = 6;

    /** 每条历史的截断长度，防止一条长回复把提示词撑爆 */
    private static final int HISTORY_MAX_CHARS = 200;

    /** 改写整体超时。智能体内部还有一层 8s，这里留出余量兜住调度开销 */
    private static final Duration REWRITE_TIMEOUT = Duration.ofSeconds(10);

    /** L3 整体超时，同上，智能体内部是 10s */
    private static final Duration L3_TIMEOUT = Duration.ofSeconds(12);

    @Autowired
    private IntentRecognitionRouter router;

    @Autowired
    private PromptLoader promptLoader;

    /**
     * 两个智能体都是 prototype 作用域，必须用 ObjectProvider 每次取新实例。
     * 直接注入拿到的是同一个单例，而 AgentBase 内部有中断标志这类可变状态，
     * 并发请求共用一个实例会互相干扰。
     */
    @Autowired
    private ObjectProvider<QueryRewritingAgent> rewriteAgentProvider;

    @Autowired
    private ObjectProvider<IntentRecognitionAgent> intentAgentProvider;

    /**
     * 跑一遍流水线。
     *
     * <p>入参 {@link AgentContext} 会被就地修改：写回 {@code rewrittenQuery}，
     * 并往 {@code agentTrace} 里追加各环节的执行记录（供落 chat_message.extra 排查）。
     *
     * @param context 至少要有 rawQuery；history 为空时改写会被跳过
     * @return 永不为 null，永不抛异常
     */
    public PipelineResult run(AgentContext context) {
        long start = System.currentTimeMillis();

        String raw = context == null ? null : context.getRawQuery();
        if (raw == null || raw.isBlank()) {
            return new PipelineResult(
                    IntentRecognitionResult.unknown("用户输入为空"),
                    QueryRewriteResult.unchanged("", "输入为空，无需改写"),
                    false, 0, 0L);
        }
        String query = raw.trim();
        // 先把改写结果置成原文：后面任何一步提前返回，下游读 effectiveQuery 都拿得到东西
        context.setRewrittenQuery(query);

        // ---------- ① 原始问题走快路径，不碰 L3 ----------
        long fastStart = System.currentTimeMillis();
        Optional<IntentRecognitionResult> fastHit = safeRoute(query);
        if (fastHit.isPresent()) {
            IntentRecognitionResult intent = fastHit.get();
            log.info("[PIPELINE] 原始问题快路径命中，source={} intent={}，跳过改写与 L3",
                    intent.getSource().getCode(), intent.getPrimaryIntent());
            context.addTrace(AgentResult.skip(AgentNameEnum.QUERY_REWRITE, "快路径已定论，无需改写"));
            context.addTrace(AgentResult.ok(AgentNameEnum.INTENT_RECOGNITION, intent,
                    System.currentTimeMillis() - fastStart));
            return new PipelineResult(intent, QueryRewriteResult.unchanged(query, "快路径已定论，未触发改写"),
                    false, 0, System.currentTimeMillis() - start);
        }

        // ---------- ② 快路径没定论，这才值得花一次改写 ----------
        QueryRewriteResult rewrite = rewrite(context, query);
        context.setRewrittenQuery(rewrite.rewrittenQuery());
        // 首轮对话会跳过改写，那次不算模型调用
        boolean rewriteInvoked = isRewriteInvoked(context);
        int llmCalls = rewriteInvoked ? 1 : 0;

        // ---------- ③ 改写真的改了，才值得重跑快路径 ----------
        if (rewrite.rewritten()) {
            long secondStart = System.currentTimeMillis();
            Optional<IntentRecognitionResult> secondHit = safeRoute(rewrite.rewrittenQuery());
            if (secondHit.isPresent()) {
                IntentRecognitionResult intent = secondHit.get();
                log.info("[PIPELINE] 改写后快路径命中，source={} intent={}，省掉一次 L3",
                        intent.getSource().getCode(), intent.getPrimaryIntent());
                context.addTrace(AgentResult.ok(AgentNameEnum.INTENT_RECOGNITION, intent,
                        System.currentTimeMillis() - secondStart));
                return new PipelineResult(intent, rewrite, rewriteInvoked, llmCalls,
                        System.currentTimeMillis() - start);
            }
        } else {
            log.debug("[PIPELINE] 改写未改变文本，跳过重复的快路径，直接进 L3");
        }

        // ---------- ④ L3 兜底 ----------
        long l3Start = System.currentTimeMillis();
        IntentRecognitionResult intent = callL3(context, rewrite.rewrittenQuery());
        context.addTrace(intent.getPrimary() == IntentCategory.UNKNOWN
                ? AgentResult.fail(AgentNameEnum.INTENT_RECOGNITION, intent.getOverallReason(),
                        System.currentTimeMillis() - l3Start)
                : AgentResult.ok(AgentNameEnum.INTENT_RECOGNITION, intent,
                        System.currentTimeMillis() - l3Start));

        return new PipelineResult(intent, rewrite, rewriteInvoked, llmCalls + 1,
                System.currentTimeMillis() - start);
    }

    /**
     * 快路径（L0→L2）。
     *
     * <p>路由器自己已经吞了分类器的异常，这里再兜一层是防它初始化阶段就出问题
     * （比如种子没加载完）—— 快路径是加速手段，它挂了最多是慢一点，不该中断链路。
     */
    private Optional<IntentRecognitionResult> safeRoute(String query) {
        try {
            return router.route(query);
        } catch (Exception e) {
            log.warn("[PIPELINE] 快路径异常，降级到 L3。原因：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 调改写智能体。
     *
     * <p>历史为空时直接跳过：改写要解决的是「那个多少钱」里的指代，
     * 首轮问题没有上文可指，调了也只是原样返回，白花一次模型调用。
     */
    private QueryRewriteResult rewrite(AgentContext context, String query) {
        List<AgentContext.HistoryTurn> history = context.getHistory();
        if (history == null || history.isEmpty()) {
            log.debug("[PIPELINE] 无历史对话，跳过改写");
            context.addTrace(AgentResult.skip(AgentNameEnum.QUERY_REWRITE, "首轮对话，无指代可消解"));
            return QueryRewriteResult.unchanged(query, "首轮对话，未触发改写");
        }

        long start = System.currentTimeMillis();
        try {
            String userPrompt = promptLoader.load(REWRITE_USER_PROMPT)
                    .replace("{{history}}", renderHistory(history))
                    .replace("{{query}}", query);

            Msg output = rewriteAgentProvider.getObject()
                    .call(List.of(userMsg(userPrompt)))
                    .block(REWRITE_TIMEOUT);

            QueryRewriteResult result = QueryRewriteParser.parse(textOf(output), query);
            long cost = System.currentTimeMillis() - start;
            log.info("[PIPELINE] 改写完成，changed={} cost={}ms「{}」→「{}」",
                    result.rewritten(), cost, query, result.rewrittenQuery());
            context.addTrace(AgentResult.ok(AgentNameEnum.QUERY_REWRITE, result, cost));
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[PIPELINE] 改写失败，退回原文继续。原因：{}", e.getMessage());
            context.addTrace(AgentResult.fail(AgentNameEnum.QUERY_REWRITE, e.getMessage(), cost));
            return QueryRewriteResult.unchanged(query, "改写失败，使用原问题：" + e.getMessage());
        }
    }

    /**
     * 调 L3。
     *
     * <p>用 {@code callL3} 而不是智能体的 {@code call}：后者会先自己再跑一遍快路径，
     * 而这条流水线前面已经跑过了，重复跑意味着再花一次 embedding 调用。
     *
     * <p>历史作为独立消息传进去（提示词里约定 history 是消息上下文），
     * 最后一条 user 消息就是问题本身，不套任何模板 —— 提示词写明
     * 「rewritten_question 已作为当前用户消息传入」。
     */
    private IntentRecognitionResult callL3(AgentContext context, String query) {
        try {
            List<Msg> msgs = new ArrayList<>(historyMsgs(context.getHistory()));
            msgs.add(userMsg(query));

            Msg output = intentAgentProvider.getObject()
                    .callL3(msgs)
                    .block(L3_TIMEOUT);

            IntentRecognitionResult result = IntentResultParser.parse(textOf(output), IntentLevelEnum.L3);
            log.info("[PIPELINE] L3 识别完成，intent={} multi={} target={}",
                    result.getPrimaryIntent(), result.isMultiIntent(), result.getTargetAgent());
            return result;
        } catch (Exception e) {
            log.warn("[PIPELINE] L3 调用失败，返回 unknown 交给主智能体追问。原因：{}", e.getMessage());
            return IntentRecognitionResult.unknown("意图识别失败：" + e.getMessage());
        }
    }

    /** 历史渲染成提示词里的纯文本形态 */
    private String renderHistory(List<AgentContext.HistoryTurn> history) {
        List<AgentContext.HistoryTurn> recent = tail(history);
        StringBuilder sb = new StringBuilder();
        for (AgentContext.HistoryTurn turn : recent) {
            sb.append("user".equalsIgnoreCase(turn.role()) ? "用户：" : "助手：")
                    .append(truncate(turn.content()))
                    .append('\n');
        }
        return sb.toString().trim();
    }

    /** 历史转成消息列表，给 L3 当上下文 */
    private List<Msg> historyMsgs(List<AgentContext.HistoryTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<Msg> msgs = new ArrayList<>();
        for (AgentContext.HistoryTurn turn : tail(history)) {
            boolean isUser = "user".equalsIgnoreCase(turn.role());
            msgs.add(Msg.builder()
                    .role(isUser ? MsgRole.USER : MsgRole.ASSISTANT)
                    .name(isUser ? "user" : AgentNameEnum.INTENT_RECOGNITION.getCode())
                    .content(TextBlock.builder().text(truncate(turn.content())).build())
                    .build());
        }
        return msgs;
    }

    private List<AgentContext.HistoryTurn> tail(List<AgentContext.HistoryTurn> history) {
        if (history.size() <= HISTORY_MAX_TURNS) {
            return history;
        }
        return history.subList(history.size() - HISTORY_MAX_TURNS, history.size());
    }

    private static Msg userMsg(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .name("user")
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= HISTORY_MAX_CHARS ? text : text.substring(0, HISTORY_MAX_CHARS) + "...";
    }

    /** 取消息里的文本内容 */
    private static String textOf(Msg msg) {
        if (msg == null) {
            return "";
        }
        List<ContentBlock> blocks = msg.getContent();
        if (blocks == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString().trim();
    }

    /** 改写是否真的发起过模型调用（跳过时 trace 里记的是 skip） */
    private static boolean isRewriteInvoked(AgentContext context) {
        return context.getAgentTrace().stream()
                .anyMatch(t -> t.getAgentName() == AgentNameEnum.QUERY_REWRITE && !t.isSkipped());
    }
}
