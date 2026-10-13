package com.hbue.ordering.common.core.scope;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户数据范围快照。
 *
 * <p>由 user-service 作为唯一事实来源提供，各业务服务在 Service 层用它完成
 * "这条数据你能不能碰"的细粒度判定。快照只读，不提供任何写入口。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public final class UserScopeSnapshot {

    /** 用户 ID。 */
    private final Long userId;

    /** 是否拥有全平台范围。 */
    private final boolean platformScope;

    /** 持有的档口范围。 */
    private final Set<Long> windowIds;

    /** 持有的餐厅范围。 */
    private final Set<Long> restaurantIds;

    /**
     * 创建用户数据范围快照。
     *
     * @param userId 用户 ID
     * @param platformScope 是否拥有全平台范围
     * @param windowIds 档口范围
     * @param restaurantIds 餐厅范围
     */
    public UserScopeSnapshot(
            Long userId,
            boolean platformScope,
            Set<Long> windowIds,
            Set<Long> restaurantIds
    ) {
        this.userId = userId;
        this.platformScope = platformScope;
        this.windowIds = windowIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(windowIds));
        this.restaurantIds = restaurantIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(restaurantIds));
    }

    /**
     * 创建空范围快照，表示该用户不具备任何业务数据范围。
     *
     * @param userId 用户 ID
     * @return 空范围快照
     */
    public static UserScopeSnapshot empty(Long userId) {
        return new UserScopeSnapshot(
                userId,
                false,
                Collections.emptySet(),
                Collections.emptySet()
        );
    }

    /**
     * 判断是否覆盖指定档口。
     *
     * <p>主管档口、全平台范围视为有权；餐厅范围是否覆盖档口需由调用方
     * 先将档口换算为餐厅，因此这里只判定直接档口与全平台。</p>
     *
     * @param windowId 目标档口 ID
     * @return 有权时返回 true
     */
    public boolean coversWindow(Long windowId) {
        return platformScope
                || (windowId != null && windowIds.contains(windowId));
    }

    /**
     * 判断是否覆盖指定餐厅。
     *
     * @param restaurantId 目标餐厅 ID
     * @return 有权时返回 true
     */
    public boolean coversRestaurant(Long restaurantId) {
        return platformScope
                || (restaurantId != null && restaurantIds.contains(restaurantId));
    }

    /**
     * 获取用户 ID。
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 是否拥有全平台范围。
     *
     * @return 拥有全平台范围时返回 true
     */
    public boolean isPlatformScope() {
        return platformScope;
    }

    /**
     * 获取档口范围。
     *
     * @return 只读档口范围集合
     */
    public Set<Long> getWindowIds() {
        return windowIds;
    }

    /**
     * 获取餐厅范围。
     *
     * @return 只读餐厅范围集合
     */
    public Set<Long> getRestaurantIds() {
        return restaurantIds;
    }
}
