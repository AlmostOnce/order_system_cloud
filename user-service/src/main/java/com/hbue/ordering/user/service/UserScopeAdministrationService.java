package com.hbue.ordering.user.service;

import com.hbue.ordering.user.dto.request.UserScopeSaveRequest;
import com.hbue.ordering.user.vo.UserScopeAdminVO;

import java.util.List;

/**
 * 用户数据范围授予业务服务。
 *
 * <p>数据范围的授予权归业务角色，不归技术角色：调用者角色为
 * {@code RESTAURANT_MANAGER}（限本餐厅）或 {@code GENERAL_MANAGER}（全平台），
 * {@code SYSTEM_ADMIN} 不具备该能力。授予者只能授予自己已具备的范围，
 * 且不得对本人授予或撤销范围。</p>
 *
 * <p>本服务只负责 {@code user_data_scopes} 的读写，不修改 {@code users.role_code}。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public interface UserScopeAdministrationService {

    /**
     * 查询指定用户持有的全部数据范围。
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @return 目标用户的数据范围列表
     */
    List<UserScopeAdminVO> listScopes(Long operatorId, Long targetUserId);

    /**
     * 覆盖式设置指定用户的数据范围。
     *
     * <p>覆盖式语义：请求即该用户最终持有的全部范围，为空表示清空。
     * 授予者不得越出自身范围、不得操作本人，且每条范围的引用对象必须真实存在。</p>
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @param request 新的范围列表
     * @return 写入后的数据范围列表
     */
    List<UserScopeAdminVO> replaceScopes(
            Long operatorId,
            Long targetUserId,
            UserScopeSaveRequest request
    );

    /**
     * 撤销指定用户的一条数据范围。
     *
     * @param operatorId 操作者用户 ID
     * @param targetUserId 目标用户 ID
     * @param scopeId 范围记录 ID
     */
    void revokeScope(Long operatorId, Long targetUserId, Long scopeId);
}
