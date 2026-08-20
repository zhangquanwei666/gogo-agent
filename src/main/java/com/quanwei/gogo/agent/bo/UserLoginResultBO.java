package com.quanwei.gogo.agent.bo;

/**
 * 登录结果业务对象，承载校验通过后的用户信息。
 * 不含 token —— token 由 controller 调 StpUtil 签发，service 不掺和 web 会话。
 *
 * @param userId   业务主键，签发 token 时作为 loginId
 * @param username 登录账号
 * @param email    邮箱
 * @param realName 真实姓名
 * @param role     角色
 */
public record UserLoginResultBO(String userId,
                                String username,
                                String email,
                                String realName,
                                String role) {
}
