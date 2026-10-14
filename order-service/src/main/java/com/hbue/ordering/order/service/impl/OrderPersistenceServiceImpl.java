package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.service.OrderPersistenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单及其明细的原子持久化服务实现。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Service
public class OrderPersistenceServiceImpl implements OrderPersistenceService {

    /** 订单表 Mapper。 */
    private final OrderMapper orderMapper;

    /** 订单明细表 Mapper。 */
    private final OrderItemMapper orderItemMapper;

    /**
     * 创建订单原子持久化服务实现。
     *
     * @param orderMapper 订单表 Mapper
     * @param orderItemMapper 订单明细表 Mapper
     */
    public OrderPersistenceServiceImpl(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper
    ) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public OrderDO saveOrderWithItems(
            OrderDO order,
            List<OrderItemDO> items
    ) {
        // 先插入订单主记录以取得数据库生成的主键；事务保证后续失败时主记录也回滚。
        if (orderMapper.insert(order) != 1) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "订单创建失败"
            );
        }

        // 明细统一关联新生成的订单 ID；任何一行保存失败都会抛异常并回滚整个订单事务。
        for (OrderItemDO item : items) {
            item.setOrderId(order.getOrderId());
            if (orderItemMapper.insert(item) != 1) {
                throw new BusinessException(
                        CommonErrorCode.INTERNAL_ERROR,
                        "订单明细保存失败"
                );
            }
        }

        return order;
    }

    /** {@inheritDoc} */
    @Override
    public boolean updateStatusIfUnchanged(
            Long orderId,
            String expectedStatus,
            Integer expectedVersion,
            String targetStatus
    ) {
        // 仅当旧状态与版本号同时匹配才执行迁移；受影响行数为 0 表示状态已被其他请求改变。
        return orderMapper.updateStatusIfVersionMatches(
                orderId,
                expectedStatus,
                expectedVersion,
                targetStatus
        ) == 1;
    }
}
