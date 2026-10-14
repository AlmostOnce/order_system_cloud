package com.hbue.ordering.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 登录及刷新接口的响应 VO。
 *
 * <p>返回访问 Token、Refresh Token、Bearer 类型及各自有效期。客户端应将
 * Token 作为凭据妥善保管；响应不包含密码、密码哈希或数据库实体。</p>
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
