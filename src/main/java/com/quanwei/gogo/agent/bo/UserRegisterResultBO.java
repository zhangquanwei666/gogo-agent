package com.quanwei.gogo.agent.bo;

/**
 * 注册结果业务对象。
 * 承载落库之后的真实值，controller 直接拿它转 RespDTO，
 * 避免响应字段在 controller 里被重新算一遍而跟库里的实际值不一致。
 *
 * @param userId   业务主键
 * @param username 登录账号
 * @param realName 真实姓名
 * @param role     实际写入的角色
 */
public record UserRegisterResultBO(String userId,
                                   String username,
                                   String realName,
                                   String role) {
}
