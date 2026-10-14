package com.hbue.ordering.user.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户数据范围视图对象。
 *
 * <p>用于 user-service 向其他业务服务提供数据范围事实，是内部接口的传输契约，
 * 不是授权决策本身。{@code platform} 表示平台级全范围；档口和餐厅 ID 表示明确授权集合。
 * 数据范围不写入 JWT，各业务服务按需查询并缓存，同时必须在范围查询失败时拒绝受保护操作。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserScopeVO {

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 是否拥有全平台范围。
     */
    private boolean platform;

    /**
     * 持有的档口范围。
     */
    private List<Long> windowIds;

    /**
     * 持有的餐厅范围。
     */
    private List<Long> restaurantIds;
}
