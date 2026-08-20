package com.quanwei.gogo.agent.common;

import lombok.Getter;

/**
 * 对话消息的角色，对应 chat_message.role。
 * 注意跟 RoleEnum 区分开：那个是用户权限角色，这个是消息发出方。
 */
@Getter
public enum MessageRoleEnum {

    /** 用户发的消息 */
    USER("user", "用户"),

    /** Agent 回复的消息，此时 agent_name 应该有值 */
    AGENT("agent", "智能体"),

    /** 系统消息，比如提示词、状态通知 */
    SYSTEM("system", "系统"),
    ;

    /** 存库的值 */
    private final String code;

    /** 说明 */
    private final String desc;

    MessageRoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 按 code 查枚举，大小写不敏感，查不到返回 null */
    public static MessageRoleEnum of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MessageRoleEnum role : values()) {
            if (role.code.equalsIgnoreCase(code.trim())) {
                return role;
            }
        }
        return null;
    }
}
