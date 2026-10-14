package com.hbue.ordering.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hbue.ordering.common.core.scope.DataScopeType;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.redis.AuthzCacheKeys;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.mapper.UserDataScopeMapper;
import com.hbue.ordering.user.model.UserDataScopeDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户数据范围业务实现。
 *
 * <p>负责读写 user-service 自有的 {@code user_data_scopes}，将持久化记录转换为
 * 权限判断所需的范围快照，并在范围发生替换、撤销或引导授予后尝试删除 Redis
 * 缓存。缓存失效失败仅记录日志，后续由缓存 TTL 兜底；数据库写入仍以本地事务为准。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Service
public class UserDataScopeServiceImpl
        extends ServiceImpl<UserDataScopeMapper, UserDataScopeDO>
        implements UserDataScopeService {

    /** 日志对象。 */
    private static final Logger log =
            LoggerFactory.getLogger(UserDataScopeServiceImpl.class);

    /**
     * 全平台范围的引用值，按约定固定为 0。
     */
    private static final long PLATFORM_SCOPE_REF = 0L;

    /** Redis 基础操作服务，用于失效数据范围缓存。 */
    private final RedisOperationsService redisOperationsService;

    /**
     * 创建用户数据范围业务服务。
     *
     * @param redisOperationsService Redis 基础操作服务
     */
    public UserDataScopeServiceImpl(
            RedisOperationsService redisOperationsService
    ) {
        this.redisOperationsService = redisOperationsService;
    }

    /**
     * 查询指定用户持有的全部数据范围。
     *
     * @param userId 用户 ID
     * @return 数据范围列表
     */
    @Override
    public List<UserDataScopeDO> listByUserId(Long userId) {
        // 用户 ID 为空时不返回任何范围，避免全表查询。
        if (userId == null) {
            return List.of();
        }
        return baseMapper.selectByUserId(userId);
    }

    /**
     * 查询指定用户的数据范围快照。
     *
     * @param userId 用户 ID
     * @return 范围快照
     * @implNote 范围集合去重并保留读取顺序；未知范围类型不加入快照
     */
    @Override
    public UserScopeSnapshot loadSnapshot(Long userId) {
        if (userId == null) {
            return UserScopeSnapshot.empty(null);
        }

        boolean platform = false;
        Set<Long> windowIds = new LinkedHashSet<>();
        Set<Long> restaurantIds = new LinkedHashSet<>();
        for (UserDataScopeDO scope : listByUserId(userId)) {
            DataScopeType scopeType = DataScopeType.findByCode(scope.getScopeType());
            if (scopeType == null) {
                continue;
            }
            switch (scopeType) {
                case WINDOW -> windowIds.add(scope.getScopeRef());
                case RESTAURANT -> restaurantIds.add(scope.getScopeRef());
                case PLATFORM -> platform = true;
            }
        }
        return new UserScopeSnapshot(
                userId,
                platform,
                windowIds,
                restaurantIds
        );
    }

    /**
     * 覆盖式替换指定用户的全部数据范围。
     *
     * @param userId 用户 ID
     * @param scopes 新的范围列表
     * @implNote 该操作在一个本地事务中先删除旧记录再写入新记录，随后失效范围缓存
     */
    @Override
    @Transactional
    public void replaceScopes(Long userId, List<UserDataScopeDO> scopes) {
        if (userId == null) {
            return;
        }

        // 覆盖式语义：先清空旧范围，再写入新范围，保证与请求完全一致。
        remove(new QueryWrapper<UserDataScopeDO>().eq("user_id", userId));
        if (scopes != null && !scopes.isEmpty()) {
            saveBatch(scopes);
        }

        // 主动失效缓存，使撤销/授予即时生效。
        evictScopeCache(userId);
    }

    /**
     * 撤销指定用户的单条数据范围。
     *
     * @param userId 用户 ID
     * @param scopeId 范围记录 ID
     * @implNote 删除条件同时包含用户 ID 和范围记录 ID，避免跨用户撤销
     */
    @Override
    @Transactional
    public void revokeScope(Long userId, Long scopeId) {
        if (userId == null || scopeId == null) {
            return;
        }

        // 限定 user_id，避免跨用户撤销他人范围。
        remove(new QueryWrapper<UserDataScopeDO>()
                .eq("user_id", userId)
                .eq("scope_id", scopeId));

        // 主动失效缓存，使撤销即时生效。
        evictScopeCache(userId);
    }

    /**
     * 授予全平台范围，重复授予时保持幂等。
     *
     * @param userId 用户 ID
     * @implNote 重复授予由预查询和数据库唯一键共同兜底，并发重复插入按已存在处理
     */
    @Override
    public void grantPlatformScope(Long userId) {
        if (userId == null) {
            return;
        }

        // 已存在全平台范围时直接返回，避免重复记录。
        Long existingCount = baseMapper.selectCount(
                new QueryWrapper<UserDataScopeDO>()
                        .eq("user_id", userId)
                        .eq("scope_type", DataScopeType.PLATFORM.getCode())
        );
        if (existingCount != null && existingCount > 0) {
            evictScopeCache(userId);
            return;
        }

        UserDataScopeDO scope = new UserDataScopeDO();
        scope.setUserId(userId);
        scope.setScopeType(DataScopeType.PLATFORM.getCode());
        scope.setScopeRef(PLATFORM_SCOPE_REF);
        LocalDateTime now = LocalDateTime.now();
        scope.setCreatedAt(now);
        scope.setUpdatedAt(now);

        try {
            save(scope);
        } catch (DuplicateKeyException exception) {
            // 并发引导时由唯一索引兜底，视为范围已存在。
        }

        // 引导授予后同样失效缓存，避免读到旧快照。
        evictScopeCache(userId);
    }

    /**
     * 删除指定用户的数据范围缓存键。
     *
     * @param userId 用户 ID
     */
    private void evictScopeCache(Long userId) {
        try {
            redisOperationsService.delete(AuthzCacheKeys.userScope(userId));
        } catch (RuntimeException exception) {
            // 缓存删除失败不能影响范围授予结果，最迟由 TTL 到期失效。
            log.warn("删除用户数据范围缓存失败：userId={}", userId);
        }
    }
}
