package com.quanwei.gogo.agent.agent.enums;

import lombok.Getter;

/**
 * 智能体名称。
 * 值会落进 chat_message.agent_name，也用于日志和链路追踪，
 * 统一在这里定义避免各处写裸字符串。
 */
@Getter
public enum AgentNameEnum {

    /** 问题改写：把带指代的追问补全成可独立理解的问题 */
    QUERY_REWRITE("QueryRewritingAgent", "问题改写"),
    ;

    /** 落库和日志用的标识 */
    private final String code;

    /** 中文说明 */
    private final String desc;

    AgentNameEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
