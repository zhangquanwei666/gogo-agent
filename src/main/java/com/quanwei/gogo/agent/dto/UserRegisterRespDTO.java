package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 注册返回值
 */
@Getter
@Setter
@ToString(callSuper = true)
public class UserRegisterRespDTO extends BaseResponse {

    /** 业务主键，对外一律用这个标识用户，自增 id 不外泄 */
    private String userId;

    /** 登录账号 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 真实姓名 */
    private String realName;

    /** 角色 */
    private String role;
}
