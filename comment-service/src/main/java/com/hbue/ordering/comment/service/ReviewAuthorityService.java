package com.hbue.ordering.comment.service;

import java.util.List;

/**
 * 评价修改与删除的操作者鉴权。
 *
 * <p>角色门禁（{@code @PreAuthorize}）只回答"这一类接口你能不能进"，本服务回答
 * "这一条评价你能不能改"。两者必须同时通过，且判定只使用服务端事实：
 * 评价归属、评价所属档口、用户在用户服务中的实时数据范围，不采信任何客户端参数。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li>评价作者本人始终可以修改、删除自己的评价；</li>
 *   <li>系统管理员与总经理可以操作任意评价，不受档口范围限制；</li>
 *   <li>餐厅经理、档口负责人只有在数据范围覆盖评价所属档口时才可以操作；</li>
 *   <li>其余情况（普通学生操作他人评价、受限管理角色越范围操作）一律拒绝。</li>
 * </ul>
 *
 * @author order-system
 * @date 2026-11-25
 */
public interface ReviewAuthorityService {

    /**
     * 校验操作者是否可以修改或删除指定评价。
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合，来自服务端解析后的 JWT 声明
     * @param ownerId    评价作者编号
     * @param windowId   评价所属档口编号
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         无权限时抛出 403；无法确认数据范围时抛出 503
     */
    void requireManageable(Long operatorId, List<String> roles, Long ownerId, Long windowId);

    /**
     * 判断操作者的数据范围是否覆盖指定档口，不做角色判断也不抛异常。
     *
     * <p>供需要"先判断再决定返回码"的场景使用，判定口径与
     * {@link #requireManageable(Long, List, Long, Long)} 中的范围部分保持一致。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param windowId   目标档口编号
     * @return 覆盖时返回 true
     */
    boolean coversWindow(Long operatorId, Long windowId);
}
