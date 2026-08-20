package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 登录返回值。
 * tokenName / tokenValue 来自 sa-token，前端后续请求要把 tokenValue 放进名为 tokenName 的请求头。
 */
@Getter
@Setter
@ToString(callSuper = true, exclude = "tokenValue")
public class UserLoginRespDTO extends BaseResponse {

    /** 本次使用的登录方式 */
    private String type;

    /** 业务主键 */
    private String userId;

    /** 登录账号 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 真实姓名 */
    private String realName;

    /** 角色 */
    private String role;

    /** token 的请求头名称，取自 sa-token 配置的 token-name */
    private String tokenName;

    /** token 值 */
    private String tokenValue;

    /** token 剩余有效期，单位秒，-1 表示永不过期 */
    private Long tokenTimeout;
}
