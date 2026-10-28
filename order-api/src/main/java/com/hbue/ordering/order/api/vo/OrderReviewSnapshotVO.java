package com.hbue.ordering.order.api.vo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
/** 复用本人订单详情响应；归属由订单服务认证与校验。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderReviewSnapshotVO(Long orderId, Long windowId, String status,
        List<OrderPurchasedItemVO> items) {}
