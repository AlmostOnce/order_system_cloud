package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户服务控制器。
 *
 * <p>负责处理用户查询和用户注册请求。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    /**
     * 创建用户控制器。
     *
     * @param userService 用户业务服务
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户服务健康检查接口。
     *
     * @return 用户服务运行状态
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        // 返回用户服务当前运行状态。
        return ApiResponse.success("user service is running!");
    }

    /**
     * 根据用户 ID 查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserBasicVO> getUserById(
            @PathVariable("userId") Long userId
    ) {
        // 由用户业务服务负责查询用户信息。
        UserBasicVO userBasicVO = userService.getUserById(userId);

        // 返回用户基础信息。
        return ApiResponse.success(userBasicVO);
    }

    /**
     * 注册用户。
     *
     * @param command 用户注册命令
     * @return 注册成功的用户基础信息
     */
    @PostMapping
    public ApiResponse<UserBasicVO> register(
            @RequestBody UserRegisterCommand command
    ) {
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
     */
    @PostMapping("/authenticate")
    public ApiResponse<UserBasicVO> authenticate(
            @RequestBody UserPasswordVerifyCommand command
    ) {
        // 由 Service 负责查询用户并校验 BCrypt 密码。
        UserBasicVO userBasicVO =
                userService.authenticate(command);

        // 返回认证成功的用户基础信息。
        return ApiResponse.success(userBasicVO);
    }
}
