package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.model.RefreshTokenInfo;
import com.hbue.ordering.auth.service.RefreshTokenService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Refresh Token Redis 服务实现。
 *
 * <p>Refresh Token 本身不携带用户信息，
 * 用户信息保存在 Redis Hash 中，Token 只作为 Redis Key 的一部分。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /**
     * 当前类的日志对象。
     */
    private static final Logger log =
            LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    /**
     * Redis Key 前缀。
     */
    private static final String KEY_PREFIX = "auth:refresh-token:";

    /**
     * 用户 ID 字段名。
     */
    private static final String FIELD_USER_ID = "userId";

    /**
     * 用户名字段名。
     */
    private static final String FIELD_USERNAME = "username";

    /**
     * 角色编码字段名。
     */
    private static final String FIELD_ROLE_CODE = "roleCode";

    /**
     * Redis 常用基础操作组件。
     */
    private final RedisOperationsService redisOperationsService;

    /**
     * Refresh Token 有效期，单位为秒。
     */
    private final long refreshTokenTtlSeconds;

    /**
     * 创建 Refresh Token Redis 服务。
     *
     * @param redisOperationsService Redis 常用基础操作组件
     * @param refreshTokenTtlSeconds Refresh Token 有效期
     */
    public RefreshTokenServiceImpl(
            RedisOperationsService redisOperationsService,
            @Value("${security.jwt.refresh-token-ttl-seconds}")
            long refreshTokenTtlSeconds
    ) {
        this.redisOperationsService = redisOperationsService;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    /**
     * 创建并保存 Refresh Token。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param roleCode 角色编码
     * @return Refresh Token
     */
    @Override
    public String createRefreshToken(
            Long userId,
            String username,
            String roleCode
    ) {
        // 使用随机 UUID 生成不携带业务信息的 Refresh Token。
        String refreshToken = UUID.randomUUID()
                .toString()
                .replace("-", "");

        // 使用 Token 生成 Redis Key，不在日志中输出完整 Token。
        String redisKey = buildRedisKey(refreshToken);

        // 组装刷新 Token 对应的用户信息。
        Map<String, String> values = new HashMap<>();
        values.put(FIELD_USER_ID, String.valueOf(userId));
        values.put(FIELD_USERNAME, username);
        values.put(FIELD_ROLE_CODE, roleCode);

        // 通过公共组件将用户信息写入 Redis Hash。
        redisOperationsService.putHashValues(redisKey, values);

        // 为 Redis Key 设置自动过期时间。
        Boolean expireSuccess = redisOperationsService.expire(
                redisKey,
                Duration.ofSeconds(refreshTokenTtlSeconds)
        );
        if (!Boolean.TRUE.equals(expireSuccess)) {
            // 过期时间设置失败时删除数据，避免产生永久 Token。
            redisOperationsService.delete(redisKey);
            throw new IllegalStateException(
                    "Refresh Token 过期时间设置失败"
            );
        }

        // 返回给客户端的只是不携带用户信息的随机 Token。
        return refreshToken;
    }

    /**
     * 根据 Refresh Token 获取用户信息。
     *
     * @param refreshToken Refresh Token
     * @return 用户信息；Token 不存在或已过期时返回 null
     */
    @Override
    public RefreshTokenInfo getRefreshTokenInfo(String refreshToken) {
        // 空 Token 不执行 Redis 查询。
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }

        // 查询 Redis Hash 中保存的用户信息。
        Map<Object, Object> values =
                redisOperationsService.getHashValues(
                        buildRedisKey(refreshToken)
                );
        if (values == null || values.isEmpty()) {
            return null;
        }

        // 读取用户 ID，数据缺失时认为 Token 无效。
        Object userIdValue = values.get(FIELD_USER_ID);
        if (userIdValue == null) {
            return null;
        }

        // 将 Redis 中的用户信息组装为内部对象。
        return new RefreshTokenInfo(
                Long.valueOf(String.valueOf(userIdValue)),
                String.valueOf(values.get(FIELD_USERNAME)),
                String.valueOf(values.get(FIELD_ROLE_CODE))
        );
    }

    /**
     * 删除 Refresh Token。
     *
     * @param refreshToken Refresh Token
     */
    @Override
    public void deleteRefreshToken(String refreshToken) {
        // 空 Token 不执行删除操作。
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        // 删除 Redis 中的 Refresh Token。
        redisOperationsService.delete(buildRedisKey(refreshToken));
    }

    /**
     * 原子消费 Refresh Token，确保同一令牌只有一个请求消费成功。
     *
     * @param refreshToken Refresh Token
     * @return Redis 确认删除令牌时返回 true；令牌已被消费时返回 false
     */
    @Override
    public boolean consumeRefreshToken(String refreshToken) {
        // 空 Token 不能被消费，也不执行 Redis 操作。
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }

        try {
            // 单 Key DEL 是 Redis 原子操作，同时作为并发轮换的最终竞争门闩。
            Boolean deleted = redisOperationsService.delete(
                    buildRedisKey(refreshToken)
            );

            // Redis 未返回明确结果时不能当成消费成功或令牌不存在。
            if (deleted == null) {
                throw new BusinessException(
                        CommonErrorCode.SERVICE_UNAVAILABLE,
                        "Refresh Token 服务暂时不可用"
                );
            }

            // 只有实际删除成功的请求才获得该 Refresh Token 的消费权。
            return deleted;
        } catch (BusinessException exception) {
            // 保留已明确分类的服务不可用异常。
            throw exception;
        } catch (RuntimeException exception) {
            // Redis 故障时不能继续签发可用的新令牌。
            log.error("原子消费 Refresh Token 失败", exception);
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Refresh Token 服务暂时不可用"
            );
        }
    }

    /**
     * 获取 Refresh Token 有效期。
     *
     * @return 有效期，单位为秒
     */
    @Override
    public long getRefreshTokenTtlSeconds() {
        // 返回配置中的 Refresh Token 有效期。
        return refreshTokenTtlSeconds;
    }

    /**
     * 生成 Refresh Token 对应的 Redis Key。
     *
     * @param refreshToken Refresh Token
     * @return Redis Key
     */
    private String buildRedisKey(String refreshToken) {
        // 统一使用固定前缀，便于后续按业务清理和排查数据。
        return KEY_PREFIX + refreshToken;
    }
}
