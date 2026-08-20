package com.quanwei.gogo.agent.common;

import lombok.Getter;

/**
 * 用户对消息的反馈，对应 chat_message.feedback。
 * 列可空，NULL 表示未反馈。
 */
@Getter
public enum FeedbackEnum {

    /** 点赞 */
    LIKE("LIKE", "点赞"),

    /** 点踩 */
    DISLIKE("DISLIKE", "点踩"),
    ;

    /** 存库的值 */
    private final String code;

    /** 说明 */
    private final String desc;

    FeedbackEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 按 code 查枚举，大小写不敏感，查不到返回 null */
    public static FeedbackEnum of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (FeedbackEnum feedback : values()) {
            if (feedback.code.equalsIgnoreCase(code.trim())) {
                return feedback;
            }
        }
        return null;
    }
}
