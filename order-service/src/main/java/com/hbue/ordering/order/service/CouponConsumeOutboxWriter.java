package com.hbue.ordering.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.enums.OrderStatus;
import com.hbue.ordering.order.api.dto.CouponConsumeConfirmationRequest;
import com.hbue.ordering.order.mapper.CouponConsumeOutboxMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 将成功支付订单绑定的优惠券核销事实可靠写入订单库 Outbox。 */
@Service
public class CouponConsumeOutboxWriter {

    /** 本地订单库待发事件存储，和订单支付状态共用本地事务。 */
    private final CouponConsumeOutboxMapper outbox;
    /** 支付 CAS 完成后读取的订单事实来源。 */
    private final OrderMapper orders;
    /** 生成固定消息体快照，不能在重试时重新拼出不同载荷。 */
    private final ObjectMapper json;

    /**
     * 注入订单事实、Outbox Mapper 和 JSON 编码器，写入始终加入支付状态本地事务。
     * @param outbox 当前订单库核销事件存储
     * @param orders 提供已确认支付订单事实的 Mapper
     * @param json 序列化并持久化稳定消息快照的编码器
     */
    public CouponConsumeOutboxWriter(CouponConsumeOutboxMapper outbox, OrderMapper orders, ObjectMapper json) {
        this.outbox = outbox;
        this.orders = orders;
        this.json = json;
    }

    /**
     * 对已支付且确实绑定用户券的订单记录唯一核销事件；无券订单不产生 MQ 消息。
     *
     * @param orderId 已由支付 CAS 更新为成功的订单主键
     * @throws BusinessException 订单支付事实缺失或不完整时拒绝提交状态
     * @throws IllegalStateException 事件落库或序列化失败时让本地事务一并回滚
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaid(Long orderId) {
        OrderDO order = orders.selectById(orderId);
        if (order == null || order.getPaidAt() == null
                || !OrderStatus.PAID.getLabel().equals(order.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "优惠券核销事件缺少已支付订单事实");
        }
        if (order.getUserCouponId() == null) return;
        if (order.getUserCouponId() <= 0 || order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "优惠券核销事件缺少订单或用户券编号");
        }

        OrderCouponConsumeEvent event = OrderCouponConsumeEvent.of(order.getOrderNo(), order.getUserCouponId());
        if (!event.valid()) throw new BusinessException(CommonErrorCode.CONFLICT, "优惠券核销事件格式不正确");
        try {
            if (outbox.insert(event.eventId(), event.orderNo(), event.userCouponId(),
                    json.writeValueAsString(event)) != 1) {
                throw new IllegalStateException("优惠券核销事件保存失败");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("优惠券核销事件编码失败", exception);
        }
    }

    /**
     * 核验券服务的消费回执并将 Outbox 标为 CONFIRMED；重复回执只对同一事件三元组幂等成功。
     * @param request 券服务确认已提交的订单号、用户券和事件 ID
     * @throws BusinessException 请求不完整或与订单 Outbox 快照不匹配时拒绝确认
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(CouponConsumeConfirmationRequest request) {
        if (request == null || request.eventId() == null || request.eventId().isBlank()
                || request.orderNo() == null || request.orderNo().isBlank()
                || request.userCouponId() == null || request.userCouponId() <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "优惠券核销回执缺少事件身份");
        }
        if (outbox.confirm(request.eventId(), request.orderNo(), request.userCouponId()) == 1) return;
        if (outbox.isConfirmed(request.eventId(), request.orderNo(), request.userCouponId()) == 1) return;
        throw new BusinessException(CommonErrorCode.CONFLICT, "优惠券核销回执与订单 Outbox 不匹配");
    }
}
