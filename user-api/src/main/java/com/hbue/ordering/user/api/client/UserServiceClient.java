package com.hbue.ordering.user.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.fallback.UserServiceClientFallbackFactory;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户服务远程调用客户端。
 *
 * <p>该接口是用户服务对外提供的远程调用契约。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@FeignClient(
        name = "user-service",
        fallbackFactory = UserServiceClientFallbackFactory.class
)
public interface UserServiceClient {

    /**
     * 根据用户 ID 查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
     */
    @GetMapping("/users/{userId}")
    ApiResponse<UserBasicVO> getUserById(
            @PathVariable("userId") Long userId
    );

    /**
     * 注册用户。
     *
     * @param command 用户注册命令
     * @return 注册成功的用户基础信息
     */
    @PostMapping("/users")
    ApiResponse<UserBasicVO> register(
            @RequestBody UserRegisterCommand command
    );

    /**
     * 校验用户账号密码。
     *
     * @param command 用户密码校验命令
     * @return 校验成功的用户基础信息
     */
    @PostMapping("/users/authenticate")
    ApiResponse<UserBasicVO> authenticate(
            @RequestBody UserPasswordVerifyCommand command
    );
}
