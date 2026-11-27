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
    private final long credentialVersion;

    /**
     * 会话链首次签发时间（epoch 毫秒），用于计算绝对会话上限。
     *
     * <p>轮换时从旧令牌继承，因此刷新不会延长会话总寿命。旧版本写入的令牌没有该字段，
     * 读取时为 0，调用方应把 0 解释为"未知"并按当前时间重新起算，避免升级瞬间让全部会话失效。</p>
     */
    private final long issuedAtMillis;

    public long getCredentialVersion() {
        return credentialVersion;
    }

    /**
     * 获取会话首次签发时间。
     *
     * @return epoch 毫秒；旧版本令牌返回 0 表示未知
     */
    public long getIssuedAtMillis() {
        return issuedAtMillis;
    }

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
     * @param userId   用户 ID
     * @param username 用户名
     * @param roleCode 角色编码
     */
    public RefreshTokenInfo(
            Long userId,
            String username,
            String roleCode
    ) {
        this(userId, username, roleCode, 0);
    }

    public RefreshTokenInfo(Long userId, String username, String roleCode, long credentialVersion) {
        this(userId, username, roleCode, credentialVersion, 0);
    }

    /**
     * 创建包含会话首次签发时间的用户信息快照。
     *
     * @param userId          用户 ID
     * @param username        用户名
     * @param roleCode        角色编码
     * @param credentialVersion 凭据版本
     * @param issuedAtMillis  会话首次签发时间，未知时传 0
     */
    public RefreshTokenInfo(Long userId, String username, String roleCode, long credentialVersion,
                            long issuedAtMillis) {
        this.credentialVersion = credentialVersion;
        this.userId = userId;
        this.username = username;
        this.roleCode = roleCode;
        this.issuedAtMillis = Math.max(0L, issuedAtMillis);
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
