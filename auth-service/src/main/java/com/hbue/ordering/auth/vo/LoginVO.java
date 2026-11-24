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
     * 为true时仅允许补验，不包含Access/Refresh Token。
     */
    private boolean emailVerificationRequired;
    private String emailVerificationToken;
    private Long emailVerificationExpiresIn;
    private String roleCode;

    /**
     * 为 true 时表示账号处于注销冷静期，响应不包含 Access/Refresh Token。
     *
     * <p>客户端应进入待注销页面，展示生效时间和撤回入口；不能再调用普通业务接口。</p>
     */
    private boolean deletionPending;

    /**
     * 注销生效时间（ISO-8601 本地时间），仅 deletionPending 为 true 时有值。
     */
    private String deletionEffectiveAt;

    /**
     * 一次性撤回令牌，仅 deletionPending 为 true 时有值；只保存在内存，不落盘。
     */
    private String deletionCancelToken;

    /**
     * 撤回令牌有效秒数。
     */
    private Long deletionCancelExpiresIn;

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
