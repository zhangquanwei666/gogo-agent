package com.quanwei.gogo.agent.bo;

/**
 * 用户基本信息业务对象。
 * 刻意不含 password —— 这个对象会一路传到 controller，密文也不该离开 service。
 *
 * @param userId   业务主键
 * @param username 登录账号
 * @param email    邮箱
 * @param realName 真实姓名
 * @param role     角色
 */
public record UserInfoBO(String userId,
                         String username,
                         String email,
                         String realName,
                         String role) {
}
