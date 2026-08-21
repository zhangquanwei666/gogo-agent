package com.quanwei.gogo.agent.agent.baseagent;

import com.quanwei.gogo.agent.agent.core.AgentContext;
import com.quanwei.gogo.agent.agent.core.AgentResult;
import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;
import com.quanwei.gogo.agent.agent.llm.LlmClient;
import com.quanwei.gogo.agent.agent.prompt.PromptLoader;
import com.quanwei.gogo.agent.agent.rewrite.QueryRewriteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 问题改写智能体。
 *
 * <p>职责单一：把带指代、省略的追问补全成一句可以脱离上下文独立理解的问题。
 * 「那家酒店多少钱」→「上海浦东丽思卡尔顿酒店多少钱」。
 *
 * <p>下游的意图识别和业务智能体都读 {@link AgentContext#effectiveQuery()}，
 * 不需要关心改写有没有真的发生。
 */
@Slf4j
@Component
public class QueryRewriteAgent implements ChatAgent<QueryRewriteResult> {

    /** 拼进 prompt 的历史轮数上限。太多会稀释注意力，也白烧 token */
    private static final int MAX_HISTORY_TURNS = 6;

    /** 单轮历史的截断长度，避免某条超长回复把 prompt 撑爆 */
    private static final int MAX_TURN_CHARS = 200;

    /** 模型判定无需改写时约定返回的标记 */
    private static final String NO_REWRITE_FLAG = "NO_REWRITE";

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    /** 提示词文件名，对应 resources/prompt/ 下的 .txt */
    private static final String SYSTEM_PROMPT_NAME = "query-rewrite-system";
    private static final String USER_PROMPT_NAME = "query-rewrite-user";

    /**
     * 指代/省略的特征词。
     * 命中任意一个才认为可能需要改写 —— 这是省掉大部分 LLM 调用的关键。
     */
    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "这个|那个|这家|那家|这些|那些|它|他|她|上面|前面|刚才|刚刚|之前|" +
                    "第一个|第二个|最后一个|再来|还有|换一个|改成|也要|同样|一样|" +
                    "多少钱|怎么样|可以吗|行吗|呢[？?]?$");

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private PromptLoader promptLoader;

    @Override
    public AgentNameEnum name() {
        return AgentNameEnum.QUERY_REWRITE;
    }

    /**
     * 两个条件都满足才值得调 LLM：有历史可参考，且问题里有指代特征。
     * 首轮对话没有上下文可消解，问题本身完整的也不需要改 —— 这两种情况直接跳过，
     * 能省掉相当比例请求的一次 LLM 往返。
     */
    @Override
    public boolean supports(AgentContext context) {
        if (context == null || context.getRawQuery() == null || context.getRawQuery().isBlank()) {
            return false;
        }
        if (context.getHistory() == null || context.getHistory().isEmpty()) {
            return false;
        }
        return REFERENCE_PATTERN.matcher(context.getRawQuery()).find();
    }

    @Override
    public AgentResult<QueryRewriteResult> execute(AgentContext context) {
        String rawQuery = context.getRawQuery();

        // 编排器理论上会先问 supports，这里再挡一道，避免被直接调用时炸掉
        if (!supports(context)) {
            QueryRewriteResult result = QueryRewriteResult.unchanged(rawQuery, "无需改写：首轮对话或问题已完整");
            context.setRewrittenQuery(rawQuery);
            return AgentResult.skip(name(), result.reason());
        }

        long start = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(SYSTEM_PROMPT_NAME);
            String userPrompt = promptLoader.render(USER_PROMPT_NAME, Map.of(
                    "history", buildHistoryText(context),
                    "query", rawQuery));
            String output = llmClient.complete(systemPrompt, userPrompt, 0.0D, TIMEOUT);
            long cost = System.currentTimeMillis() - start;

            QueryRewriteResult result = parseOutput(rawQuery, output);
            context.setRewrittenQuery(result.rewrittenQuery());

            log.info("[{}] traceId={} 改写{} 耗时={}ms, 原文={}, 改写后={}",
                    name().getCode(), context.getTraceId(),
                    result.rewritten() ? "生效" : "跳过", cost, rawQuery, result.rewrittenQuery());

            return AgentResult.ok(name(), result, cost);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            // 改写是锦上添花，失败了用原文继续，绝不能让整轮对话中断
            context.setRewrittenQuery(rawQuery);
            log.warn("[{}] traceId={} 改写失败，降级使用原文，耗时={}ms, 原因={}",
                    name().getCode(), context.getTraceId(), cost, e.getMessage());
            return AgentResult.fail(name(), e.getMessage(), cost);
        }
    }

    /**
     * 只拼历史对话这一段，填进 user 模板的 {{history}} 占位符。
     * 模板骨架在 resources/prompt/query-rewrite-user.txt 里，Java 不再拼措辞
     */
    private String buildHistoryText(AgentContext context) {
        List<AgentContext.HistoryTurn> history = context.getHistory();
        int from = Math.max(0, history.size() - MAX_HISTORY_TURNS);

        StringBuilder sb = new StringBuilder();
        for (AgentContext.HistoryTurn turn : history.subList(from, history.size())) {
            String speaker = "user".equalsIgnoreCase(turn.role()) ? "用户" : "助手";
            sb.append(speaker).append("：").append(truncate(turn.content())).append(System.lineSeparator());
        }
        return sb.toString().trim();
    }

    /** 解析模型输出，做好防御 —— 模型不一定听话 */
    private QueryRewriteResult parseOutput(String rawQuery, String output) {
        if (output == null || output.isBlank()) {
            return QueryRewriteResult.unchanged(rawQuery, "模型返回空内容");
        }

        String cleaned = output.trim();
        if (cleaned.contains(NO_REWRITE_FLAG)) {
            return QueryRewriteResult.unchanged(rawQuery, "模型判定无需改写");
        }

        // 模型有时会自作主张加引号
        cleaned = cleaned.replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "").trim();
        if (cleaned.isBlank()) {
            return QueryRewriteResult.unchanged(rawQuery, "模型返回空内容");
        }

        // 改写结果显著长于原文时大概率是模型在扩写或回答问题，不采信
        if (cleaned.length() > rawQuery.length() * 5 + 50) {
            return QueryRewriteResult.unchanged(rawQuery, "改写结果异常过长，疑似模型扩写");
        }
        if (cleaned.equals(rawQuery)) {
            return QueryRewriteResult.unchanged(rawQuery, "改写结果与原文一致");
        }
        return QueryRewriteResult.rewritten(rawQuery, cleaned);
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_TURN_CHARS ? text : text.substring(0, MAX_TURN_CHARS) + "...";
    }
}
