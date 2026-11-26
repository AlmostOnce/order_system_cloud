package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.client.CommentPurchaseFactClient;
import com.hbue.ordering.comment.client.vo.PurchaseFactVO;
import com.hbue.ordering.comment.model.ReviewType;
import com.hbue.ordering.comment.service.DiscussionCacheKeys;
import com.hbue.ordering.comment.service.DiscussionEligibilityService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.role.RoleCode;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.response.ApiResponse;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 评论消费准入的实现。
 *
 * <p>判定顺序：管理角色直接豁免 → 读短缓存 → 向订单服务查询 → 回写缓存。
 * 缓存只保存布尔结论，不保存订单明细；"已消费"结果稳定（已完成订单是终态），
 * 可以缓存 5 分钟；"未消费"可能因为用户刚取餐而变化，只缓存 30 秒。</p>
 *
 * <p>失败语义与限流相反：准入是业务规则，订单服务不可用时返回 503 而不是放行，
 * 否则"必须消费过"会因下游故障形同虚设。Redis 不可用时跳过缓存直查，
 * 直查仍失败才拒绝，不把缓存当作准入依据。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Service
public class DiscussionEligibilityServiceImpl implements DiscussionEligibilityService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(DiscussionEligibilityServiceImpl.class);

    /**
     * 缓存中表示"已消费"的值。
     */
    private static final String CACHE_ELIGIBLE = "1";

    /**
     * 缓存中表示"未消费"的值。
     */
    private static final String CACHE_INELIGIBLE = "0";

    /**
     * 已消费结果的缓存时长：已完成订单不可退款，结论稳定。
     */
    private static final Duration ELIGIBLE_TTL = Duration.ofMinutes(5);

    /**
     * 未消费结果的缓存时长：用户可能刚完成订单，窗口必须短。
     */
    private static final Duration INELIGIBLE_TTL = Duration.ofSeconds(30);

    /**
     * 订单服务消费事实客户端。
     */
    private final CommentPurchaseFactClient facts;

    /**
     * Redis 基础操作服务。
     */
    private final RedisOperationsService redis;

    /**
     * 服务间共享内部令牌。
     */
    private final String internalToken;

    /**
     * 创建消费准入服务。
     *
     * @param facts         订单服务消费事实客户端
     * @param redis         Redis 基础操作服务
     * @param internalToken 服务间共享内部令牌
     */
    public DiscussionEligibilityServiceImpl(CommentPurchaseFactClient facts, RedisOperationsService redis,
                                            @Value("${internal.scope.token:}") String internalToken) {
        this.facts = facts;
        this.redis = redis;
        this.internalToken = internalToken;
    }

    /**
     * 校验操作者消费过评价对象。
     *
     * @param userId   当前登录用户内部编号
     * @param roles    当前登录用户的角色编码集合
     * @param type     评价类型
     * @param targetId 菜品编号或档口编号
     */
    @Override
    public void requireConsumed(Long userId, List<String> roles, ReviewType type, Long targetId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 商家与管理员是评价区的经营者，不要求他们先购买自己的商品。
        if (isManager(roles)) {
            return;
        }
        if (type == null || targetId == null || targetId <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "评论目标不明确");
        }

        String cacheKey = DiscussionCacheKeys.eligible(userId, type.purchaseTargetType(), targetId);
        String cached = readCache(cacheKey);
        if (CACHE_ELIGIBLE.equals(cached)) {
            return;
        }
        if (CACHE_INELIGIBLE.equals(cached)) {
            throw notPurchased(type);
        }

        boolean purchased = queryPurchaseFact(userId, type, targetId);
        writeCache(cacheKey, purchased);
        if (!purchased) {
            throw notPurchased(type);
        }
    }

    /**
     * 判断是否属于管理角色。
     *
     * <p>只要持有一个管理角色就豁免消费校验，不区分档口与餐厅范围：
     * 评论本身是公开讨论，商家跨档口发言不构成越权，屏蔽操作才需要范围校验。</p>
     *
     * @param roles 角色编码集合
     * @return 属于管理角色时返回 true
     */
    private boolean isManager(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        if (roles.contains(RoleCode.STALL_MANAGER.getCode())
                || roles.contains(RoleCode.RESTAURANT_MANAGER.getCode())
                || roles.contains(RoleCode.GENERAL_MANAGER.getCode())
                || roles.contains(RoleCode.SYSTEM_ADMIN.getCode())) {
            return true;
        }
        return false;
    }

    /**
     * 向订单服务查询消费事实。
     *
     * @param userId   用户内部编号
     * @param type     评价类型
     * @param targetId 菜品或档口编号
     * @return 是否已消费
     */
    private boolean queryPurchaseFact(Long userId, ReviewType type, Long targetId) {
        ApiResponse<PurchaseFactVO> response;
        try {
            response = facts.purchaseFact(internalToken, userId, type.purchaseTargetType(), targetId);
        } catch (FeignException exception) {
            // 下游超时或报错时不能默认放行，也不能伪装成"未消费"，必须让用户稍后重试。
            log.warn("查询消费事实失败，userId={}, type={}, targetId={}, status={}",
                    userId, type.code(), targetId, exception.status());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认消费记录，请稍后重试");
        }
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            log.warn("消费事实返回异常，userId={}, type={}, targetId={}", userId, type.code(), targetId);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认消费记录，请稍后重试");
        }
        return response.getData().purchased();
    }

    /**
     * 构造"未消费"的拒绝异常。
     *
     * @param type 评价类型
     * @return 403 业务异常
     */
    private BusinessException notPurchased(ReviewType type) {
        String target = type == ReviewType.COMMENT ? "该菜品" : "该店铺";
        return new BusinessException(CommonErrorCode.FORBIDDEN, "仅购买过" + target + "的用户可以评论");
    }

    /**
     * 读取准入缓存。
     *
     * <p>Redis 故障时返回 null 并按未命中处理，继续向订单服务查询；
     * 缓存只是加速，不能因为读不到缓存就拒绝正常用户。</p>
     *
     * @param key 缓存键
     * @return 缓存值，未命中或异常时返回 null
     */
    private String readCache(String key) {
        try {
            return redis.getValue(key);
        } catch (RuntimeException exception) {
            log.warn("读取消费准入缓存失败，key={}", key, exception);
            return null;
        }
    }

    /**
     * 回写准入缓存。
     *
     * <p>只有订单服务明确返回结论时才调用；写失败只记日志，不影响本次校验结果。</p>
     *
     * @param key       缓存键
     * @param purchased 是否已消费
     */
    private void writeCache(String key, boolean purchased) {
        try {
            if (purchased) {
                redis.setValue(key, CACHE_ELIGIBLE, ELIGIBLE_TTL);
            } else {
                redis.setValue(key, CACHE_INELIGIBLE, INELIGIBLE_TTL);
            }
        } catch (RuntimeException exception) {
            log.warn("写入消费准入缓存失败，key={}", key, exception);
        }
    }
}
