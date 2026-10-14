package com.hbue.ordering.product.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 档口内部查询的最小视图对象。
 *
 * <p>供其他服务在数据范围校验时把档口换算为所属餐厅，并判断档口启停状态；
 * 只暴露服务间校验所需字段，不携带商品持久化对象，属于内部契约。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WindowInfoVO {

    /**
     * 档口 ID。
     */
    private Long windowId;

    /**
     * 所属餐厅 ID。
     */
    private Long restaurantId;

    /**
     * 档口状态：1 启用，0 停用。
     */
    private Integer status;
}
