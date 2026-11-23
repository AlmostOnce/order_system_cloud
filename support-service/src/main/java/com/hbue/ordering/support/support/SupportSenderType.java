package com.hbue.ordering.support.support;

/**
 * 工单时间线消息发送方类型。
 *
 * <p>学生补充与商家公开回复都对学生可见；系统消息由状态变更自动写入，
 * 居中展示；内部备注使用 STAFF 发送方但带 {@code internal_note = 1}，
 * 学生接口在 SQL 层过滤，不能仅依赖前端隐藏。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
public enum SupportSenderType {

    /**
     * 提交工单的学生。
     */
    CUSTOMER("CUSTOMER", "学生"),

    /**
     * 受理工单的员工（公开回复或内部备注）。
     */
    STAFF("STAFF", "工作人员"),

    /**
     * 系统自动消息，例如已受理、已关闭。
     */
    SYSTEM("SYSTEM", "系统");

    /**
     * 对外稳定编码，同时也是数据库存储值。
     */
    private final String code;

    /**
     * 面向学生的中文名称。
     */
    private final String label;

    /**
     * 创建发送方类型。
     *
     * @param code  对外稳定编码
     * @param label 中文名称
     */
    SupportSenderType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 获取稳定编码。
     *
     * @return 发送方编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取中文名称。
     *
     * @return 发送方名称
     */
    public String getLabel() {
        return label;
    }
}
