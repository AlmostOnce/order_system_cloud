package com.hbue.ordering.order.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.redis.AuthzCacheKeys;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.product.api.vo.WindowInfoVO;
import com.hbue.ordering.user.api.client.UserScopeInternalClient;
import com.hbue.ordering.user.api.vo.UserScopeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 订单服务数据范围查询与判定实现。
 *
 * <p>范围事实来自 user-service 的内部查询接口，结果缓存在 Redis
 * （键 {@code authz:user-scope:{userId}}，TTL 300 秒），
 * 撤销范围时由 user-service 主动删除缓存键。</p>
 *
 * <p>订单属私有数据，越权与不存在对外表现必须一致。因此本实现只提供
 * 判定结果（{@link #canAccessWindow(Long, Long)}），由 Service 决定返回 404；
 * {@link #checkWindowAccess(Long, Long)} 仅用于公开资源场景。</p>
 *
 * <p>餐厅范围需要把档口换算为所属餐厅，档口归属数据由 product-service 提供。
 * Redis 仅作为短期加速层，缓存缺失或读取失败时回源 user-service；范围事实无法确认时抛出服务不可用，
 * 不默认放行任何商家数据操作。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Service
public class DefaultDataScopeService implements DataScopeService {

    /** 日志对象。 */
    private static final Logger log =
            LoggerFactory.getLogger(DefaultDataScopeService.class);

    /** 用户数据范围内部查询客户端。 */
    private final UserScopeInternalClient userScopeInternalClient;

    /** 档口与餐厅内部查询客户端。 */
    private final WindowInternalClient windowInternalClient;

    /** Redis 基础操作服务。 */
    private final RedisOperationsService redisOperationsService;

    /** JSON 序列化器，用于缓存范围快照。 */
    private final ObjectMapper objectMapper;

    /** 服务间共享密钥。 */
    private final String internalScopeToken;

    /**
     * 创建订单服务数据范围判定实现。
     *
     * @param userScopeInternalClient 用户数据范围内部查询客户端
     * @param windowInternalClient 档口与餐厅内部查询客户端
     * @param redisOperationsService Redis 基础操作服务
     * @param objectMapper JSON 序列化器
     * @param internalScopeToken 服务间共享密钥
     */
    public DefaultDataScopeService(
            UserScopeInternalClient userScopeInternalClient,
            WindowInternalClient windowInternalClient,
            RedisOperationsService redisOperationsService,
            ObjectMapper objectMapper,
            @Value("${internal.scope.token:}") String internalScopeToken
    ) {
        this.userScopeInternalClient = userScopeInternalClient;
        this.windowInternalClient = windowInternalClient;
        this.redisOperationsService = redisOperationsService;
        this.objectMapper = objectMapper;
        this.internalScopeToken = internalScopeToken;
    }

    /**
     * 查询用户的数据范围快照，优先使用 Redis 缓存。
     *
     * @param userId 用户 ID
     * @return 范围快照
     * @throws BusinessException user-service 调用失败或返回无效结果时抛出，调用方应拒绝受保护操作
     */
    @Override
    public UserScopeSnapshot loadScopes(Long userId) {
        if (userId == null) {
            return UserScopeSnapshot.empty(null);
        }

        // 缓存只保存 user-service 返回的授权事实，不在订单服务内自行推导用户角色范围。
        String cacheKey = AuthzCacheKeys.userScope(userId);
        UserScopeVO cachedScope = readCache(cacheKey);
        if (cachedScope != null) {
            return toSnapshot(cachedScope, userId);
        }

        // 缓存未命中或不可读时回源权威用户服务；回源失败不能退化为无范围或全范围。
        ApiResponse<UserScopeVO> response =
                userScopeInternalClient.getScopes(userId, internalScopeToken);
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            String message = response == null ? null : response.getMessage();
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    message == null || message.isBlank()
                            ? "用户服务暂时不可用，无法确认数据范围"
                            : message
            );
        }

        UserScopeVO scopeVO = response.getData();
        writeCache(cacheKey, scopeVO);
        return toSnapshot(scopeVO, userId);
    }

    /**
     * 判断用户是否有权操作指定档口。
     *
     * <p>主管档口、全平台范围直接命中；否则把档口换算为所属餐厅，
     * 再判断是否被餐厅范围覆盖。</p>
     *
     * @param userId 当前登录用户 ID
     * @param windowId 目标档口 ID
     * @return 有权时返回 true
     * @throws BusinessException 范围事实或档口归属依赖不可用时抛出，避免依赖故障导致错误授权
     */
    @Override
    public boolean canAccessWindow(Long userId, Long windowId) {
        if (windowId == null) {
            return false;
        }
        UserScopeSnapshot scopes = loadScopes(userId);
        // 档口直接授权优先命中；餐厅授权必须先取得档口所属餐厅再判定。
        if (scopes.coversWindow(windowId)) {
            return true;
        }
        if (scopes.getRestaurantIds().isEmpty()) {
            return false;
        }

        WindowInfoVO window = loadWindow(windowId);
        return window != null
                && scopes.coversRestaurant(window.getRestaurantId());
    }

    /**
     * 判断用户是否有权操作指定餐厅。
     *
     * @param userId 当前登录用户 ID
     * @param restaurantId 目标餐厅 ID
     * @return 有权时返回 true
     * @throws BusinessException user-service 范围查询失败时抛出
     */
    @Override
    public boolean canAccessRestaurant(Long userId, Long restaurantId) {
        if (restaurantId == null) {
            return false;
        }
        return loadScopes(userId).coversRestaurant(restaurantId);
    }

    /**
     * 校验用户是否有权操作指定档口，失败时抛出 403。
     *
     * @param userId 当前登录用户 ID
     * @param windowId 目标档口 ID
     * @throws BusinessException 无权访问时抛出 403 业务异常
     */
    @Override
    public void checkWindowAccess(Long userId, Long windowId) {
        if (!canAccessWindow(userId, windowId)) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "没有操作该档口数据的权限"
            );
        }
    }

    /**
     * 校验用户是否有权操作指定餐厅，失败时抛出 403。
     *
     * @param userId 当前登录用户 ID
     * @param restaurantId 目标餐厅 ID
     * @throws BusinessException 无权访问时抛出 403 业务异常
     */
    @Override
    public void checkRestaurantAccess(Long userId, Long restaurantId) {
        if (!canAccessRestaurant(userId, restaurantId)) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "没有操作该餐厅数据的权限"
            );
        }
    }

    /**
     * 查询档口信息。
     *
     * @param windowId 档口 ID
     * @return 档口信息；档口不存在时返回 null
     */
    private WindowInfoVO loadWindow(Long windowId) {
        ApiResponse<WindowInfoVO> response =
                windowInternalClient.getWindow(windowId, internalScopeToken);
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            String message = response == null ? null : response.getMessage();
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    message == null || message.isBlank()
                            ? "商品服务暂时不可用，无法确认档口归属"
                            : message
            );
        }
        return response.getData();
    }

    /**
     * 从 Redis 读取范围缓存。
     *
     * @param cacheKey 缓存键
     * @return 缓存的范围；未命中或缓存不可用时返回 null
     */
    private UserScopeVO readCache(String cacheKey) {
        try {
            String cachedJson = redisOperationsService.getValue(cacheKey);
            if (cachedJson == null || cachedJson.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cachedJson, UserScopeVO.class);
        } catch (RuntimeException | IOException exception) {
            // 缓存故障不应阻断鉴权，回退到实时查询。
            log.warn("读取用户数据范围缓存失败，回退实时查询：key={}", cacheKey);
            return null;
        }
    }

    /**
     * 把范围写入 Redis 缓存。
     *
     * @param cacheKey 缓存键
     * @param scopeVO 范围事实
     */
    private void writeCache(String cacheKey, UserScopeVO scopeVO) {
        try {
            redisOperationsService.setValue(
                    cacheKey,
                    objectMapper.writeValueAsString(scopeVO),
                    AuthzCacheKeys.USER_SCOPE_TTL
            );
        } catch (RuntimeException | IOException exception) {
            // 缓存写入失败不影响本次鉴权结果。
            log.warn("写入用户数据范围缓存失败：key={}", cacheKey);
        }
    }

    /**
     * 把内部契约对象转换为范围快照。
     *
     * @param scopeVO 内部契约范围对象
     * @param userId 用户 ID
     * @return 范围快照
     */
    private UserScopeSnapshot toSnapshot(UserScopeVO scopeVO, Long userId) {
        return new UserScopeSnapshot(
                userId,
                scopeVO.isPlatform(),
                toIdSet(scopeVO.getWindowIds()),
                toIdSet(scopeVO.getRestaurantIds())
        );
    }

    /**
     * 把范围 ID 列表转换为集合。
     *
     * @param ids 范围 ID 列表
     * @return 范围 ID 集合
     */
    private Set<Long> toIdSet(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(ids);
    }
}
