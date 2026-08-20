package com.quanwei.gogo.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.quanwei.gogo.agent.bo.UserRegisterBO;
import com.quanwei.gogo.agent.bo.UserRegisterResultBO;
import com.quanwei.gogo.agent.entity.UserAccount;

/**
 * 用户账号业务处理。只做业务编排和校验，数据存取全部委托给 UserAccountDao。
 */
public interface UserAccountService {

    /**
     * 注册账号，密码入库前做 BCrypt 加密，角色固定为 RoleEnum.USER
     *
     * @return 落库之后的真实值
     */
    UserRegisterResultBO register(UserRegisterBO userRegisterBO);

    /**
     * 校验账号密码
     *
     * @return 校验通过返回账号信息，失败返回 null（不区分"账号不存在"和"密码错"，避免账号枚举）
     */
    UserAccount verifyPassword(String username, String rawPassword);

    /** 分页查询 */
    IPage<UserAccount> pageQuery(long pageNo, long pageSize, String username, String realName);
}
