package com.hbue.ordering.user.api.client.fallback;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.UserServiceClient;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户服务远程调用降级工厂。
 *
 * @author order-system
 * @date 2026-09-20
 */
@Component
public class UserServiceClientFallbackFactory
        implements FallbackFactory<UserServiceClient> {

    /**
     * HTTP 未授权状态码。
     */
    private static final int HTTP_STATUS_UNAUTHORIZED = 401;

    /**
     * 日志对象。
     */
    private static final Logger log =
            LoggerFactory.getLogger(UserServiceClientFallbackFactory.class);

    /**
     * 创建用户服务降级客户端。
     *
     * @param cause 远程调用失败原因
     * @return 用户服务降级客户端
     */
    @Override
    public UserServiceClient create(Throwable cause) {
        // 账号密码错误是预期的认证结果，不按系统故障记录异常堆栈。
        boolean authenticationRejected = isUnauthorized(cause);
        if (authenticationRejected) {
            log.info("user-service 拒绝了本次登录凭据");
        } else {
            // 远程调用或服务端故障需要记录异常，日志中不包含请求密码。
            log.error("调用 user-service 失败", cause);
        }

        // 返回用户服务降级实现。
        return new UserServiceClient() {

            /**
             * 用户查询降级处理。
             *
             * @param userId 用户 ID
             * @return 服务不可用响应
             */
            @Override
            public ApiResponse<UserBasicVO> getUserById(
                    Long userId
            ) {
                // 返回统一的服务不可用响应。
                return serviceUnavailable();
            }

            /**
             * 用户注册降级处理。
             *
             * @param command 用户注册命令
             * @return 服务不可用响应
             */
            @Override
            public ApiResponse<UserBasicVO> register(
                    UserRegisterCommand command
            ) {
                // 返回统一的服务不可用响应。
                return serviceUnavailable();
            }

            /**
             * 用户密码校验降级处理。
             *
             * @param command 用户密码校验命令
             * @return 服务不可用响应
             */
            @Override
            public ApiResponse<UserBasicVO> authenticate(
                    UserPasswordVerifyCommand command
            ) {
                // 将下游认证拒绝保留为 401，其它故障视为服务不可用。
                if (authenticationRejected) {
                    return ApiResponse.failure(
                            CommonErrorCode.UNAUTHORIZED,
                            "用户名或密码错误"
                    );
                }

                return serviceUnavailable();
            }
        };
    }

    /**
     * 判断 Feign 异常链路中是否包含 HTTP 401 响应。
     *
     * @param cause Feign 降级原因
     * @return 存在 HTTP 401 响应时返回 true
     */
    private boolean isUnauthorized(Throwable cause) {
        // 遍历异常原因链，兼容 Feign 异常被上层包装的情况。
        Throwable currentCause = cause;
        while (currentCause != null) {
            if (currentCause instanceof FeignException feignException
                    && feignException.status() == HTTP_STATUS_UNAUTHORIZED) {
                return true;
            }
            currentCause = currentCause.getCause();
        }
        return false;
    }

    /**
     * 创建用户服务不可用响应。
     *
     * @return 服务不可用响应
     */
    private ApiResponse<UserBasicVO> serviceUnavailable() {
        // 不向前端暴露远程服务内部异常。
        return ApiResponse.failure(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "用户服务暂时不可用"
        );
    }
}
