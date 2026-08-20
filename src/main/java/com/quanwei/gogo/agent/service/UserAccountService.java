package com.quanwei.gogo.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.quanwei.gogo.agent.bo.UserLoginBO;
import com.quanwei.gogo.agent.bo.UserLoginResultBO;
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
     * 登录校验。account 先按用户名查，查不到再按邮箱查。
     * 只做凭证校验，不签发 token —— token 由 controller 调 StpUtil 处理。
     *
     * @return 校验通过的用户信息
     * @throws com.quanwei.gogo.agent.exception.BizException 账号不存在或密码错误时抛出，
     *         两种情况用同一个错误码，避免被拿来枚举账号
     */
    UserLoginResultBO login(UserLoginBO userLoginBO);

    /** 分页查询 */
    IPage<UserAccount> pageQuery(long pageNo, long pageSize, String username, String realName);
}
