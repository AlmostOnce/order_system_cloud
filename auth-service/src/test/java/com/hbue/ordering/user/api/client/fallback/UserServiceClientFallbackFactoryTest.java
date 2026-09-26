package com.hbue.ordering.user.api.client.fallback;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.UserServiceClient;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用户服务 Feign 降级工厂测试。
 *
 * @author order-system
 * @date 2026-09-25
 */
class UserServiceClientFallbackFactoryTest {

    /**
     * 验证用户服务拒绝凭据时保留未授权结果，而不是转换成系统异常。
     */
    @Test
    void shouldPreserveUnauthorizedWhenUserServiceRejectsCredentials() {
        UserServiceClientFallbackFactory fallbackFactory =
                new UserServiceClientFallbackFactory();
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://user-service/users/authenticate",
                Map.of(),
                Request.Body.empty(),
                new RequestTemplate()
        );
        FeignException cause = new FeignException.Unauthorized(
                "用户服务拒绝登录凭据",
                request,
                new byte[0],
                Map.of()
        );

        UserServiceClient fallback = fallbackFactory.create(cause);
        ApiResponse<UserBasicVO> response = fallback.authenticate(
                new UserPasswordVerifyCommand()
        );

        assertEquals(
                CommonErrorCode.UNAUTHORIZED.code(),
                response.getCode()
        );
        assertEquals("用户名或密码错误", response.getMessage());
    }

    /**
     * 验证用户服务调用发生故障时返回服务暂不可用结果。
     */
    @Test
    void shouldReturnServiceUnavailableWhenUserServiceCallFails() {
        UserServiceClientFallbackFactory fallbackFactory =
                new UserServiceClientFallbackFactory();

        UserServiceClient fallback = fallbackFactory.create(
                new IllegalStateException("connection refused")
        );
        ApiResponse<UserBasicVO> response = fallback.authenticate(
                new UserPasswordVerifyCommand()
        );

        assertEquals(
                CommonErrorCode.SERVICE_UNAVAILABLE.code(),
                response.getCode()
        );
    }
}
