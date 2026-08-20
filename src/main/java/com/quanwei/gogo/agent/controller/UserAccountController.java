package com.quanwei.gogo.agent.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.quanwei.gogo.agent.bo.UserLoginBO;
import com.quanwei.gogo.agent.bo.UserInfoBO;
import com.quanwei.gogo.agent.bo.UserLoginResultBO;
import com.quanwei.gogo.agent.bo.UserRegisterBO;
import com.quanwei.gogo.agent.bo.UserRegisterResultBO;
import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import com.quanwei.gogo.agent.common.LoginTypeEnum;
import com.quanwei.gogo.agent.exception.BizException;
import com.quanwei.gogo.agent.common.BaseResponse;
import com.quanwei.gogo.agent.dto.UserCurrentRespDTO;
import com.quanwei.gogo.agent.dto.UserLoginReqDTO;
import com.quanwei.gogo.agent.dto.UserLoginRespDTO;
import com.quanwei.gogo.agent.dto.UserRegisterReqDTO;
import com.quanwei.gogo.agent.dto.UserRegisterRespDTO;
import com.quanwei.gogo.agent.service.UserAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账号接口
 */
@RestController
@RequestMapping("/user")
public class UserAccountController {

    @Autowired
    private UserAccountService userAccountService;

    /** 用户注册 */
    @PostMapping("/register")
    public UserRegisterRespDTO register(@RequestBody UserRegisterReqDTO request) {
        // DTO 是传输层结构，转成 BO 再进 service
        UserRegisterBO userRegisterBO = new UserRegisterBO(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getRealName());

        UserRegisterResultBO result = userAccountService.register(userRegisterBO);

        // 全部取自落库后的真实值，不再拿入参回填
        UserRegisterRespDTO response = new UserRegisterRespDTO();
        response.setUserId(result.userId());
        response.setUsername(result.username());
        response.setEmail(result.email());
        response.setRealName(result.realName());
        response.setRole(result.role());
        // code / msg 走 BaseResponse 的默认值 200 / success
        return response;
    }

    /**
     * 用户登录，account 传用户名或邮箱都可以。
     * service 只负责校验凭证，token 在这里签发 —— StpUtil 依赖 web 会话上下文，属于 web 层的事。
     */
    @PostMapping("/login")
    public UserLoginRespDTO login(@RequestBody UserLoginReqDTO request) {
        // 传输层是字符串，在这里收敛成枚举，非法值不放进业务层
        LoginTypeEnum loginType = LoginTypeEnum.of(request.getType());
        if (loginType == null) {
            throw new BizException(ErrorCodeEnum.LOGIN_TYPE_INVALID);
        }

        UserLoginBO userLoginBO = new UserLoginBO(
                loginType,
                request.getAccount(),
                request.getPassword());

        UserLoginResultBO result = userAccountService.login(userLoginBO);

        // 校验通过才签发 token，loginId 用业务主键 userId
        StpUtil.login(result.userId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        UserLoginRespDTO response = new UserLoginRespDTO();
        response.setType(loginType.getCode());
        response.setUserId(result.userId());
        response.setUsername(result.username());
        response.setEmail(result.email());
        response.setRealName(result.realName());
        response.setRole(result.role());
        response.setTokenName(tokenInfo.getTokenName());
        response.setTokenValue(tokenInfo.getTokenValue());
        response.setTokenTimeout(tokenInfo.getTokenTimeout());
        return response;
    }

    /**
     * 退出登录，清掉当前 token 在 Redis 里的所有痕迹。
     * 未登录时调用不报错，sa-token 内部做了空判断，方便前端无脑调。
     */
    @PostMapping("/logout")
    public BaseResponse logout() {
        StpUtil.logout();
        return new BaseResponse();
    }

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
