package com.quanwei.gogo.agent.controller;

import com.quanwei.gogo.agent.bo.UserRegisterBO;
import com.quanwei.gogo.agent.bo.UserRegisterResultBO;
import com.quanwei.gogo.agent.dto.UserRegisterReqDTO;
import com.quanwei.gogo.agent.dto.UserRegisterRespDTO;
import com.quanwei.gogo.agent.service.UserAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册账户接口。
 * 跟 LoginController 共用 /api/v1/auth 前缀，拆分只是按职责分文件。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class RegisterController {

    @Autowired
    private UserAccountService userAccountService;

    /** 用户注册，角色固定为普通用户，不接受外部指定 */
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
}
