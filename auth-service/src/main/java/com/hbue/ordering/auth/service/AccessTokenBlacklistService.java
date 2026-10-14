package com.hbue.ordering.auth.service;

import com.hbue.ordering.common.redis.RedisOperationsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Access Token 黑名单服务。
 *
 * <p>只保存已注销 Access Token 的唯一 ID（jti），并让黑名单 Key 在该 JWT
 * 原定过期时一并过期，避免长期保留无用记录。该服务不存储完整 JWT，也不管理
 * Refresh Token；Redis 写入失败会向调用方传播，不能在黑名单未写入时宣称注销成功。</p>
 *
 * @author order-system
 * @date 2026-09-25
 */
@Service
public class AccessTokenBlacklistService {

    /**
     * Redis 基础操作组件。
     */
    private final RedisOperationsService redisOperationsService;

    /**
     * Access Token 黑名单 Redis Key 前缀。
     */
    private final String blacklistKeyPrefix;

    /**
     * 创建 Access Token 黑名单服务。
     *
     * @param redisOperationsService Redis 基础操作组件
     * @param blacklistKeyPrefix Access Token 黑名单 Key 前缀
     */
    public AccessTokenBlacklistService(
            RedisOperationsService redisOperationsService,
            @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}")
            String blacklistKeyPrefix
    ) {
        this.redisOperationsService = redisOperationsService;
        this.blacklistKeyPrefix = blacklistKeyPrefix;
    }

    /**
     * 将 Access Token 的唯一 ID 加入黑名单。
     *
     * @param tokenId JWT 的 jti 声明
     * @param expiresAt Access Token 的过期时间
     * @throws IllegalArgumentException jti 或过期时间缺失时抛出
     * @throws RuntimeException Redis 写入失败时传播给调用方
     */
    public void blacklist(String tokenId, Instant expiresAt) {
        // 无法确定令牌身份或过期时间时拒绝写入。
        if (tokenId == null || tokenId.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException(
                    "Access Token 的 jti 和过期时间不能为空"
            );
        }

        // 黑名单只需保留到原 Access Token 过期。
        Duration timeToLive = Duration.between(
                Instant.now(),
                expiresAt
        );
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            return;
        }

        // Redis 故障继续向上抛出，不能在未写入黑名单时谎报注销成功。
        redisOperationsService.setValue(
                blacklistKeyPrefix + tokenId,
                "1",
                timeToLive
        );
    }
}
