package com.quanwei.gogo.agent.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.quanwei.gogo.agent.bo.UserLoginBO;
import com.quanwei.gogo.agent.bo.UserLoginResultBO;
import com.quanwei.gogo.agent.bo.UserRegisterBO;
import com.quanwei.gogo.agent.bo.UserRegisterResultBO;
import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import com.quanwei.gogo.agent.common.LoginTypeEnum;
import com.quanwei.gogo.agent.common.RoleEnum;
import com.quanwei.gogo.agent.dao.UserAccountDao;
import com.quanwei.gogo.agent.entity.UserAccount;
import com.quanwei.gogo.agent.exception.BizException;
import com.quanwei.gogo.agent.service.UserAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UserAccountServiceImpl implements UserAccountService {

    @Autowired
    private UserAccountDao userAccountDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRegisterResultBO register(UserRegisterBO userRegisterBO) {
        if (userRegisterBO == null) {
            throw new BizException(ErrorCodeEnum.REGISTER_PARAM_EMPTY);
        }
        if (!StringUtils.hasText(userRegisterBO.username())) {
            throw new BizException(ErrorCodeEnum.USERNAME_EMPTY);
        }
        if (!StringUtils.hasText(userRegisterBO.rawPassword())) {
            throw new BizException(ErrorCodeEnum.PASSWORD_EMPTY);
        }

        // 先查一次给出友好提示，真正的兜底是 uk_username / uk_email 唯一索引
        if (userAccountDao.existsByUsername(userRegisterBO.username())) {
            throw new BizException(ErrorCodeEnum.USERNAME_DUPLICATED, userRegisterBO.username());
        }
        if (StringUtils.hasText(userRegisterBO.email())
                && userAccountDao.existsByEmail(userRegisterBO.email())) {
            throw new BizException(ErrorCodeEnum.EMAIL_DUPLICATED, userRegisterBO.email());
        }

        UserAccount account = new UserAccount();
        account.setUserId(UUID.randomUUID().toString().replace("-", ""));
        account.setUsername(userRegisterBO.username());
        // 空字符串要转成 null，否则唯一索引会把多个 "" 判成重复
        account.setEmail(StringUtils.hasText(userRegisterBO.email()) ? userRegisterBO.email() : null);
        account.setPassword(passwordEncoder.encode(userRegisterBO.rawPassword()));
        account.setRealName(userRegisterBO.realName());
        // 角色不接受外部传入，注册一律是普通用户
        account.setRole(RoleEnum.USER.getCode());
        // createdTime / modifyTime 由 MyMetaObjectHandler 填充

        userAccountDao.insert(account);

        // 回传落库后的实际值，不让 controller 自己去猜
        return new UserRegisterResultBO(account.getUserId(),
                account.getUsername(),
                account.getEmail(),
                account.getRealName(),
                account.getRole());
    }

    @Override
    public UserLoginResultBO login(UserLoginBO userLoginBO) {
        if (userLoginBO == null || !StringUtils.hasText(userLoginBO.account())) {
            throw new BizException(ErrorCodeEnum.ACCOUNT_EMPTY);
        }
        if (userLoginBO.loginType() == null) {
            throw new BizException(ErrorCodeEnum.LOGIN_TYPE_INVALID);
        }
        if (!StringUtils.hasText(userLoginBO.rawPassword())) {
            throw new BizException(ErrorCodeEnum.PASSWORD_EMPTY);
        }

        // 按登录方式分派到对应字段，两个字段各有唯一索引，都是精确命中
        UserAccount account = switch (userLoginBO.loginType()) {
            case USERNAME -> userAccountDao.selectByUsername(userLoginBO.account());
            case EMAIL -> userAccountDao.selectByEmail(userLoginBO.account());
        };

        // 账号不存在和密码错误抛同一个错误码，避免被拿来枚举账号
        if (account == null
                || !passwordEncoder.matches(userLoginBO.rawPassword(), account.getPassword())) {
            throw new BizException(ErrorCodeEnum.ACCOUNT_OR_PASSWORD_ERROR);
        }

        return new UserLoginResultBO(account.getUserId(),
                account.getUsername(),
                account.getEmail(),
                account.getRealName(),
                account.getRole());
    }

    @Override
    public IPage<UserAccount> pageQuery(long pageNo, long pageSize, String username, String realName) {
        return userAccountDao.selectPage(pageNo, pageSize, username, realName);
    }
}
