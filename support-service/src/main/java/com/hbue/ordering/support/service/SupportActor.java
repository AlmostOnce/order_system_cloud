package com.hbue.ordering.support.service;

import java.util.Set;

/**
 * 管理端操作者身份快照。
 *
 * <p>由 Controller 从已验签的 JWT 构造，包含内部用户编号、角色编码与原始
 * Authorization 头。Service 使用角色判断可见工单类型、使用数据范围判断
 * 具体餐厅；两者都通过后才允许操作。</p>
 *
 * @param userId        操作者内部用户编号
 * @param roles         角色编码集合，不含 ROLE_ 前缀
 * @param authorization 原始 Bearer 凭据，学生端调用订单服务时使用
 * @author order-system
 * @date 2026-11-23
 */
public record SupportActor(
        Long userId,
        Set<String> roles,
        String authorization
) {

    /**
     * 判断操作者是否持有指定角色。
     *
     * @param role 角色编码
     * @return 持有该角色时返回 true
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
