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

    /**
     * 仅当订单状态和版本号都未变化时更新状态。
     *
     * @param orderId 订单 ID
     * @param expectedStatus 读取时的原状态
     * @param expectedVersion 读取时的版本号
     * @param targetStatus 目标状态
     * @return 更新成功时返回 true；状态或版本已变化时返回 false
     */
    boolean updateStatusIfUnchanged(
            Long orderId,
            String expectedStatus,
            Integer expectedVersion,
            String targetStatus
    );
}
