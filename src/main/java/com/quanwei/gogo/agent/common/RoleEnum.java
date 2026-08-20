package com.quanwei.gogo.agent.common;

import lombok.Getter;

/**
 * 用户角色。
 * code 就是落到 user_account.role 列里的值，长度受 VARCHAR(16) 限制。
 */
@Getter
public enum RoleEnum {

    /** 普通用户，注册接口的默认角色 */
    USER("USER", "普通用户"),

    /** 管理员，只能由后台运维手动指派，不允许通过注册接口获得 */
    ADMIN("ADMIN", "管理员"),
    ;

    /** 存库的角色标识 */
    private final String code;

    /** 角色说明 */
    private final String desc;

    RoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
