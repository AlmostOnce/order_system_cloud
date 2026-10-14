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
 * <p>该接口是认证服务等调用方访问 user-service 的 Feign 契约，固定使用服务名
 * {@code user-service} 完成注册发现，不暴露数据库实体。请求和响应模型属于本模块，
 * 用户服务不可用时由 {@link UserServiceClientFallbackFactory} 生成降级响应；
 * 登录凭据错误的 401 与下游故障需要保持不同语义。</p>
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
     * @return 统一响应中的用户基础信息；用户不存在时由服务端返回业务失败
     */
    @GetMapping("/users/{userId}")
    ApiResponse<UserBasicVO> getUserById(
            @PathVariable("userId") Long userId
    );

    /**
     * 注册用户。
     *
     * @param command 用户注册命令
     * @return 统一响应中的新用户基础信息；调用方不接触用户服务持久化对象
     */
    @PostMapping("/users")
    ApiResponse<UserBasicVO> register(
            @RequestBody UserRegisterCommand command
    );

    /**
     * 校验用户账号密码。
     *
     * @param command 用户密码校验命令
     * @return 校验成功时为用户基础信息；认证拒绝仍以未授权响应表达，远程故障由降级实现处理
     */
    @PostMapping("/users/authenticate")
    ApiResponse<UserBasicVO> authenticate(
            @RequestBody UserPasswordVerifyCommand command
    );
}
