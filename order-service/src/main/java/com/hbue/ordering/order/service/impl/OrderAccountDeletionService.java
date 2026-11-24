package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.mapper.OrderRefundMapper;
import com.hbue.ordering.order.vo.AccountDeletionBlockersVO;
import org.springframework.stereotype.Service;

/**
 * 账号注销的订单侧阻断查询。
 *
 * <p>把订单与退款两张表的统计封装在订单服务内部，用户服务只通过内部接口获取计数。
 * 本服务只读，不修改订单或退款状态；调用方在每次资格校验和到期处理时重新查询，
 * 不使用缓存，避免用户在处理间隙产生新订单后被误注销。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Service
public class OrderAccountDeletionService {

    /**
     * 订单主表数据访问。
     */
    private final OrderMapper orderMapper;

    /**
     * 退款数据访问。
     */
    private final OrderRefundMapper orderRefundMapper;

    /**
     * 创建订单侧阻断查询服务。
     *
     * @param orderMapper       订单主表数据访问
     * @param orderRefundMapper 退款数据访问
     */
    public OrderAccountDeletionService(OrderMapper orderMapper, OrderRefundMapper orderRefundMapper) {
        this.orderMapper = orderMapper;
        this.orderRefundMapper = orderRefundMapper;
    }

    /**
     * 查询指定用户的订单与退款阻断计数。
     *
     * @param userId 用户 ID
     * @return 阻断统计
     */
    public AccountDeletionBlockersVO blockers(Long userId) {
        return new AccountDeletionBlockersVO(
                orderMapper.countUnfinishedForAccountDeletion(userId),
                orderRefundMapper.countUnsettledForAccountDeletion(userId),
                orderRefundMapper.countRejectedForAccountDeletion(userId)
        );
    }
}
