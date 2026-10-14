package com.hbue.ordering.order.service;

import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;

import java.util.List;

/**
 * 订单及其明细的原子持久化服务接口。
 *
 * <p>封装需要明确事务边界的订单写操作，避免业务编排分别写订单头和明细造成部分提交；
 * 本接口只保证订单服务本地数据库写入原子性，不负责商品核价、用户授权或跨服务事务。</p>
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException 任一订单或明细插入失败时抛出并回滚事务
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
