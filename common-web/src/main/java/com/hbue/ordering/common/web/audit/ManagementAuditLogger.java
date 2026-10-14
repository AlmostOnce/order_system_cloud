package com.hbue.ordering.common.web.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 管理操作审计日志记录器。
 *
 * <p>Phase 1 的审计先落结构化日志（后续阶段再落审计表），固定字段：
 * {@code actorUserId}、{@code roleCode}、{@code action}、{@code targetType}、
 * {@code targetId}、{@code before}、{@code after}、{@code result}。</p>
 *
 * <p>日志只允许追加：本类不提供任何修改或删除历史记录的入口。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Component
public class ManagementAuditLogger {

    /** 审计专用日志通道，便于日志采集按通道过滤。 */
    private static final Logger AUDIT_LOG =
            LoggerFactory.getLogger("AUDIT");

    /** Spring Security 角色权限前缀。 */
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * 记录一次管理操作。
     *
     * @param actorUserId 操作者用户 ID
     * @param action 操作动作，例如 ORDER_STATUS_ADVANCE
     * @param targetType 目标类型，例如 ORDER
     * @param targetId 目标 ID
     * @param before 变更前状态
     * @param after 变更后状态
     * @param success 操作是否成功
     */
    public void record(
            Long actorUserId,
            String action,
            String targetType,
            Object targetId,
            Object before,
            Object after,
            boolean success
    ) {
        AUDIT_LOG.info(
                "audit actorUserId={} roleCode={} action={} targetType={} "
                        + "targetId={} before={} after={} result={}",
                actorUserId,
                currentRoleCode(),
                action,
                targetType,
                targetId,
                before,
                after,
                success ? "SUCCESS" : "FAILURE"
        );
    }

    /**
     * 从当前认证信息的权限集合中取出首个带 {@code ROLE_} 前缀的角色编码。
     *
     * <p>不根据请求参数推断操作者角色；当前认证上下文缺失或没有角色权限时返回 unknown。</p>
     *
     * @return 角色编码；无法确定时返回 unknown
     */
    private String currentRoleCode() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return "unknown";
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith(ROLE_PREFIX)) {
                return name.substring(ROLE_PREFIX.length());
            }
        }
        return "unknown";
    }
}
