package com.hbue.ordering.auth.model;

/**
 * Refresh Token 在认证服务内部关联的用户身份快照。
 *
 * <p>这些字段从 Redis 中读取，用于刷新时重新签发访问令牌以及注销时核对
 * Refresh Token 所属用户。该对象只在认证服务内部使用，不作为接口响应对象
 * 直接返回给客户端，也不包含用户密码等凭据。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
public class RefreshTokenInfo {

    /**
     * 用户 ID。
     */
    private final Long userId;

    /**
     * 用户名。
     */
    private final String username;

    /**
     * 角色编码。
     */
    private final String roleCode;

    /**
     * 创建 Refresh Token 对应的用户信息。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param roleCode 角色编码
     */
    public RefreshTokenInfo(
            Long userId,
            String username,
            String roleCode
    ) {
        this.userId = userId;
        this.username = username;
        this.roleCode = roleCode;
    }

    /**
     * 获取用户 ID。
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取用户名。
     *
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取角色编码。
     *
     * @return 角色编码
     */
    public String getRoleCode() {
        return roleCode;
    }
}
