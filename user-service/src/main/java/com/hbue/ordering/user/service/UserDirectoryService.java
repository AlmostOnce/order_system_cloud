package com.hbue.ordering.user.service;

import com.hbue.ordering.user.vo.AdminUserPageVO;

/**
 * 管理端账号检索业务服务。
 *
 * <p>提供两条只读检索能力，区别在于调用者能看到的账号集合：
 * 系统管理员可按角色、状态、关键词检索全平台账号；餐厅经理与总经理在授予
 * 数据范围前检索候选用户，其中总经理可检索全平台，餐厅经理只能检索到
 * “已持有本餐厅（或其下属档口）范围”的账号。任何检索都不返回密码、支付密码、
 * 完整邮箱等敏感字段，也不承担改角色、改状态、写范围的职责。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
public interface UserDirectoryService {

    /**
     * 系统管理员检索账号目录。
     *
     * @param operatorId 操作者用户 ID，来自已验签 JWT
     * @param keyword    关键词，匹配用户名、邮箱或完整用户号；为空表示不筛选
     * @param roleCode   角色编码筛选；为空表示不限角色
     * @param status     账号状态筛选：1 启用、0 禁用；为空表示不限状态
     * @param cursor     上一页返回的 nextCursor；首屏为 null
     * @param size       每页条数，服务端会限制在允许区间内
     * @return 游标分页的账号摘要
     * @throws com.hbue.ordering.common.core.exception.BusinessException 操作者无效、角色编码未知、状态或游标非法时抛出
     */
    AdminUserPageVO listDirectory(
            Long operatorId,
            String keyword,
            String roleCode,
            Integer status,
            String cursor,
            int size
    );

    /**
     * 业务角色检索可授予数据范围的候选账号。
     *
     * <p>总经理（持有全平台范围）可检索全平台账号；餐厅经理只能检索到已经持有
     * 本餐厅或其下属档口范围的账号，用于在既有协作关系内继续授予/调整范围。
     * 该收敛口径避免餐厅经理借此接口枚举全平台账号。</p>
     *
     * @param operatorId 操作者用户 ID，来自已验签 JWT
     * @param keyword    关键词，匹配用户名、邮箱或完整用户号；为空表示不筛选
     * @param cursor     上一页返回的 nextCursor；首屏为 null
     * @param size       每页条数，服务端会限制在允许区间内
     * @return 游标分页的候选账号摘要
     * @throws com.hbue.ordering.common.core.exception.BusinessException 操作者无效或游标非法时抛出
     */
    AdminUserPageVO listScopeCandidates(
            Long operatorId,
            String keyword,
            String cursor,
            int size
    );
}
