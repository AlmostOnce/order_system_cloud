package com.hbue.ordering.order.util;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 退款纯规则计算。
 *
 * <p>金额一律按订单实付行金额分摊，从不采信前端提交的金额；申请额度会把"处理中申请"已占用的
 * 份数与金额一并计入，保证多笔申请叠加后也不超过实付。本类无状态、不访问数据库，
 * 便于单测覆盖边界而不需要启动 Spring 上下文。</p>
 *
 * <p>状态判定使用 {@link OrderStatus} 的标签，避免中文状态字面量散落在业务代码中。</p>
 *
 * @author order-system
 * @date 2026-10-20
 */
public final class RefundRules {

    /**
     * 允许申请售后的订单状态集合：已支付、制作中、待取货、已完成。
     *
     * <p>待支付与已取消没有任何实付金额，已退款已完成全部退款，均不在可申请范围内。</p>
     */
    private static final Set<String> APPLICABLE_STATUSES = Set.of(
            OrderStatus.PAID.getLabel(),
            OrderStatus.PREPARING.getLabel(),
            OrderStatus.WAITING_FOR_PICKUP.getLabel(),
            OrderStatus.COMPLETED.getLabel());

    /**
     * 已支付订单的售后时限：完成后 72 小时内可申请。
     */
    private static final long REFUND_WINDOW_HOURS = 72;

    /**
     * 工具类不允许实例化。
     */
    private RefundRules() {
    }

    /**
     * 校验订单当前是否处于可申请售后的状态与时限内。
     *
     * <p>完成时间固定取订单完成时刻，不随售后记录的更新时间变化；恰好满 72 小时即拒绝，
     * 用 {@code isBefore} 而不是 {@code isAfter} 来固化这个边界。</p>
     *
     * @param status      订单当前中文状态
     * @param completedAt 订单完成时刻，仅"已完成"状态需要
     * @param now         当前时刻，由调用方传入便于测试
     * @throws BusinessException 状态不允许或已超过售后时限
     */
    public static void requireApplicable(String status, LocalDateTime completedAt, LocalDateTime now) {
        if (!APPLICABLE_STATUSES.contains(status)) throw bad("当前订单不能申请退款");
        if (OrderStatus.COMPLETED.getLabel().equals(status)
                && (completedAt == null || !now.isBefore(completedAt.plusHours(REFUND_WINDOW_HOURS))))
            throw bad("已超过完成后72小时申请期限");
    }

    /**
     * 计算本次可退款金额。
     *
     * <p>除最后一份外按行金额均分并向下取整到分，最后一份承接全部剩余金额，从而保证多笔申请
     * 累加后严格等于行实付，不产生"少退一分"或"多退一分"的尾差。</p>
     *
     * @param lineAmount 订单行实付金额，必须为正
     * @param total      该行购买总份数
     * @param reserved   已被其他处理中申请占用的份数
     * @param committed  已被其他处理中申请占用的金额
     * @param requested  本次申请份数
     * @return 两位小数的可退款金额
     * @throws BusinessException 份数越界或金额不足
     */
    public static BigDecimal amount(BigDecimal lineAmount, int total, int reserved, BigDecimal committed, int requested) {
        if (total <= 0 || requested <= 0 || reserved < 0 || requested > total - reserved || lineAmount == null || lineAmount.signum() <= 0)
            throw bad("可退款数量不足");
        BigDecimal remaining = lineAmount.subtract(committed);
        BigDecimal value = requested == total - reserved ? remaining : lineAmount.divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN).multiply(BigDecimal.valueOf(requested));
        if (value.signum() <= 0 || value.compareTo(remaining) > 0) throw bad("可退款金额不足");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * 构造统一的冲突异常，调用方无需重复指定错误码。
     *
     * @param text 面向用户的提示
     * @return 冲突类业务异常
     */
    private static BusinessException bad(String text) {
        return new BusinessException(CommonErrorCode.CONFLICT, text);
    }
}
