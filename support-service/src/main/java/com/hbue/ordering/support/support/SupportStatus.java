package com.hbue.ordering.support.support;

/**
 * 反馈工单状态。
 *
 * <p>状态流转只允许 {@code PENDING → PROCESSING → CLOSED}，学生可在任何未关闭状态
 * 主动关闭。所有状态变更必须带原状态条件更新，避免并发下先查后改覆盖他人操作。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
public enum SupportStatus {

    /**
     * 待受理：学生已提交，尚无员工受理。
     */
    PENDING("PENDING", "待受理"),

    /**
     * 处理中：已被员工受理，可能已有回复或内部备注。
     */
    PROCESSING("PROCESSING", "处理中"),

    /**
     * 已关闭：只读，禁止补充、受理、回复和内部备注。
     */
    CLOSED("CLOSED", "已关闭");

    /**
     * 对外稳定编码，同时也是数据库存储值。
     */
    private final String code;

    /**
     * 面向学生的中文名称。
     */
    private final String label;

    /**
     * 创建状态。
     *
     * @param code  对外稳定编码
     * @param label 中文名称
     */
    SupportStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 获取稳定编码。
     *
     * @return 状态编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取中文名称。
     *
     * @return 状态名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 判断当前状态是否允许继续对话。
     *
     * @return 未关闭时返回 true
     */
    public boolean isOpen() {
        return this != CLOSED;
    }

    /**
     * 按编码查找状态。
     *
     * @param code 数据库中的状态编码
     * @return 匹配的状态；未知或空值返回 null
     */
    public static SupportStatus findByCode(String code) {
        if (code == null) {
            return null;
        }
        for (SupportStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 按编码获取中文名称。
     *
     * @param code 数据库中的状态编码
     * @return 中文名称；无法识别时原样返回编码，避免列表空白
     */
    public static String labelOf(String code) {
        SupportStatus status = findByCode(code);
        return status == null ? code : status.label;
    }
}
