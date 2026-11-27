package com.hbue.ordering.user.vo;

import java.time.LocalDateTime;

/**
 * 管理端账号检索结果条目。
 *
 * <p>用于系统管理员的账号目录与业务角色的数据范围候选列表。为降低敏感信息暴露面，
 * 不返回内部数据库主键 {@code user_id}，对外定位统一使用 {@code userUuid}；
 * 邮箱只返回脱敏值，调用方无法据此还原完整地址。具体能看到哪些账号由调用接口的
 * 角色与数据范围决定，前端不得自行扩大范围。</p>
 *
 * @param userUuid    对外稳定随机标识，后续改角色、改状态、授予范围都使用它
 * @param userNo      永久用户号，可用于人工核对账号
 * @param username    用户名
 * @param roleCode    当前角色编码，取值见 {@code RoleCode}
 * @param status      账号状态：1 启用，0 禁用
 * @param email       脱敏后的邮箱；账号无邮箱或已注销时为 null
 * @param emailVerified 邮箱是否已验证；未验证账号无法登录管理端
 * @param createdAt   注册时间，用于人工判断账号新旧
 * @author order-system
 * @date 2026-11-28
 */
public record AdminUserSummaryVO(
        String userUuid,
        String userNo,
        String username,
        String roleCode,
        Integer status,
        String email,
        boolean emailVerified,
        LocalDateTime createdAt
) {
}
