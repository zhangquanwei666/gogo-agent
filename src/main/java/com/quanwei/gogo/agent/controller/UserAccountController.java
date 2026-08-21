package com.quanwei.gogo.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.quanwei.gogo.agent.bo.UserInfoBO;
import com.quanwei.gogo.agent.dto.UserCurrentRespDTO;
import com.quanwei.gogo.agent.service.UserAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账号信息接口。
 * 登录登出在 LoginController，注册在 RegisterController，这里只放账号信息本身的查询。
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserAccountController {

    @Autowired
    private UserAccountService userAccountService;

    /**
     * 查询当前登录用户。
     * 用户身份取自 token，不接受外部传 userId —— 传什么就查什么等于把接口送给别人用。
     * 未登录时 StpUtil.getLoginIdAsString() 会抛 NotLoginException，由全局异常处理器转成 401。
     */
    @GetMapping("/current")
    public UserCurrentRespDTO current() {
        String userId = StpUtil.getLoginIdAsString();

        UserInfoBO userInfo = userAccountService.getUserInfo(userId);

        UserCurrentRespDTO response = new UserCurrentRespDTO();
        response.setUserId(userInfo.userId());
        response.setUsername(userInfo.username());
        response.setEmail(userInfo.email());
        response.setRealName(userInfo.realName());
        response.setRole(userInfo.role());
        return response;
    }
}
