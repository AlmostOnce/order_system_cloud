package com.hbue.ordering.product.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 档口内部查询视图对象。
 *
 * <p>供其他服务在数据范围校验时把档口换算为所属餐厅使用，
 * 属于服务间内部契约，不对外暴露。</p>
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
