package com.quanwei.gogo.agent.common;

import lombok.Getter;

/**
 * 登录方式。
 * 决定用 account 去匹配哪个字段，跟签发的 token 无关 —— 两种方式签发的 sa-token 完全一样。
 */
@Getter
public enum LoginTypeEnum {

    /** 用户名登录，account 匹配 user_account.username */
    USERNAME("USERNAME", "用户名登录"),

    /** 邮箱登录，account 匹配 user_account.email */
    EMAIL("EMAIL", "邮箱登录"),
    ;

    /** 前端传的类型标识 */
    private final String code;

    /** 类型说明 */
    private final String desc;

    LoginTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 查枚举，大小写不敏感。
     * 查不到返回 null，由调用方决定抛什么错 —— 本包不依赖 exception 包。
     */
    public static LoginTypeEnum of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (LoginTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return null;
    }
}
