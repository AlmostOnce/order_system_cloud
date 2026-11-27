package com.hbue.ordering.order.model;

import lombok.Data;

/**
 * 数据库中待发布或已发布的优惠券核销 Outbox 行。
 *
 * <p>只是表结构的读写载体，不含业务规则；载荷字段存的是支付确认时序列化的固定 JSON 快照，
 * 发布器必须原样发送，不能反解析后重新拼装。</p>
 *
 * @author order-system
 * @date 2026-10-24
 */
@Data
public class CouponConsumeOutboxRow {

    /**
     * 与消息体一致的稳定事件 ID，用于 broker key 和重复投递幂等。
     */
    private String eventId;

    /**
     * 支付成功订单的业务订单号。
     */
    private String orderNo;

    /**
     * 订单创建时已锁定的用户券记录主键。
     */
    private Long userCouponId;

    /**
     * 支付确认本地事务中保存的 JSON 事件快照。
     */
    private String payload;
}
