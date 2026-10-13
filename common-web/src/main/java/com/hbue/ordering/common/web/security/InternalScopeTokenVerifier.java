package com.hbue.ordering.common.web.security;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务间内部调用共享密钥校验器。
 *
 * <p>用于校验请求头 {@code X-Internal-Token}，保护仅供后端服务互调的内部接口
 * （数据范围查询、档口与餐厅查询等）。这些路径不在 Gateway 路由内，
 * 外部无法直接访问，但仍需校验凭据，避免内网被横向调用。</p>
 *
 * <p>共享密钥通过 {@code internal.scope.token} 配置，各服务取值必须一致；
 * 未配置时一律拒绝，避免因漏配而放开内部接口。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Component
public class InternalScopeTokenVerifier {

    /** 服务间共享密钥。 */
    private final String expectedToken;

    /**
     * 创建内部调用共享密钥校验器。
     *
     * @param expectedToken 配置的服务间共享密钥
     */
    public InternalScopeTokenVerifier(
            @Value("${internal.scope.token:}") String expectedToken
    ) {
        this.expectedToken = expectedToken;
    }

    /**
     * 校验内部调用凭据，失败时抛出 401。
     *
     * @param token 请求头中携带的共享密钥
     */
    public void verify(String token) {
        // 未配置共享密钥时拒绝全部内部调用，避免漏配导致内部接口暴露。
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "内部调用凭据未配置"
            );
        }

        // 使用常量时间比较，避免通过响应时间推断密钥。
        byte[] expectedBytes = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = token == null
                ? new byte[0]
                : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "内部调用凭据无效"
            );
        }
    }
}
