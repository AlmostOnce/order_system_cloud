package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.client.CommentPurchaseFactClient;
import com.hbue.ordering.comment.client.vo.ReviewCandidateVO;
import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.mapper.WindowReviewMapper;
import com.hbue.ordering.comment.service.PendingReviewService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 待评价订单统计实现。
 *
 * <p>跨服务数据流：先向订单服务读取最近已完成订单的明细编号，再一次性查询评价表中
 * 已提交的店铺评价与菜品评价，最后按“店铺缺失或任一菜品缺失”计数。
 * 只扫描有限数量的最近订单，避免为了角标拉取全部历史；因此数量是“最近订单范围内”的
 * 待评价数，不是无限历史的精确总数，接口不对外承诺可据此定位具体订单。</p>
 *
 * <p>订单服务不可用时返回 503，而不是 0：0 会让用户误以为没有待评价订单，
 * 把下游故障伪装成正常空态，属于错误的业务结论。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Service
public class PendingReviewServiceImpl implements PendingReviewService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(PendingReviewServiceImpl.class);

    /**
     * 最多向订单服务扫描的已完成订单数。
     *
     * <p>与 order-service 内部接口的默认上限保持一致；评论服务主动限制，
     * 避免依赖下游默认值。</p>
     */
    private static final int MAX_CANDIDATE_ORDERS = 200;

    /**
     * 订单服务消费事实客户端，同时承载待评价候选查询。
     */
    private final CommentPurchaseFactClient orderFacts;

    /**
     * 菜品评价只读查询。
     */
    private final CommentMapper comments;

    /**
     * 店铺评价只读查询。
     */
    private final WindowReviewMapper windowReviews;

    /**
     * 服务间共享内部令牌。
     */
    private final String internalToken;

    /**
     * 创建待评价统计服务。
     *
     * @param orderFacts    订单服务内部客户端
     * @param comments      菜品评价查询
     * @param windowReviews 店铺评价查询
     * @param internalToken 订单服务内部接口共享令牌
     */
    public PendingReviewServiceImpl(CommentPurchaseFactClient orderFacts, CommentMapper comments,
                                    WindowReviewMapper windowReviews,
                                    @Value("${internal.order.token:}") String internalToken) {
        this.orderFacts = orderFacts;
        this.comments = comments;
        this.windowReviews = windowReviews;
        this.internalToken = internalToken;
    }

    /**
     * 统计待评价订单数。
     *
     * <p>同一订单的多条明细按订单号聚合，菜品号去重；只用订单号集合查询评价表，
     * 保证无论订单有多少道菜都只发两次查询。订单行缺少必要编号时跳过该行并记日志，
     * 不让单条脏数据把整个角标变成 503。</p>
     *
     * @param userId 当前登录用户内部编号
     * @return 待评价订单数
     */
    @Override
    public int count(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "请先登录");
        }
        List<ReviewCandidateVO> candidates = loadCandidates(userId);
        if (candidates.isEmpty()) {
            return 0;
        }

        // 用 LinkedHashMap 保持订单顺序，便于日志排查；windowId 只做合法性校验，不参与计数。
        LinkedHashMap<Long, Long> orderWindows = new LinkedHashMap<>();
        LinkedHashMap<Long, LinkedHashSet<Long>> orderProducts = new LinkedHashMap<>();
        for (ReviewCandidateVO candidate : candidates) {
            if (candidate == null || candidate.orderId() == null || candidate.orderId() <= 0
                    || candidate.windowId() == null || candidate.windowId() <= 0
                    || candidate.productId() == null || candidate.productId() <= 0) {
                log.warn("待评价候选行缺少有效编号，userId={}, candidate={}", userId, candidate);
                continue;
            }
            // 同一订单只保留一个档口；菜品号去重，避免同菜品多行导致重复判断。
            orderWindows.putIfAbsent(candidate.orderId(), candidate.windowId());
            LinkedHashSet<Long> products = orderProducts.get(candidate.orderId());
            if (products == null) {
                products = new LinkedHashSet<>();
                orderProducts.put(candidate.orderId(), products);
            }
            products.add(candidate.productId());
        }
        if (orderWindows.isEmpty()) {
            return 0;
        }

        List<Long> orderIds = new ArrayList<>(orderWindows.keySet());
        Set<Long> reviewedOrderIds = new HashSet<>(windowReviews.reviewedOrderIds(userId, orderIds));
        Set<String> reviewedProducts = new HashSet<>();
        for (CommentMapper.ReviewedProduct row : comments.reviewedProducts(userId, orderIds)) {
            if (row == null || row.orderId() == null || row.productId() == null) {
                continue;
            }
            reviewedProducts.add(productKey(row.orderId(), row.productId()));
        }

        int pending = 0;
        for (Long orderId : orderIds) {
            // 店铺评价缺失时整单计入待评价，不再逐个菜品判断；菜品只在该订单有店铺评价后检查。
            if (!reviewedOrderIds.contains(orderId)) {
                pending++;
                continue;
            }
            LinkedHashSet<Long> products = orderProducts.get(orderId);
            if (products == null || products.isEmpty()) {
                continue;
            }
            for (Long productId : products) {
                if (!reviewedProducts.contains(productKey(orderId, productId))) {
                    pending++;
                    break;
                }
            }
        }
        return pending;
    }

    /**
     * 调用订单服务读取待评价候选。
     *
     * <p>超时、非 2xx 或响应体缺数据统一返回 503：评论服务无法独立判断订单是否存在，
     * 不能把“下游不可用”降级成“没有待评价订单”。</p>
     *
     * @param userId 用户内部编号
     * @return 候选明细；没有订单时为空集合
     */
    private List<ReviewCandidateVO> loadCandidates(Long userId) {
        ApiResponse<List<ReviewCandidateVO>> response;
        try {
            response = orderFacts.reviewCandidates(internalToken, userId, MAX_CANDIDATE_ORDERS);
        } catch (FeignException exception) {
            log.warn("查询待评价候选失败，userId={}, status={}", userId, exception.status());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "待评价数量暂时无法获取，请稍后重试");
        }
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            log.warn("待评价候选返回异常，userId={}", userId);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "待评价数量暂时无法获取，请稍后重试");
        }
        return response.getData();
    }

    /**
     * 构造订单与菜品的组合键。
     *
     * <p>用冒号连接两个自增编号，只用于内存集合查找，不落库、不暴露给前端。</p>
     *
     * @param orderId   订单编号
     * @param productId 菜品编号
     * @return 组合键
     */
    private String productKey(Long orderId, Long productId) {
        return orderId + ":" + productId;
    }
}
