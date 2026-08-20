package com.quanwei.gogo.agent.common;

import lombok.Getter;

/**
 * 业务错误码。
 * 所有对外的提示信息统一在这里定义，业务代码里不要出现裸字符串。
 *
 * <p>code 沿用 HTTP 语义：400 参数问题、401 未认证、403 无权限、409 状态冲突、500 系统异常。
 */
@Getter
public enum ErrorCodeEnum {

    SUCCESS(200, "success"),

    /* ---------- 400 参数校验 ---------- */
    PARAM_INVALID(400, "参数不合法"),
    REGISTER_PARAM_EMPTY(400, "注册参数不能为空"),
    USERNAME_EMPTY(400, "登录账号不能为空"),
    PASSWORD_EMPTY(400, "登录密码不能为空"),

    /* ---------- 409 业务冲突 ---------- */
    USERNAME_DUPLICATED(409, "登录账号已存在"),

    /* ---------- 500 系统异常 ---------- */
    SYSTEM_ERROR(500, "服务异常，请稍后重试"),
    ;

    /** 业务状态码 */
    private final int code;

    /** 提示信息 */
    private final String msg;

    ErrorCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
