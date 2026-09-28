package com.hbue.ordering.order.service;

import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;

import java.util.List;

/**
 * 订单及其明细的原子持久化服务接口。
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface OrderPersistenceService {

    /**
     * 在同一数据库事务内保存订单头和全部菜品快照。
     *
     * @param order 订单数据
     * @param items 订单菜品快照
     * @return 写入主键后的订单对象
     */
    OrderDO saveOrderWithItems(
            OrderDO order,
            List<OrderItemDO> items
    );
}
