package com.hbue.ordering.support.client.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 商品服务返回的窗口最小投影。
 *
 * <p>用于把工单窗口换算为餐厅编号，并保存窗口名称快照。窗口名称允许为空，
 * 历史窗口数据未填写名称时仍可提交工单，只影响展示。</p>
 *
 * @param windowId     窗口编号
 * @param windowName   窗口名称
 * @param restaurantId 所属餐厅编号
 * @param status       窗口状态，1 启用、0 停用
 * @author order-system
 * @date 2026-11-23
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupportWindowInfoVO(
        Long windowId,
        String windowName,
        Long restaurantId,
        Integer status
) {
}
