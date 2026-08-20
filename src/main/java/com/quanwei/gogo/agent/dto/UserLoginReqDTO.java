package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 登录入参
 */
@Getter
@Setter
@ToString(callSuper = true, exclude = "password")
public class UserLoginReqDTO extends BaseRequest {

    /** 登录方式，必填。可选值见 LoginTypeEnum：USERNAME / EMAIL */
    private String type;

    /** 登录标识，按 type 解释成用户名或邮箱 */
    private String account;

    /** 登录密码，明文传入 */
    private String password;
}
