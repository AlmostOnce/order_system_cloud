package com.hbue.ordering.order.util;

import com.hbue.ordering.order.model.OrderDO;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 订单支付截止时间的统一推导。
 *
 * <p>订单创建时固定十五分钟支付窗口；历史记录在迁移前没有 {@code expires_at} 列，
 * 只能按创建时间加十五分钟推导，保证新旧数据使用同一套超时判定口径。</p>
 *
 * <p>本类只做纯计算、不访问数据库，放在 util 包而不是 service 包，避免被误认为可注入的服务。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
public final class OrderPaymentDeadline {

    /**
     * 支付窗口长度；与订单创建时写入 {@code expires_at} 的规则保持一致。
     */
    private static final long PAYMENT_WINDOW_MINUTES = 15;

    /**
     * 业务时区；数据库存的是东八区本地时间，换算时间戳时必须显式指定，不能依赖主机默认时区。
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 工具类不允许实例化。
     */
    private OrderPaymentDeadline() {
    }

    /**
     * 计算订单支付截止时刻的毫秒时间戳。
     *
     * <p>优先使用持久化的 {@code expires_at}；缺失时按创建时间推导；两者都缺失说明订单数据损坏，
     * 直接抛异常而不是返回 0，否则调用方会把损坏数据误判为"已过期"并自动关单。</p>
     *
     * @param order 订单持久化对象
     * @return 支付截止时刻的毫秒时间戳（东八区口径）
     * @throws IllegalStateException 订单既无截止时间也无创建时间
     */
    public static long epochMillis(OrderDO order) {
        LocalDateTime deadline = order.getExpiresAt();
        if (deadline == null && order.getCreatedAt() != null) deadline = order.getCreatedAt().plusMinutes(PAYMENT_WINDOW_MINUTES);
        if (deadline == null) throw new IllegalStateException("订单缺少支付截止时间");
        return deadline.atZone(BUSINESS_ZONE).toInstant().toEpochMilli();
    }
}
