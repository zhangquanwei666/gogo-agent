package com.quanwei.gogo.agent.agent.baseagent;

import com.quanwei.gogo.agent.agent.core.AgentContext;
import com.quanwei.gogo.agent.agent.core.AgentResult;
import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;

/**
 * 智能体统一接口。
 *
 * <p>所有智能体都实现它，由编排器按顺序驱动。
 * 实现类只关心「读上下文 → 干活 → 写回上下文」，不关心自己在链路的哪个位置。
 *
 * @param <T> 该智能体的产出类型
 */
public interface ChatAgent<T> {

    /** 智能体标识 */
    AgentNameEnum name();

    /**
     * 是否需要执行。
     * 返回 false 时编排器会跳过，不产生任何 LLM 调用 ——
     * 这是控制延迟和成本的主要手段。
     */
    default boolean supports(AgentContext context) {
        return true;
    }

    /**
     * 执行。
     * 实现方要自己兜住所有异常，返回 fail 结果而不是往外抛。
     */
    AgentResult<T> execute(AgentContext context);
}
