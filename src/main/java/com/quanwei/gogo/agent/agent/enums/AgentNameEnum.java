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
    QUERY_REWRITE("QueryRewritingAgent", "多轮对话用户问题改写与指代消除"),

    /** 意图识别：L0/L1/L2/L3 多级分类器，判定用户诉求属于哪个业务意图 */
    INTENT_RECOGNITION("IntentRecognitionAgent", "多级意图识别与多意图拆分"),

    /**
     * 主智能体：流水线终点，负责寒暄、信息不足时的澄清追问和最终答复。
     * code 必须和 {@link IntentCategory#GREETING} / {@link IntentCategory#UNKNOWN}
     * 里绑的 targetAgent（bean 名 masterAgent）区分开 —— 那个是容器标识，这个是落库的展示名。
     */
    MASTER("MasterAgent", "主智能体，负责寒暄、澄清追问与最终答复"),
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
