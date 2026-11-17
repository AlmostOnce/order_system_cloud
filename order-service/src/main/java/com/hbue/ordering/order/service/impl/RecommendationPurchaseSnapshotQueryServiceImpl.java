package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotPageVO;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.recommendation.RecommendationPurchaseSnapshotFactory;
import com.hbue.ordering.order.service.RecommendationPurchaseSnapshotQueryService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通过订单权威库提供有界历史回填，不读取推荐库或其他服务数据库，不允许按客户端userId导出。
 * 每页订单头和明细在同一可重复读事务中获取，防止并发退款让旧版本订单头搭配新退款份数。
 */
@Service
public class RecommendationPurchaseSnapshotQueryServiceImpl implements RecommendationPurchaseSnapshotQueryService {
    /** 每页业务上限，额外读取一条仅用于判定是否还有后续订单。 */
    private static final int MAX_LIMIT = 100;
    /** 订单与明细均属order_db，本页不持有业务写锁或调用远程服务。 */
    private final OrderMapper orders;
    private final OrderItemMapper items;

    /** 注入权威事实读取接口，批量读取避免随页大小增加数据库往返次数。 */
    public RecommendationPurchaseSnapshotQueryServiceImpl(OrderMapper orders, OrderItemMapper items) {
        this.orders = orders; this.items = items;
    }

    /**
     * 按订单主键正序回填。实时消费应先启动，后来才支付的旧订单由实时事件补齐，不承诺跨页全局冻结。
     *
     * @param afterOrderId 排他下界；首次为0，后续使用响应游标，不接受负数
     * @param limit 一页1至100个订单，不按明细行截断完整订单
     * @return 当前源版本完整快照及下一游标，空页不执行明细IN查询
     * @throws BusinessException 参数非法时返回标准400
     * @throws IllegalStateException 缺失明细或损坏事实时整页失败，不推进游标而漏掉订单
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Override
    public RecommendationPurchaseSnapshotPageVO page(long afterOrderId, int limit) {
        if (afterOrderId < 0 || limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "购买快照游标或分页数量无效");
        }
        List<OrderDO> rows = orders.selectRecommendationPurchasePage(afterOrderId, limit + 1);
        if (rows == null) throw new IllegalStateException("订单回填未返回可确认的结果");
        boolean hasMore = rows.size() > limit;
        var visible = rows.stream().limit(limit).toList();
        if (visible.isEmpty()) return new RecommendationPurchaseSnapshotPageVO(List.of(), null, false);
        var ids = visible.stream().map(OrderDO::getOrderId).toList();
        List<OrderItemDO> details = items.selectRecommendationItems(ids);
        if (details == null) throw new IllegalStateException("订单回填未返回明细事实");
        Map<Long, List<OrderItemDO>> byOrder = details.stream().collect(Collectors.groupingBy(OrderItemDO::getOrderId));
        var snapshots = visible.stream().map(order -> RecommendationPurchaseSnapshotFactory.create(
                order, byOrder.getOrDefault(order.getOrderId(), List.of()))).toList();
        Long next = hasMore ? visible.get(visible.size() - 1).getOrderId() : null;
        return new RecommendationPurchaseSnapshotPageVO(snapshots, next, hasMore);
    }
}
