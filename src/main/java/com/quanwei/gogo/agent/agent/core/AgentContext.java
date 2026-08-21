package com.quanwei.gogo.agent.agent.core;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次对话请求的上下文，贯穿整条智能体链路。
 *
 * <p>刻意设计成可变对象：每个智能体读取自己需要的字段、写回自己的产出，
 * 由编排器串起来。智能体之间不直接依赖，只通过这个对象传递数据。
 */
@Getter
@Setter
@ToString(exclude = "history")
public class AgentContext {

    /** 会话 ID */
    private String conversationId;

    /** 当前登录用户 */
    private String userId;

    /** 链路追踪 ID，贯穿所有智能体的日志 */
    private String traceId;

    /** 用户这一轮的原始输入 */
    private String rawQuery;

    /**
     * 改写后的问题。
     * 未触发改写时等于 rawQuery，下游一律读这个字段，不用判断改写有没有跑过。
     */
    private String rewrittenQuery;

    /** 历史消息，按时间正序，用于指代消解 */
    private List<HistoryTurn> history = new ArrayList<>();

    /** 各智能体的执行记录，最终写进 chat_message.extra 供排查 */
    private List<AgentResult<?>> agentTrace = new ArrayList<>();

    /** 追加一条执行记录 */
    public void addTrace(AgentResult<?> result) {
        if (result != null) {
            this.agentTrace.add(result);
        }
    }

    /** 下游统一入口：拿到最终该用于处理的问题文本 */
    public String effectiveQuery() {
        return rewrittenQuery != null && !rewrittenQuery.isBlank() ? rewrittenQuery : rawQuery;
    }

    /**
     * 历史对话中的一轮。
     *
     * @param role    user / agent
     * @param content 消息内容
     */
    public record HistoryTurn(String role, String content) {
    }
}
