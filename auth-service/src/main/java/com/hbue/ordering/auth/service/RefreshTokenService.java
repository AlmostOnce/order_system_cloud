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
     * @param userId 用户 ID
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
}
