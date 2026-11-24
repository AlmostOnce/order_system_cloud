package com.hbue.ordering.notification.model;

/**
 * 用户可配置的站内通知分类。
 *
 * <p>编码同时写入 notification_preferences.category，并参与未读统计的 SQL 匹配：
 * ORDER 对应通知类型 ORDER_STATUS；SUPPORT 对应 SUPPORT_REPLY 与 SUPPORT_STATUS；
 * CHAT 对应商家聊天的未读角标与实时提示。编码一旦发布即被数据库记录引用，
 * 不得随意修改，新增分类时需同步补充通知类型映射。</p>
 *
 * <p>默认开启：数据库中缺少某分类记录时按开启处理，只有用户显式关闭才落库。
 * 关闭只抑制提醒（实时提示、未读角标），通知记录与聊天消息本身继续保留。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
public enum NotificationCategory {

    /**
     * 订单状态通知：支付、制作、待取货、退款结果等订单事件。
     */
    ORDER("ORDER", "订单状态通知", "支付、制作、待取货等订单状态变化"),

    /**
     * 反馈与工单通知：问题反馈的新回复与处理状态变化。
     */
    SUPPORT("SUPPORT", "反馈与工单通知", "反馈回复和工单处理进度变化"),

    /**
     * 商家聊天提醒：商家发来新消息时的未读红点。
     */
    CHAT("CHAT", "商家聊天提醒", "商家发来新消息时显示未读红点");

    /**
     * 数据库存储编码，参与条件匹配，发布后不可修改。
     */
    private final String code;

    /**
     * 设置页展示名称。
     */
    private final String label;

    /**
     * 设置页说明文案，向用户解释该分类包含哪些提醒。
     */
    private final String description;

    /**
     * 创建通知分类。
     *
     * @param code        数据库存储编码
     * @param label       展示名称
     * @param description 说明文案
     */
    NotificationCategory(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    /**
     * 返回数据库存储编码。
     *
     * @return 分类编码
     */
    public String code() {
        return code;
    }

    /**
     * 返回设置页展示名称。
     *
     * @return 展示名称
     */
    public String label() {
        return label;
    }

    /**
     * 返回设置页说明文案。
     *
     * @return 说明文案
     */
    public String description() {
        return description;
    }

    /**
     * 按数据库编码查找分类。
     *
     * <p>未知编码返回 null，由调用方决定拒绝请求还是按默认开启处理；
     * 这里不做兜底映射，避免把客户端的错误编码静默当成某个真实分类。</p>
     *
     * @param code 待查找的编码
     * @return 对应分类，未知编码返回 null
     */
    public static NotificationCategory fromCode(String code) {
        for (NotificationCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }
}
