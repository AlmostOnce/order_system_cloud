package com.hbue.ordering.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 登录响应对象。
 *
 * <p>只返回访问 Token 和 Token 有效期，
 * 不返回用户密码等敏感信息。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Getter
@Builder
public class LoginVO {

    /**
     * 访问 Token。
     */
    private String accessToken;

    /**
     * Refresh Token。
     */
    private String refreshToken;

    /**
     * Token 类型。
     */
    private String tokenType;

    /**
     * Token 有效期，单位为秒。
     */
    private Long expiresIn;

    /**
     * Refresh Token 有效期，单位为秒。
     */
    private Long refreshExpiresIn;
}
