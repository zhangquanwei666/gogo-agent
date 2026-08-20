package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 注册入参。
 * 刻意不复用 UserAccount 实体，避免前端能伪造 id / userId / 时间字段。
 * 也刻意不收 role —— 注册一律是普通用户，管理员只能由后台指派，否则任何人都能把自己注册成管理员。
 */
@Getter
@Setter
@ToString(callSuper = true, exclude = "password")
public class UserRegisterReqDTO extends BaseRequest {

    /** 登录账号 */
    private String username;

    /** 登录密码，明文传入，由 service 做 BCrypt 加密后入库 */
    private String password;

    /** 真实姓名 */
    private String realName;
}
