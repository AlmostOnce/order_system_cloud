package com.hbue.ordering.comment.model;

/**
 * 评论所依附的评价类型。
 *
 * <p>两类评价的正文与存储位置不同：菜品评价元数据在 MySQL、正文在 MongoDB；
 * 店铺评价全部在 MySQL。评论功能不关心正文存储，只用该枚举定位"评论挂在哪类评价上"，
 * 以及该用哪种消费事实做准入判断。</p>
 *
 * <p>编码同时写入 {@code review_discussion.review_type}，发布后不可修改；
 * 新增类型时必须同步补充准入维度和通知的业务类型映射。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
public enum ReviewType {

    /**
     * 菜品评价：准入口径为"买过该菜品"。
     */
    COMMENT("COMMENT", "PRODUCT"),

    /**
     * 店铺评价：准入口径为"在该档口消费过"。
     */
    WINDOW_REVIEW("WINDOW_REVIEW", "WINDOW");

    /**
     * 数据库存储编码，发布后不可修改。
     */
    private final String code;

    /**
     * 消费事实查询的目标类型，传给订单服务内部接口。
     */
    private final String purchaseTargetType;

    /**
     * 创建评价类型。
     *
     * @param code                数据库存储编码
     * @param purchaseTargetType  消费事实查询的目标类型
     */
    ReviewType(String code, String purchaseTargetType) {
        this.code = code;
        this.purchaseTargetType = purchaseTargetType;
    }

    /**
     * 返回数据库存储编码。
     *
     * @return 类型编码
     */
    public String code() {
        return code;
    }

    /**
     * 返回消费事实查询使用的目标类型。
     *
     * @return PRODUCT 或 WINDOW
     */
    public String purchaseTargetType() {
        return purchaseTargetType;
    }

    /**
     * 按编码查找评价类型。
     *
     * <p>未知编码返回 null，由调用方决定拒绝请求；不做兜底映射，
     * 避免把错误的类型静默当成菜品评价而放行准入校验。</p>
     *
     * @param code 类型编码
     * @return 对应类型，未知编码返回 null
     */
    public static ReviewType fromCode(String code) {
        for (ReviewType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
