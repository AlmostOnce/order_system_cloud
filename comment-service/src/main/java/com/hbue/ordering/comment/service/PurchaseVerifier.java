package com.hbue.ordering.comment.service;
import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.order.api.vo.OrderReviewSnapshotVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import feign.FeignException;
import org.springframework.stereotype.Component;
@Component
public class PurchaseVerifier {
    private final CommentOrderClient orders;
    public PurchaseVerifier(CommentOrderClient orders) { this.orders = orders; }
    public Long requireCompletedPurchase(Long orderId, Long productId, String bearer) {
        OrderReviewSnapshotVO order;
        try {
            var response = orders.getOrder(orderId, bearer);
            if (response == null || !"COMMON_000".equals(response.getCode()) || response.getData() == null)
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认订单，请稍后重试");
            order = response.getData();
        } catch (FeignException exception) {
            var code = switch (exception.status()) {
                case 401 -> CommonErrorCode.UNAUTHORIZED;
                case 403, 404 -> CommonErrorCode.NOT_FOUND;
                default -> CommonErrorCode.SERVICE_UNAVAILABLE;
            };
            throw new BusinessException(code, "无法确认本人订单");
        }
        if (!orderId.equals(order.orderId()) || order.windowId() == null || order.windowId() <= 0
                || order.items() == null) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "订单信息不完整");
        if (!"已完成".equals(order.status())) throw new BusinessException(CommonErrorCode.CONFLICT, "订单完成后才能评价");
        if (order.items().stream().noneMatch(i -> i != null && productId.equals(i.productId())
                && i.quantity() != null && i.quantity() > 0))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "该订单未购买此菜品");
        return order.windowId();
    }
}
