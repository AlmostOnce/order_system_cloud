package com.hbue.ordering.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.enums.OrderStatus;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.order.vo.OrderStatusVO;
import com.hbue.ordering.user.api.vo.UserBasicVO;

import java.util.List;

/**
 * 订单业务服务。
 *
 * @author order-system
 * @date 2026-09-19
 */
public interface OrderService extends IService<OrderDO> {

    /**
     * 远程查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
     */
    UserBasicVO getUserBasic(Long userId);

    /**
     * 查询当前用户的订单详情。
     *
     * @param userId 当前登录用户 ID
     * @param orderId 订单 ID
     * @return 订单详情
     */
    OrderDetailVO getOrderDetailById(
            Long userId,
            Long orderId
    );

    /**
     * 创建订单。
     *
     * @param userId 当前登录用户 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 创建订单请求
     * @return 新创建的订单详情
     */
    OrderDetailVO createOrder(
            Long userId,
            String idempotencyKey,
            OrderCreateRequest request
    );

    /**
     * 顾客取消自己尚未支付的订单。
     *
     * @param userId 当前登录顾客 ID
     * @param orderId 订单 ID
     * @return 订单状态变更结果
     */
    OrderStatusVO cancelOrder(Long userId, Long orderId);

    /**
     * 管理员按合法履约顺序推进订单状态。
     *
     * @param orderId 订单 ID
     * @param targetStatus 目标状态
     * @return 订单状态变更结果
     */
    OrderStatusVO updateStatusByAdmin(
            Long orderId,
            OrderStatus targetStatus
    );


    /**
     * 查询当前用户的订单列表。
     *
     * @param userId 当前登录用户 ID
     * @return 当前用户的订单列表
     */
    List<OrderDO> listByUserId(Long userId);
}
