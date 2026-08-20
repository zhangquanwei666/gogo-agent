package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 查询当前登录用户的返回值
 */
@Getter
@Setter
@ToString(callSuper = true)
public class UserCurrentRespDTO extends BaseResponse {

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
}
