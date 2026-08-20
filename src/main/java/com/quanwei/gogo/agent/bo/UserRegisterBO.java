package com.quanwei.gogo.agent.bo;

/**
 * 注册业务对象。
 * controller 把 DTO 转成 BO 再调 service，这样 service 不依赖 web 层的数据结构。
 * 不含 role —— 角色由 service 按 RoleEnum.USER 写死，不接受外部传入。
 *
 * @param username    登录账号
 * @param rawPassword 明文密码，由 service 做 BCrypt 加密后入库
 * @param realName    真实姓名
 */
public record UserRegisterBO(String username,
                             String rawPassword,
                             String realName) {

    /** 避免密码被日志打出去 */
    @Override
    public String toString() {
        return "UserRegisterBO{username='" + username + '\''
                + ", realName='" + realName + '\''
                + '}';
    }
}
