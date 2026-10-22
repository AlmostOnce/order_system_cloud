package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户服务控制器。
 *
 * <p>负责提供用户基础信息查询、注册和供认证服务调用的账号密码校验入口。
 * 控制器只完成 HTTP 参数接收与响应封装；密码比对、唯一性判断和安全字段过滤
 * 由 UserService 处理。用户实体不直接暴露给调用方。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@RestController
@Tag(name = "用户接口")
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final InternalScopeTokenVerifier verifier;

    /**
     * 创建用户控制器。
     *
     * @param userService 用户业务服务
     */
    public UserController(UserService userService, InternalScopeTokenVerifier verifier) {
        this.verifier = verifier;
        this.userService = userService;
    }

    /**
     * 用户服务轻量连通性检查接口。
     *
     * @return 用户服务运行状态
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        // 返回用户服务当前运行状态。
        return ApiResponse.success("user service is running!");
    }

    /**
     * 注册用户。
     *
     * @param command 用户注册命令
     * @return 注册成功的用户基础信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException 用户名或手机号已被使用时由统一异常处理转换为响应
     */
    @PostMapping
    public ApiResponse<UserBasicVO> register(
            @RequestBody UserRegisterCommand command,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        verifier.verify(token);
        // 由 Service 负责用户名检查、密码加密和用户保存。
        UserBasicVO userBasicVO = userService.register(command);

        // 返回注册成功的用户信息。
        return ApiResponse.success(userBasicVO);
    }


    /**
     * 校验用户账号密码。
     *
     * @param command 用户密码校验命令
     * @return 校验成功的用户基础信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException 用户不存在、不可用或密码错误时统一返回认证失败
     */
    @PostMapping("/authenticate")
    public ApiResponse<UserBasicVO> authenticate(
            @RequestBody UserPasswordVerifyCommand command,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        verifier.verify(token);
        // 由 Service 负责查询用户并校验 BCrypt 密码。
        UserBasicVO userBasicVO =
                userService.authenticate(command);

        // 返回认证成功的用户基础信息。
        return ApiResponse.success(userBasicVO);
    }
}
