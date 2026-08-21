package com.quanwei.gogo.agent.agent.core;

import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;
import lombok.Getter;
import lombok.ToString;

/**
 * 单个智能体的执行结果。
 *
 * <p>约定：智能体不向外抛异常，失败也要返回一个 success=false 的结果，
 * 由编排器决定是降级继续还是中断。单个智能体挂掉不能让整轮对话失败。
 *
 * @param <T> 具体产出的类型
 */
@Getter
@ToString
public class AgentResult<T> {

    /** 哪个智能体产出的 */
    private final AgentNameEnum agentName;

    /** 是否成功 */
    private final boolean success;

    /** 是否跳过执行（比如首轮对话无需改写），跳过也算成功 */
    private final boolean skipped;

    /** 产出数据，失败或跳过时为 null */
    private final T data;

    /** 失败原因，成功时为 null */
    private final String errorMsg;

    /** 耗时，毫秒 */
    private final long costMs;

    private AgentResult(AgentNameEnum agentName, boolean success, boolean skipped,
                        T data, String errorMsg, long costMs) {
        this.agentName = agentName;
        this.success = success;
        this.skipped = skipped;
        this.data = data;
        this.errorMsg = errorMsg;
        this.costMs = costMs;
    }

    public static <T> AgentResult<T> ok(AgentNameEnum agentName, T data, long costMs) {
        return new AgentResult<>(agentName, true, false, data, null, costMs);
    }

    /** 跳过执行，比如条件不满足。算成功，但没有产出 */
    public static <T> AgentResult<T> skip(AgentNameEnum agentName, String reason) {
        return new AgentResult<>(agentName, true, true, null, reason, 0L);
    }

    public static <T> AgentResult<T> fail(AgentNameEnum agentName, String errorMsg, long costMs) {
        return new AgentResult<>(agentName, false, false, null, errorMsg, costMs);
    }
}
