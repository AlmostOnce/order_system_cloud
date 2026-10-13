package com.hbue.ordering.common.core.scope;

/**
 * 数据范围判定服务。
 *
 * <p>角色门禁（{@code @PreAuthorize}）解决"这类操作你能不能做"，
 * 本服务解决"这条数据你能不能碰"，两者必须同时通过。</p>
 *
 * <p>范围事实来源于 user-service，各业务服务在 Service 层调用本服务完成细粒度判定。
 * 授权判定只看服务端事实，不采信客户端传入的任何角色或范围参数。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public interface DataScopeService {

    /**
     * 校验用户是否有权操作指定档口的数据。
     *
     * <p>主管档口与全平台范围视为有权。失败时抛出 403，适用于档口、菜单等
     * 公开资源（无需隐藏存在性）。若目标属私有数据（订单、支付单），
     * 调用方应改用 {@link #loadScopes(Long)} 自行判定并返回 404，
     * 避免通过错误码探测数据是否存在。</p>
     *
     * @param userId 当前登录用户 ID
     * @param windowId 目标档口 ID
     */
    void checkWindowAccess(Long userId, Long windowId);

    /**
     * 校验用户是否有权操作指定餐厅的数据。
     *
     * <p>全平台范围视为有权。失败时抛出 403。</p>
     *
     * @param userId 当前登录用户 ID
     * @param restaurantId 目标餐厅 ID
     */
    void checkRestaurantAccess(Long userId, Long restaurantId);

    /**
     * 判断用户是否有权操作指定档口的数据，不抛出异常。
     *
     * <p>与 {@link #checkWindowAccess(Long, Long)} 判定口径一致，
     * 供私有数据场景（订单、支付单）自行决定返回 404 使用。</p>
     *
     * @param userId 当前登录用户 ID
     * @param windowId 目标档口 ID
     * @return 有权时返回 true
     */
    boolean canAccessWindow(Long userId, Long windowId);

    /**
     * 判断用户是否有权操作指定餐厅的数据，不抛出异常。
     *
     * @param userId 当前登录用户 ID
     * @param restaurantId 目标餐厅 ID
     * @return 有权时返回 true
     */
    boolean canAccessRestaurant(Long userId, Long restaurantId);

    /**
     * 查询用户的数据范围快照。
     *
     * @param userId 用户 ID
     * @return 范围快照；用户无任何范围时返回空快照
     */
    UserScopeSnapshot loadScopes(Long userId);
}
