package com.hbue.ordering.support.service;

import java.util.List;

/**
 * 员工数据范围快照。
 *
 * <p>{@code platform = true} 表示全平台；否则只允许访问授权餐厅集合内的
 * 用餐工单。软件工单没有餐厅归属，因此非全平台范围的管理员无法看到任何
 * 软件工单。该对象不缓存，每次操作前重新读取，保证范围调整立即生效。</p>
 *
 * @param platform      是否全平台范围
 * @param restaurantIds 授权餐厅编号集合
 * @author order-system
 * @date 2026-11-23
 */
public record SupportScope(boolean platform, List<Long> restaurantIds) {
}
