package com.hbue.ordering.common.core.role;

/**
 * 系统角色编码。
 *
 * <p>与 {@code users.role_code} 列取值一一对应。角色是技术身份，
 * 由 {@code SYSTEM_ADMIN} 分配；数据范围是业务权限，
 * 由餐厅经理与总经理分配，两者分属不同接口。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public enum RoleCode {

    /** 顾客：只能操作本人数据。 */
    CUSTOMER("CUSTOMER", "顾客"),

    /** 档口负责人：管理所辖档口。 */
    STALL_MANAGER("STALL_MANAGER", "档口负责人"),

    /** 餐厅经理：管理所属餐厅，并授予本餐厅内的档口范围。 */
    RESTAURANT_MANAGER("RESTAURANT_MANAGER", "餐厅经理"),

    /** 总经理：全平台经营数据，并授予任意餐厅范围。 */
    GENERAL_MANAGER("GENERAL_MANAGER", "总经理"),

    /** 系统管理员：技术角色，管账号与角色，不碰业务数据。 */
    SYSTEM_ADMIN("SYSTEM_ADMIN", "系统管理员");

    /** 数据库中保存的角色编码。 */
    private final String code;

    /** 角色中文名称。 */
    private final String label;

    /**
     * 创建角色编码。
     *
     * @param code 数据库中保存的角色编码
     * @param label 角色中文名称
     */
    RoleCode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 获取数据库角色编码。
     *
     * @return 角色编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取角色中文名称。
     *
     * @return 角色中文名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 根据数据库编码查找角色。
     *
     * @param code 数据库角色编码
     * @return 匹配的角色；未知或空编码返回 null
     */
    public static RoleCode findByCode(String code) {
        if (code == null) {
            return null;
        }
        for (RoleCode roleCode : values()) {
            if (roleCode.code.equals(code)) {
                return roleCode;
            }
        }
        return null;
    }
}
