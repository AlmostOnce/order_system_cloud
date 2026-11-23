package com.hbue.ordering.support.client.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 用户服务返回的数据范围最小投影。
 *
 * <p>{@code platform = true} 表示全平台；否则以餐厅与窗口集合为授权事实。
 * 服务调用失败时必须拒绝受保护操作，不能把空集合当成默认放行。</p>
 *
 * @param platform      是否全平台范围
 * @param windowIds     授权窗口集合，可能为空
 * @param restaurantIds 授权餐厅集合，可能为空
 * @author order-system
 * @date 2026-11-23
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupportUserScopeVO(
        boolean platform,
        List<Long> windowIds,
        List<Long> restaurantIds
) {
}
