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
    ACCOUNT_EMPTY(400, "账号不能为空，支持用户名或邮箱"),
    LOGIN_TYPE_INVALID(400, "登录类型不合法，可选值：USERNAME / EMAIL"),

    /* ---------- 401 认证失败 ---------- */
    ACCOUNT_OR_PASSWORD_ERROR(401, "账号或密码错误"),
    NOT_LOGIN(401, "当前会话未登录，请先登录"),

    MESSAGE_ROLE_INVALID(400, "消息角色不合法，可选值：user / agent / system"),
    FEEDBACK_INVALID(400, "反馈类型不合法，可选值：LIKE / DISLIKE"),
    MESSAGE_CONTENT_EMPTY(400, "消息内容不能为空"),
    USER_ID_EMPTY(400, "用户ID不能为空"),
    CONVERSATION_ID_EMPTY(400, "会话ID不能为空"),
    MESSAGE_ID_EMPTY(400, "消息ID不能为空"),

    METHOD_NOT_ALLOWED(405, "请求方法不被支持"),

    /* ---------- 404 资源不存在 ---------- */
    API_NOT_FOUND(404, "接口不存在"),
    USER_NOT_FOUND(404, "用户不存在"),
    CONVERSATION_NOT_FOUND(404, "会话不存在或无权访问"),
    MESSAGE_NOT_FOUND(404, "消息不存在"),

    /* ---------- 409 业务冲突 ---------- */
    USERNAME_DUPLICATED(409, "登录账号已存在"),
    EMAIL_DUPLICATED(409, "邮箱已被注册"),

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
