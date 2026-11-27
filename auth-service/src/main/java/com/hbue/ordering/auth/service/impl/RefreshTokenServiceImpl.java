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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Refresh Token Redis 服务实现。
 *
 * <p>Refresh Token 本身不携带用户信息，用户 ID、用户名和角色编码保存在 Redis
 * Hash 中，Token 只作为随机凭据及 Redis Key 的一部分。每个 Key 都设置 TTL；
 * 刷新流程通过删除旧 Key 原子竞争消费权，避免旧 Token 被重复使用。</p>
 *
 * <p>会话寿命受两层约束：单枚令牌的有效期，以及从首次签发起算的绝对会话期限。
 * 轮换时继承旧令牌的首次签发时间，因此持续刷新不能无限延长登录状态。同一用户的
 * 令牌同时登记在有序集合索引中，超过并发会话上限时淘汰最久未活动的一枚，
 * 既限制多端会话数量，也为后续按用户清理全部会话提供入口。</p>
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
     * 按用户登记会话的有序集合 Key 前缀：auth:refresh-sessions:{userId}。
     */
    private static final String SESSION_INDEX_PREFIX = "auth:refresh-sessions:";

    /**
     * 会话首次签发时间字段名，用于计算绝对会话期限。
     */
    private static final String FIELD_ISSUED_AT = "issued_at";

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
     * 会话绝对有效期，单位为秒；从首次签发起算，轮换不延长。
     */
    private final long refreshTokenAbsoluteSeconds;

    /**
     * 同一用户允许同时存在的 Refresh Token 数量上限。
     */
    private final int refreshTokenMaxSessions;

    /**
     * 创建 Refresh Token Redis 服务。
     *
     * @param redisOperationsService     Redis 常用基础操作组件
     * @param refreshTokenTtlSeconds     单枚 Refresh Token 有效期
     * @param refreshTokenAbsoluteSeconds 会话绝对有效期，必须大于零
     * @param refreshTokenMaxSessions     同一用户并发会话上限，至少为 1
     */
    public RefreshTokenServiceImpl(
            RedisOperationsService redisOperationsService,
            @Value("${security.jwt.refresh-token-ttl-seconds}")
            long refreshTokenTtlSeconds,
            @Value("${security.jwt.refresh-token-absolute-seconds}")
            long refreshTokenAbsoluteSeconds,
            @Value("${security.jwt.refresh-token-max-sessions:5}")
            int refreshTokenMaxSessions
    ) {
        // 配置非法时启动即失败，避免上线后出现永不失效或无法淘汰的会话。
        if (refreshTokenTtlSeconds < 1 || refreshTokenAbsoluteSeconds < 1) {
            throw new IllegalArgumentException("Refresh Token 有效期与绝对会话期限必须大于零");
        }
        if (refreshTokenMaxSessions < 1) {
            throw new IllegalArgumentException("Refresh Token 并发会话上限至少为 1");
        }
        this.redisOperationsService = redisOperationsService;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.refreshTokenAbsoluteSeconds = refreshTokenAbsoluteSeconds;
        this.refreshTokenMaxSessions = refreshTokenMaxSessions;
    }

    /**
     * 创建并保存 Refresh Token。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param roleCode 角色编码
     * @return Refresh Token
     * @throws IllegalStateException Redis Key 的过期时间无法设置时抛出，并清理刚写入的数据
     */
    @Override
    public String createRefreshToken(
            Long userId,
            String username,
            String roleCode
    ) {
        return createRefreshToken(userId, username, roleCode, 0);
    }

    @Override
    public String createRefreshToken(Long userId, String username, String roleCode, long credentialVersion) {
        // 登录场景从当前时间开始一个新会话链。
        return createRefreshToken(userId, username, roleCode, credentialVersion, System.currentTimeMillis());
    }

    @Override
    public String createRefreshToken(Long userId, String username, String roleCode, long credentialVersion,
                                     long issuedAtMillis) {
        if (credentialVersion < 0) throw new IllegalArgumentException("凭据版本无效");
        long now = System.currentTimeMillis();
        // 旧版本令牌没有首次签发时间，按当前时间起算，避免升级瞬间让存量会话立即失效。
        long sessionStart = issuedAtMillis > 0 ? issuedAtMillis : now;
        // 绝对到期时间只由首次签发起算，轮换不会延长；溢出直接抛错而不是产生负数期限。
        long absoluteDeadline = Math.addExact(sessionStart,
                Math.multiplyExact(refreshTokenAbsoluteSeconds, 1000L));
        long absoluteRemainingMillis = absoluteDeadline - now;
        if (absoluteRemainingMillis < 1000L) {
            // 会话整体到期必须重新登录，不能再签发任何新令牌。
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录状态已超过最长有效期，请重新登录");
        }
        // 单枚令牌 TTL 不能超过会话剩余绝对时间，否则每次刷新都会把会话整体续命。
        long ttlSeconds = Math.min(refreshTokenTtlSeconds, absoluteRemainingMillis / 1000L);

        // 使用随机 UUID 生成不携带业务信息的 Refresh Token。
        String refreshToken = UUID.randomUUID()
                .toString()
                .replace("-", "");

        // 使用 Token 生成 Redis Key，不在日志中输出完整 Token。
        String redisKey = buildRedisKey(refreshToken);

        // 组装刷新 Token 对应的用户信息，并记录会话首次签发时间供后续轮换继承。
        Map<String, String> values = new HashMap<>();
        values.put(FIELD_USER_ID, String.valueOf(userId));
        values.put("credential_version", String.valueOf(credentialVersion));
        values.put(FIELD_USERNAME, username);
        values.put(FIELD_ROLE_CODE, roleCode);
        values.put(FIELD_ISSUED_AT, String.valueOf(sessionStart));

        // 通过公共组件将用户信息写入 Redis Hash。
        redisOperationsService.putHashValues(redisKey, values);

        // 为 Redis Key 设置自动过期时间。
        Boolean expireSuccess = redisOperationsService.expire(
                redisKey,
                Duration.ofSeconds(ttlSeconds)
        );
        if (!Boolean.TRUE.equals(expireSuccess)) {
            // 过期时间设置失败时删除数据，避免产生永久 Token。
            redisOperationsService.delete(redisKey);
            throw new IllegalStateException(
                    "Refresh Token 过期时间设置失败"
            );
        }

        try {
            // 登记会话索引并按上限淘汰最早会话；失败时整体拒绝签发，保持 fail-closed。
            registerSessionIndex(userId, refreshToken, now);
        } catch (RuntimeException exception) {
            // 无法统计的令牌不能返回给客户端，否则会绕过并发会话上限且无法按用户清理。
            redisOperationsService.delete(redisKey);
            throw exception;
        }

        // 返回给客户端的只是不携带用户信息的随机 Token。
        return refreshToken;
    }

    /**
     * 登记会话索引，并在同一用户超过并发会话上限时淘汰最久未活动的一枚。
     *
     * <p>索引用有序集合保存，分数为本次签发时间，成员为 Refresh Token。每次签发都更新分数，
     * 因此淘汰顺序等价于"最久未活动"。淘汰时同时删除对应令牌 Key，让被淘汰会话立即失效。</p>
     *
     * <p>该实现不保证跨实例原子性：并发登录的极端情况下可能短暂超过上限一个，下一次签发即会收敛。
     * 这是有意的软上限取舍——真正的有效性仍由令牌 Key 与绝对会话期限保证，且避免为一次登录引入
     * Lua 脚本或分布式锁。</p>
     *
     * @param userId       令牌所属用户
     * @param refreshToken 本次签发的令牌
     * @param now          本次签发时间（epoch 毫秒）
     */
    private void registerSessionIndex(Long userId, String refreshToken, long now) {
        String indexKey = buildSessionIndexKey(userId);
        Boolean added = redisOperationsService.addToSortedSet(indexKey, refreshToken, now);
        if (added == null) {
            throw new IllegalStateException("Refresh Token 会话索引写入失败");
        }
        // 索引 TTL 与绝对会话期限一致；设置失败只记录日志，索引仍会随成员淘汰逐步收缩。
        Boolean expireSuccess = redisOperationsService.expire(
                indexKey,
                Duration.ofSeconds(refreshTokenAbsoluteSeconds)
        );
        if (!Boolean.TRUE.equals(expireSuccess)) {
            log.warn("Refresh Token 会话索引过期时间设置失败，userId={}", userId);
        }

        Long size = redisOperationsService.sortedSetSize(indexKey);
        if (size == null) {
            throw new IllegalStateException("Refresh Token 会话数量读取失败");
        }
        long extra = size - refreshTokenMaxSessions;
        if (extra <= 0) {
            return;
        }
        // 只淘汰比当前令牌更早的成员；刚签发的令牌一定排在最末，跳过它是防御同毫秒登录的并列分数。
        List<String> earliest = redisOperationsService.sortedSetRange(indexKey, 0, extra - 1);
        for (String member : earliest) {
            if (refreshToken.equals(member)) {
                continue;
            }
            redisOperationsService.delete(buildRedisKey(member));
            redisOperationsService.removeFromSortedSet(indexKey, member);
            log.info("Refresh Token 会话数超过上限，已淘汰最早会话，userId={}", userId);
        }
    }

    /**
     * 根据 Refresh Token 获取用户信息。
     *
     * @param refreshToken Refresh Token
     * @return 用户信息；Token 不存在或已过期时返回 null
     * @throws RuntimeException Redis 查询失败或存储的用户 ID 格式错误时抛出
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

        // 将 Redis 中的用户信息组装为内部对象；首次签发时间缺失或损坏时返回 0 表示未知。
        return new RefreshTokenInfo(
                Long.valueOf(String.valueOf(userIdValue)),
                String.valueOf(values.get(FIELD_USERNAME)),
                String.valueOf(values.get(FIELD_ROLE_CODE)),
                values.get("credential_version") == null ? 0 : Long.parseLong(String.valueOf(values.get("credential_version"))),
                parseIssuedAt(values.get(FIELD_ISSUED_AT))
        );
    }

    /**
     * 解析会话首次签发时间，缺失或格式损坏时按未知处理。
     *
     * @param value Redis 中的字段值
     * @return 非负的 epoch 毫秒；无法解析时返回 0
     */
    private long parseIssuedAt(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException exception) {
            // 字段被人工改写或旧版本异常写入时不能让刷新流程 500，按未知签发时间重新起算。
            return 0L;
        }
    }

    /**
     * 删除 Refresh Token，并同步清理该用户会话索引中的对应成员。
     *
     * @param refreshToken Refresh Token
     */
    @Override
    public void deleteRefreshToken(String refreshToken) {
        // 空 Token 不执行删除操作。
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        // 必须先按令牌读取所属用户：删除令牌 Key 后就再也定位不到会话索引。
        removeSessionIndexEntry(refreshToken);

        // 删除 Redis 中的 Refresh Token。
        redisOperationsService.delete(buildRedisKey(refreshToken));
    }

    /**
     * 从所属用户的会话索引中移除令牌。
     *
     * <p>索引是辅助数据，清理失败只记录日志：残留成员会在下次超过上限时被淘汰，
     * 且索引自身带有绝对会话期限的 TTL，不会永久占用。</p>
     *
     * @param refreshToken 待清理的令牌
     */
    private void removeSessionIndexEntry(String refreshToken) {
        try {
            Map<Object, Object> values = redisOperationsService.getHashValues(buildRedisKey(refreshToken));
            if (values == null || values.isEmpty()) {
                return;
            }
            Object userIdValue = values.get(FIELD_USER_ID);
            if (userIdValue == null) {
                return;
            }
            Long userId = Long.valueOf(String.valueOf(userIdValue));
            redisOperationsService.removeFromSortedSet(buildSessionIndexKey(userId), refreshToken);
        } catch (RuntimeException exception) {
            // 索引清理失败不能掩盖令牌本身的删除结果，也不能让注销接口因辅助数据失败而报错。
            log.warn("清理 Refresh Token 会话索引失败", exception);
        }
    }

    /**
     * 原子消费 Refresh Token，确保同一令牌只有一个请求消费成功。
     *
     * @param refreshToken Refresh Token
     * @return Redis 确认删除令牌时返回 true；令牌已被消费时返回 false
     * @throws BusinessException Redis 无法明确确认删除结果时抛出，避免继续签发 Token
     */
    @Override
    public boolean consumeRefreshToken(String refreshToken) {
        // 空 Token 不能被消费，也不执行 Redis 操作。
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }

        try {
            // 在 DEL 之前清理索引：DEL 之后 Hash 已删除，无法再定位令牌所属用户。
            removeSessionIndexEntry(refreshToken);

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
     * 获取会话绝对有效期。
     *
     * @return 从首次签发起算允许存活的最长秒数
     */
    @Override
    public long getRefreshTokenMaxLifetimeSeconds() {
        return refreshTokenAbsoluteSeconds;
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

    /**
     * 生成用户会话索引的有序集合 Key。
     *
     * @param userId 用户 ID
     * @return 会话索引 Key
     */
    private String buildSessionIndexKey(Long userId) {
        return SESSION_INDEX_PREFIX + userId;
    }
}
