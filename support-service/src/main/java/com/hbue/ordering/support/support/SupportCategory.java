package com.hbue.ordering.support.support;

/**
 * 反馈分类白名单。
 *
 * <p>每个分类固定归属一种工单类型，服务端同时校验类型与分类，
 * 防止客户端提交「用餐问题 + 闪退」这类不一致组合。中文文案由后端 VO
 * 一并返回，客户端版本不一致时仍能展示。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
public enum SupportCategory {

    /**
     * 用餐问题：卫生或异物。
     */
    HYGIENE(SupportType.DINING, "HYGIENE", "卫生/异物"),

    /**
     * 用餐问题：出餐错误。
     */
    WRONG_ITEM(SupportType.DINING, "WRONG_ITEM", "出餐错误"),

    /**
     * 用餐问题：服务态度。
     */
    ATTITUDE(SupportType.DINING, "ATTITUDE", "服务态度"),

    /**
     * 用餐问题：等待过久。
     */
    WAIT_TIME(SupportType.DINING, "WAIT_TIME", "等待过久"),

    /**
     * 用餐问题：分量或价格异常。
     */
    PORTION_PRICE(SupportType.DINING, "PORTION_PRICE", "分量/价格"),

    /**
     * 软件问题：闪退或卡顿。
     */
    CRASH(SupportType.SOFTWARE, "CRASH", "闪退/卡顿"),

    /**
     * 软件问题：支付异常。
     */
    PAYMENT(SupportType.SOFTWARE, "PAYMENT", "支付异常"),

    /**
     * 软件问题：下单或订单异常。
     */
    ORDER(SupportType.SOFTWARE, "ORDER", "下单/订单异常"),

    /**
     * 软件问题：页面显示异常。
     */
    DISPLAY(SupportType.SOFTWARE, "DISPLAY", "页面显示"),

    /**
     * 软件问题：登录或账号异常。
     */
    ACCOUNT(SupportType.SOFTWARE, "ACCOUNT", "登录/账号"),

    /**
     * 两类工单共用的「其他」分类；校验时必须结合类型判断归属。
     */
    OTHER(SupportType.DINING, "OTHER", "其他"),

    /**
     * 软件问题专用的「其他」分类，避免用同一枚举值混淆类型归属。
     */
    SOFTWARE_OTHER(SupportType.SOFTWARE, "OTHER", "其他");

    /**
     * 分类所属工单类型。
     */
    private final SupportType type;

    /**
     * 对外稳定编码，同时也是数据库存储值。
     */
    private final String code;

    /**
     * 面向学生的中文名称。
     */
    private final String label;

    /**
     * 创建分类。
     *
     * @param type  所属工单类型
     * @param code  对外稳定编码
     * @param label 中文名称
     */
    SupportCategory(SupportType type, String code, String label) {
        this.type = type;
        this.code = code;
        this.label = label;
    }

    /**
     * 获取所属工单类型。
     *
     * @return 工单类型
     */
    public SupportType getType() {
        return type;
    }

    /**
     * 获取稳定编码。
     *
     * @return 分类编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取中文名称。
     *
     * @return 分类名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 按类型与编码查找分类。
     *
     * @param type 已解析的工单类型
     * @param code 客户端提交的分类编码
     * @return 匹配的分类；类型为空、编码未知或类型不匹配时返回 null
     */
    public static SupportCategory findByTypeAndCode(SupportType type, String code) {
        if (type == null || code == null) {
            return null;
        }
        for (SupportCategory category : values()) {
            if (category.type == type && category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 按类型与编码查找展示文案。
     *
     * @param typeCode    数据库中的类型编码
     * @param categoryCode 数据库中的分类编码
     * @return 中文分类名称；历史数据无法识别时回退为「其他」，避免展示空白
     */
    public static String labelOf(String typeCode, String categoryCode) {
        SupportCategory category = findByTypeAndCode(SupportType.findByCode(typeCode), categoryCode);
        return category == null ? "其他" : category.label;
    }
}
