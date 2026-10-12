package com.hbue.ordering.payment.enums;

/**
 * 支付单状态及其合法流转规则。
 *
 * <p>支付单状态与订单状态（中文 label）不同，是新引入的独立状态集，
 * 数据库直接存储英文码，无存量数据兼容负担。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
public enum PaymentStatus {

    /** 已创建，等待渠道回调。 */
    CREATED("CREATED"),

    /** 渠道通知支付成功。 */
    SUCCESS("SUCCESS"),

    /** 渠道通知支付失败，用户可重新发起支付。 */
    FAILED("FAILED"),

    /** 已关闭（预留给超时关单场景，当前版本不产生）。 */
    CLOSED("CLOSED");

    /** 数据库存储的状态码。 */
    private final String code;

    /**
     * 创建支付单状态。
     *
     * @param code 数据库存储的状态码
     */
    PaymentStatus(String code) {
        this.code = code;
    }

    /**
     * 获取数据库存储的状态码。
     *
     * @return 状态码
     */
    public String getCode() {
        return code;
    }

    /**
     * 判断当前支付单状态能否流转到目标状态。
     *
     * @param targetStatus 目标状态
     * @return 状态迁移合法时返回 true
     */
    public boolean canTransitionTo(PaymentStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }

        return switch (this) {
            case CREATED -> targetStatus == SUCCESS || targetStatus == FAILED;
            case SUCCESS, FAILED, CLOSED -> false;
        };
    }

    /**
     * 根据数据库中的状态码查找枚举。
     *
     * @param code 数据库状态码
     * @return 匹配的支付单状态；未知或空状态码返回 null
     */
    public static PaymentStatus findByCode(String code) {
        if (code == null) {
            return null;
        }

        for (PaymentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
