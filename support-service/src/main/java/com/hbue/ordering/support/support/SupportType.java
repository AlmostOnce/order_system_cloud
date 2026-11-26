package com.hbue.ordering.support.support;

/**
 * 反馈工单类型。
 *
 * <p>类型决定表单字段、处理角色与数据范围：用餐问题必须关联本人订单，
 * 由餐厅经理和总经理处理；软件问题不关联业务对象，由总经理和系统管理员处理。
 * 客户端只能提交枚举编码，服务端据此校验分类白名单。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
public enum SupportType {

    /**
     * 用餐问题：卫生、出餐、态度、等待、分量价格等，需要食堂侧处理。
     */
    DINING("DINING", "用餐问题"),

    /**
     * 软件问题：闪退、支付、下单、显示、账号等，需要技术侧处理。
     */
    SOFTWARE("SOFTWARE", "软件问题");

    /**
     * 对外稳定编码，同时也是数据库存储值。
     */
    private final String code;

    /**
     * 面向学生的中文名称。
     */
    private final String label;

    /**
     * 创建工单类型。
     *
     * @param code  对外稳定编码
     * @param label 中文名称
     */
    SupportType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 获取稳定编码。
     *
     * @return 类型编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取中文名称。
     *
     * @return 类型名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 按编码查找类型。
     *
     * @param code 客户端提交的类型编码
     * @return 匹配的类型；未知或空值返回 null，由调用方转换为参数错误
     */
    public static SupportType findByCode(String code) {
        if (code == null) {
            return null;
        }
        for (SupportType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
