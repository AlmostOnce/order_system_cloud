package com.hbue.ordering.support.support;

/**
 * 管理端角色编码常量。
 *
 * <p>角色编码同时出现在 Spring Security 的 {@code @PreAuthorize} 表达式、会话快照
 * {@code SupportActor} 和工单可见性判定中。集中到本类后，角色改名只需改一处，且编译期即可
 * 发现拼写错误，避免"注解里放行、服务层判空"的静默失配。</p>
 *
 * <p>编码与网关注入的 JWT 角色声明保持一致，不带 {@code ROLE_} 前缀。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
public final class SupportRole {

    /**
     * 总经理：持有全平台数据范围时可见两类工单，否则仅见授权餐厅的用餐工单。
     */
    public static final String GENERAL_MANAGER = "GENERAL_MANAGER";

    /**
     * 餐厅经理：只能看到并处理授权餐厅的用餐工单。
     */
    public static final String RESTAURANT_MANAGER = "RESTAURANT_MANAGER";

    /**
     * 系统管理员：只处理软件工单，不接触任何用餐业务数据。
     */
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    /**
     * 管理端工单接口的统一放行表达式。
     *
     * <p>由编译期常量拼接而成，可直接用于 {@code @PreAuthorize}；此处只解决"这类接口能不能进"，
     * 具体某条工单能不能看仍由 {@code SupportScopeService} 判定。</p>
     */
    public static final String MANAGEMENT_ACCESS =
            "hasAnyRole('" + RESTAURANT_MANAGER + "','" + GENERAL_MANAGER + "','" + SYSTEM_ADMIN + "')";

    /**
     * 工具类不允许实例化。
     */
    private SupportRole() {
    }
}
