package com.quanwei.gogo.agent.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.quanwei.gogo.agent.bo.UserRegisterBO;
import com.quanwei.gogo.agent.bo.UserRegisterResultBO;
import com.quanwei.gogo.agent.common.ErrorCodeEnum;
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

        // 先查一次给出友好提示，真正的兜底是 uk_username 唯一索引
        if (userAccountDao.existsByUsername(userRegisterBO.username())) {
            throw new BizException(ErrorCodeEnum.USERNAME_DUPLICATED, userRegisterBO.username());
        }

        UserAccount account = new UserAccount();
        account.setUserId(UUID.randomUUID().toString().replace("-", ""));
        account.setUsername(userRegisterBO.username());
        account.setPassword(passwordEncoder.encode(userRegisterBO.rawPassword()));
        account.setRealName(userRegisterBO.realName());
        // 角色不接受外部传入，注册一律是普通用户
        account.setRole(RoleEnum.USER.getCode());
        // createdTime / modifyTime 由 MyMetaObjectHandler 填充

        userAccountDao.insert(account);

        // 回传落库后的实际值，不让 controller 自己去猜
        return new UserRegisterResultBO(account.getUserId(),
                account.getUsername(),
                account.getRealName(),
                account.getRole());
    }

    @Override
    public UserAccount verifyPassword(String username, String rawPassword) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            return null;
        }
        UserAccount account = userAccountDao.selectByUsername(username);
        if (account == null) {
            return null;
        }
        if (!passwordEncoder.matches(rawPassword, account.getPassword())) {
            return null;
        }
        return account;
    }

    @Override
    public IPage<UserAccount> pageQuery(long pageNo, long pageSize, String username, String realName) {
        return userAccountDao.selectPage(pageNo, pageSize, username, realName);
    }
}
