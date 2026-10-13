package com.hbue.ordering.common.core.scope;

/**
 * 数据范围类型。
 *
 * <p>与 {@code user_data_scopes.scope_type} 列取值一一对应，
 * 用于表达"用户能操作哪些数据"。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public enum DataScopeType {

    /** 档口范围，scope_ref 为 window_id。 */
    WINDOW("WINDOW"),

    /** 餐厅范围，scope_ref 为 restaurant_id。 */
    RESTAURANT("RESTAURANT"),

    /** 全平台范围，scope_ref 固定为 0。 */
    PLATFORM("PLATFORM");

    /** 数据库中保存的范围类型编码。 */
    private final String code;

    /**
     * 创建数据范围类型。
     *
     * @param code 数据库范围类型编码
     */
    DataScopeType(String code) {
        this.code = code;
    }

    /**
     * 获取数据库范围类型编码。
     *
     * @return 范围类型编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 根据数据库编码查找范围类型。
     *
     * @param code 数据库范围类型编码
     * @return 匹配的范围类型；未知或空编码返回 null
     */
    public static DataScopeType findByCode(String code) {
        if (code == null) {
            return null;
        }
        for (DataScopeType scopeType : values()) {
            if (scopeType.code.equals(code)) {
                return scopeType;
            }
        }
        return null;
    }
}
