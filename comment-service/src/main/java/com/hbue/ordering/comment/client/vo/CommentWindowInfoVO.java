package com.hbue.ordering.comment.client.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 商品服务返回的档口最小投影。
 *
 * <p>评论服务只需要把评价所属档口换算为餐厅，用于判断"餐厅经理是否覆盖该档口"，
 * 因此只保留档口编号与所属餐厅编号；档口名称、营业状态等字段与本场景无关，不做声明。</p>
 *
 * @param windowId     档口编号
 * @param restaurantId 所属餐厅编号
 * @author order-system
 * @date 2026-11-25
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentWindowInfoVO(
        Long windowId,
        Long restaurantId
) {
}
