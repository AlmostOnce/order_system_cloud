package com.hbue.ordering.order.enums;

/**
 * 订单状态及其合法流转规则。
 *
 * @author order-system
 * @date 2026-09-28
 */
public enum OrderStatus {

    /** 等待顾客完成支付。 */
    PENDING_PAYMENT("待支付"),

    /** 支付已成功，等待商家接单。 */
    PAID("已支付"),

    /** 商家正在制作订单。 */
    PREPARING("制作中"),

    /** 订单已制作完成，等待顾客取货。 */
    WAITING_FOR_PICKUP("待取货"),

    /** 顾客已完成取货，订单生命周期结束。 */
    COMPLETED("已完成"),

    /** 顾客取消了尚未支付的订单。 */
    CANCELLED("已取消");

    /** 数据库及现有接口使用的中文状态值。 */
    private final String label;

    /**
     * 创建订单状态。
     *
     * @param label 数据库及接口使用的中文状态值
     */
    OrderStatus(String label) {
        this.label = label;
    }

    /**
     * 获取数据库及接口使用的中文状态值。
     *
     * @return 中文状态值
     */
    public String getLabel() {
        return label;
    }

    /**
     * 判断当前状态能否流转到目标状态。
     *
     * @param targetStatus 目标状态
     * @return 状态迁移合法时返回 true
     */
    public boolean canTransitionTo(OrderStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }

        return switch (this) {
            case PENDING_PAYMENT -> targetStatus == PAID
                    || targetStatus == CANCELLED;
            case PAID -> targetStatus == PREPARING;
            case PREPARING -> targetStatus == WAITING_FOR_PICKUP;
            case WAITING_FOR_PICKUP -> targetStatus == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /**
     * 判断管理员能否执行当前状态到目标状态的履约推进。
     *
     * <p>支付状态只能由后续支付结果通知推进，管理员不能手动标记已支付；
     * 顾客取消也不属于管理员履约操作。</p>
     *
     * @param targetStatus 目标状态
     * @return 管理员可以执行时返回 true
     */
    public boolean canBeAdvancedByAdminTo(OrderStatus targetStatus) {
        return this != PENDING_PAYMENT
                && targetStatus != CANCELLED
                && canTransitionTo(targetStatus);
    }

    /**
     * 根据数据库中的中文状态值查找枚举。
     *
     * @param label 数据库状态值
     * @return 匹配的订单状态；未知或空状态返回 null
     */
    public static OrderStatus findByLabel(String label) {
        if (label == null) {
            return null;
        }

        for (OrderStatus orderStatus : values()) {
            if (orderStatus.label.equals(label)) {
                return orderStatus;
            }
        }
        return null;
    }
}
