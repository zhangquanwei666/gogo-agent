package com.quanwei.gogo.agent.bo;

import com.quanwei.gogo.agent.common.LoginTypeEnum;

/**
 * 登录业务对象。
 *
 * @param loginType   登录方式，决定 account 去匹配哪个字段
 * @param account     登录标识，按 loginType 解释成用户名或邮箱
 * @param rawPassword 明文密码，由 service 跟库里的 BCrypt 密文比对
 */
public record UserLoginBO(LoginTypeEnum loginType,
                          String account,
                          String rawPassword) {

    /** 避免密码被日志打出去 */
    @Override
    public String toString() {
        return "UserLoginBO{loginType=" + loginType
                + ", account='" + account + '\''
                + '}';
    }
}
