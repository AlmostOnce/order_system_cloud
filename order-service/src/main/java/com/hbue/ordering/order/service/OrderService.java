package com.hbue.ordering.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.enums.OrderStatus;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.order.vo.OrderPageVO;
import com.hbue.ordering.order.vo.OrderStatusVO;
import com.hbue.ordering.user.api.vo.UserBasicVO;


/**
 * 订单业务服务契约。
 *
 * <p>定义订单查询、创建、取消、支付确认及商家履约推进等业务能力，并继承 MyBatis-Plus
 * 通用服务能力。具体规则由实现类负责，订单头与明细的本地事务由
 * {@link OrderPersistenceService} 管理；Controller 不应绕过本接口直接操作 Mapper。</p>
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException 用户服务返回失败或不可用时抛出
     */
    UserBasicVO getUserBasic(Long userId);

    /**
     * 查询当前用户的订单详情。
     *
     * @param userId 当前登录用户 ID
     * @param orderId 订单 ID
     * @return 订单详情
     * @throws com.hbue.ordering.common.core.exception.BusinessException 订单不存在、非本人订单或用户服务调用失败时抛出
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException 请求无效、核价失败、幂等键冲突或依赖服务不可用时抛出
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException 订单非本人所有、状态不可取消或并发更新冲突时抛出
     */
    OrderStatusVO cancelOrder(Long userId, Long orderId);

    /** 系统到期取消，服务端复核期限；不记录为顾客操作。 */
    OrderStatusVO cancelExpiredOrder(Long orderId);

    /**
     * 商家按合法履约顺序推进订单状态。
     *
     * <p>调用方必须持有 STALL_MANAGER 或 RESTAURANT_MANAGER 角色；
     * 订单属私有数据，目标订单不在操作者数据范围内时按不存在处理（404）。
     * 只允许推进到制作中或待取货；已完成通过取餐码核销服务处理。</p>
     *
     * @param operatorId 当前登录操作者 ID
     * @param orderId 订单 ID
     * @param targetStatus 目标状态
     * @return 订单状态变更结果
     * @throws com.hbue.ordering.common.core.exception.BusinessException 数据范围不足、状态迁移非法或并发更新冲突时抛出
     */
    OrderStatusVO advanceOrderStatus(
            Long operatorId,
            Long orderId,
            OrderStatus targetStatus
    );

    /**
     * 支付回调驱动的内部订单推进：待支付到已支付。
     *
     * <p>该接口仅由支付服务在渠道回调验签与金额校验通过后调用，
     * 顾客与管理员均无权直接标记订单已支付；
     * 重复通知（订单已是已支付）按幂等成功处理。</p>
     *
     * @param orderId 订单 ID
     * @return 订单状态变更结果
     * @throws com.hbue.ordering.common.core.exception.BusinessException 订单不存在或状态不允许支付确认时抛出
     */
    OrderStatusVO markOrderPaid(Long orderId, java.time.LocalDateTime paidAt);

    /** 读取受内部令牌保护的销量事实快照，供异步统计消费者兼容旧版仅含订单号的消息。 */
    com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO getPaidSalesSnapshot(Long orderId);


    /**
     * 分页查询当前用户的订单摘要，筛选在数据库内完成；每次最多返回50条，不统计全部记录数。
     * @param userId 从认证上下文取得的用户编号
     * @param status all、pending、active、completed或cancelled，未知值拒绝
     * @param cursor 本人订单主键的十进制字符串，首屏为空；不接受他人游标
     * @param limit 每页1至50条，Controller默认20条
     * @return 包含items、nextCursor、hasMore的订单页；店铺名称由调用方按页批量补全
     */
    OrderPageVO pageByUserId(Long userId, String status, String cursor, int limit);
}
