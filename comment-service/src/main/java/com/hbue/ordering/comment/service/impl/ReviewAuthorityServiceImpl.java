package com.hbue.ordering.comment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.client.CommentScopeClient;
import com.hbue.ordering.comment.client.CommentWindowClient;
import com.hbue.ordering.comment.client.vo.CommentUserScopeVO;
import com.hbue.ordering.comment.client.vo.CommentWindowInfoVO;
import com.hbue.ordering.comment.service.ReviewAuthorityService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.role.RoleCode;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.redis.AuthzCacheKeys;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.response.ApiResponse;
import feign.FeignException;
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
 * 评价修改与删除的鉴权实现。
 *
 * <p>判定顺序固定为：作者本人 → 系统管理员/总经理 → 餐厅经理与档口负责人的数据范围。
 * 顺序不能调换：先判角色会让作者在角色缺失时被误拒，先判范围会在作者没有管理范围时被误拒。</p>
 *
 * <p>系统管理员与总经理不需要范围判定：前者是需求指定的例外，后者按角色定位即全平台。
 * 只有餐厅经理与档口负责人需要继续把"档口编号"换算为"所属餐厅"来判定覆盖关系。</p>
 *
 * <p>数据范围以用户服务为唯一事实来源，结果缓存到 Redis（键 {@code authz:user-scope:{userId}}，
 * 与订单、商品服务共用同一份缓存，撤销范围时由用户服务主动删除）。缓存读取失败回源查询，
 * 缓存写入失败不影响本次判定。</p>
 *
 * <p>餐厅经理的范围是餐厅编号，评价只保存档口编号，因此需要再向商品服务查询档口归属。
 * 只有确实存在餐厅范围时才发起这次查询，避免给直接授权档口的管理员增加无谓调用。</p>
 *
 * <p>失败语义：无权限返回 403（评价是公开资源，不需要用 404 隐藏存在性）；
 * 范围或档口归属无法确认时返回 503，绝不因为依赖不可用而降级放行。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Service
public class ReviewAuthorityServiceImpl implements ReviewAuthorityService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(ReviewAuthorityServiceImpl.class);

    /**
     * 用户数据范围内部客户端。
     */
    private final CommentScopeClient scopeClient;

    /**
     * 档口内部查询客户端。
     */
    private final CommentWindowClient windowClient;

    /**
     * Redis 基础操作服务，用于缓存范围快照。
     */
    private final RedisOperationsService redis;

    /**
     * JSON 编解码器，用于序列化范围缓存。
     */
    private final ObjectMapper json;

    /**
     * 服务间共享内部令牌。
     */
    private final String internalToken;

    /**
     * 创建评价鉴权服务。
     *
     * @param scopeClient   用户数据范围内部客户端
     * @param windowClient  档口内部查询客户端
     * @param redis         Redis 基础操作服务
     * @param json          JSON 编解码器
     * @param internalToken 服务间共享内部令牌
     */
    public ReviewAuthorityServiceImpl(CommentScopeClient scopeClient, CommentWindowClient windowClient,
                                      RedisOperationsService redis, ObjectMapper json,
                                      @Value("${internal.scope.token:}") String internalToken) {
        this.scopeClient = scopeClient;
        this.windowClient = windowClient;
        this.redis = redis;
        this.json = json;
        this.internalToken = internalToken;
    }

    /**
     * 校验操作者是否可以修改或删除指定评价。
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param ownerId    评价作者编号
     * @param windowId   评价所属档口编号
     */
    @Override
    public void requireManageable(Long operatorId, List<String> roles, Long ownerId, Long windowId) {
        if (operatorId == null || operatorId <= 0) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 作者本人始终可管理自己的评价，不受角色与数据范围影响。
        if (operatorId.equals(ownerId)) {
            return;
        }
        // 系统管理员与总经理不受档口范围限制：前者是需求指定的例外，后者按角色定位本就覆盖全平台。
        if (hasRole(roles, RoleCode.SYSTEM_ADMIN) || hasRole(roles, RoleCode.GENERAL_MANAGER)) {
            return;
        }
        // 餐厅经理、档口负责人等非总经理管理角色才需要逐个档口判定范围；普通学生到这里即越权。
        if (!hasAnyManagerRole(roles)) {
            throw forbidden("只能操作自己发布的评价");
        }
        if (!coversWindow(operatorId, windowId)) {
            throw forbidden("没有管理该档口评价的权限");
        }
    }

    /**
     * 判断操作者的数据范围是否覆盖指定档口。
     *
     * @param operatorId 当前登录用户编号
     * @param windowId   目标档口编号
     * @return 覆盖时返回 true
     */
    @Override
    public boolean coversWindow(Long operatorId, Long windowId) {
        if (windowId == null || windowId <= 0) {
            return false;
        }
        UserScopeSnapshot scopes = loadScopes(operatorId);
        // 全平台范围或直接授权的档口范围命中时，无需再查档口归属。
        if (scopes.coversWindow(windowId)) {
            return true;
        }
        if (scopes.getRestaurantIds().isEmpty()) {
            return false;
        }
        Long restaurantId = windowRestaurant(windowId);
        return restaurantId != null && scopes.coversRestaurant(restaurantId);
    }

    /**
     * 查询数据范围快照，优先读缓存。
     *
     * @param userId 用户编号
     * @return 范围快照；用户无任何范围时返回空快照
     */
    private UserScopeSnapshot loadScopes(Long userId) {
        if (userId == null) {
            return UserScopeSnapshot.empty(null);
        }
        String cacheKey = AuthzCacheKeys.userScope(userId);
        CommentUserScopeVO cached = readCache(cacheKey);
        if (cached != null) {
            return toSnapshot(cached, userId);
        }
        if (internalToken == null || internalToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "内部调用凭据未配置");
        }
        ApiResponse<CommentUserScopeVO> response;
        try {
            response = scopeClient.getScopes(userId, internalToken);
        } catch (FeignException exception) {
            log.warn("查询用户数据范围失败，userId={}, httpStatus={}", userId, exception.status());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认数据范围，请稍后重试");
        }
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            // 范围查询失败不能当成"没有范围"继续处理，否则依赖恢复前后行为不一致。
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认数据范围，请稍后重试");
        }
        writeCache(cacheKey, response.getData());
        return toSnapshot(response.getData(), userId);
    }

    /**
     * 查询档口所属餐厅。
     *
     * @param windowId 档口编号
     * @return 餐厅编号；档口不存在时返回 null
     */
    private Long windowRestaurant(Long windowId) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "内部调用凭据未配置");
        }
        ApiResponse<CommentWindowInfoVO> response;
        try {
            response = windowClient.getWindow(windowId, internalToken);
        } catch (FeignException exception) {
            log.warn("查询档口归属失败，windowId={}, httpStatus={}", windowId, exception.status());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认档口归属，请稍后重试");
        }
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认档口归属，请稍后重试");
        }
        return response.getData() == null ? null : response.getData().restaurantId();
    }

    /**
     * 从 Redis 读取范围缓存。
     *
     * @param cacheKey 缓存键
     * @return 缓存的范围；未命中或缓存损坏时返回 null
     */
    private CommentUserScopeVO readCache(String cacheKey) {
        try {
            String cached = redis.getValue(cacheKey);
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return json.readValue(cached, CommentUserScopeVO.class);
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
     * @param scope    范围事实
     */
    private void writeCache(String cacheKey, CommentUserScopeVO scope) {
        try {
            redis.setValue(cacheKey, json.writeValueAsString(scope), AuthzCacheKeys.USER_SCOPE_TTL);
        } catch (RuntimeException | IOException exception) {
            // 写入失败只影响下次是否回源，不影响本次判定结果。
            log.warn("写入用户数据范围缓存失败：key={}", cacheKey);
        }
    }

    /**
     * 把内部契约对象转换为范围快照。
     *
     * @param scope  内部契约范围对象
     * @param userId 用户编号
     * @return 范围快照
     */
    private UserScopeSnapshot toSnapshot(CommentUserScopeVO scope, Long userId) {
        return new UserScopeSnapshot(userId, scope.platform(),
                toIdSet(scope.windowIds()), toIdSet(scope.restaurantIds()));
    }

    /**
     * 把编号列表转换为集合。
     *
     * @param ids 编号列表，可空
     * @return 只读编号集合
     */
    private Set<Long> toIdSet(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(ids);
    }

    /**
     * 判断是否持有指定角色。
     *
     * @param roles 角色编码集合，可空
     * @param role  目标角色
     * @return 持有时返回 true
     */
    private boolean hasRole(List<String> roles, RoleCode role) {
        return roles != null && roles.contains(role.getCode());
    }

    /**
     * 判断是否持有需要按范围判定的管理角色。
     *
     * <p>只包含餐厅经理与档口负责人：总经理已在前面直接放行，普通学生命中该分支说明在操作他人评价。</p>
     *
     * @param roles 角色编码集合，可空
     * @return 持有任一受限管理角色时返回 true
     */
    private boolean hasAnyManagerRole(List<String> roles) {
        return hasRole(roles, RoleCode.RESTAURANT_MANAGER)
                || hasRole(roles, RoleCode.STALL_MANAGER);
    }

    /**
     * 构造统一的越权异常。
     *
     * @param message 提示信息
     * @return 越权异常
     */
    private BusinessException forbidden(String message) {
        return new BusinessException(CommonErrorCode.FORBIDDEN, message);
    }
}
