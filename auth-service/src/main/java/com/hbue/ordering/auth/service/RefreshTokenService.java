package com.hbue.ordering.auth.service;

import com.hbue.ordering.auth.model.RefreshTokenInfo;

/**
 * Refresh Token 服务。
 *
 * <p>负责生成、读取、消费和删除 Redis 中保存的 Refresh Token。Token 本身为
 * 随机凭据，用户身份信息保存在服务端；刷新时必须先验证并消费旧 Token，不能
 * 仅凭客户端传入的字符串直接签发新访问令牌。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
public interface RefreshTokenService {

    /**
     * 创建并保存 Refresh Token。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param roleCode 角色编码
     * @return Refresh Token
     * @throws RuntimeException Redis 写入或设置过期时间失败时抛出
     */
    String createRefreshToken(
            Long userId,
            String username,
            String roleCode
    );

    String createRefreshToken(Long userId, String username, String roleCode, long credentialVersion);

    /**
     * 创建并保存 Refresh Token，并沿用会话链的首次签发时间。
     *
     * <p>轮换时必须传入旧令牌的首次签发时间，使新令牌的过期时间取"单次有效期"与
     * "绝对会话剩余时间"的较小值，刷新无法延长会话总寿命。</p>
     *
     * @param userId            用户 ID
     * @param username          用户名
     * @param roleCode          角色编码
     * @param credentialVersion 凭据版本
     * @param issuedAtMillis    会话首次签发时间（epoch 毫秒）；登录时传当前时间
     * @return Refresh Token
     * @throws com.hbue.ordering.common.core.exception.BusinessException 会话已超过绝对有效期时抛出
     * @throws IllegalStateException Redis 写入或设置过期时间失败时抛出
     */
    String createRefreshToken(Long userId, String username, String roleCode, long credentialVersion,
                              long issuedAtMillis);

    /**
     * 根据 Refresh Token 获取用户信息。
     *
     * @param refreshToken Refresh Token
     * @return 用户信息；Token 不存在或已过期时返回 null
     * @throws RuntimeException Redis 查询失败时抛出
     */
    RefreshTokenInfo getRefreshTokenInfo(String refreshToken);

    /**
     * 删除 Refresh Token。
     *
     * @param refreshToken Refresh Token
     */
    void deleteRefreshToken(String refreshToken);

    /**
     * 原子消费 Refresh Token，确保同一令牌只有一个请求消费成功。
     *
     * @param refreshToken Refresh Token
     * @return Redis 确认删除令牌时返回 true；令牌已被消费时返回 false
     * @throws com.hbue.ordering.common.core.exception.BusinessException Redis 无法明确确认消费结果时抛出
     */
    boolean consumeRefreshToken(String refreshToken);

    /**
     * 获取 Refresh Token 有效期。
     *
     * @return 有效期，单位为秒
     */
    long getRefreshTokenTtlSeconds();

    /**
     * 获取同一会话链允许存活的最长时长。
     *
     * @return 绝对有效期，单位为秒；从首次签发起算，轮换不延长
     */
    long getRefreshTokenMaxLifetimeSeconds();
}
