package com.quanwei.gogo.agent.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quanwei.gogo.agent.entity.UserAccount;
import com.quanwei.gogo.agent.mapper.UserAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * user_account 数据访问层。
 * 约定：所有对 user_account 的数据库操作都收口在这个类，service 不直接碰 mapper。
 * 这里只做数据存取，不掺业务判断（比如"用户名重复要报什么错"属于 service 的事）。
 */
@Repository
public class UserAccountDao {

    @Autowired
    private UserAccountMapper userAccountMapper;

    /** 新增账号 */
    public int insert(UserAccount userAccount) {
        return userAccountMapper.insert(userAccount);
    }

    /** 按业务主键查询 */
    public UserAccount selectByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return userAccountMapper.selectById(userId);
    }

    /** 按登录账号查询，登录校验用 */
    public UserAccount selectByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username));
    }

    /** 按邮箱查询，邮箱登录用 */
    public UserAccount selectByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getEmail, email));
    }

    /** 登录账号是否已存在 */
    public boolean existsByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return userAccountMapper.exists(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username));
    }

    /** 邮箱是否已被注册 */
    public boolean existsByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return userAccountMapper.exists(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getEmail, email));
    }

    /** 分页查询，username / realName 支持模糊匹配 */
    public IPage<UserAccount> selectPage(long pageNo, long pageSize, String username, String realName) {
        return userAccountMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<UserAccount>lambdaQuery()
                        .like(StringUtils.hasText(username), UserAccount::getUsername, username)
                        .like(StringUtils.hasText(realName), UserAccount::getRealName, realName)
                        .orderByDesc(UserAccount::getCreatedTime));
    }
}
