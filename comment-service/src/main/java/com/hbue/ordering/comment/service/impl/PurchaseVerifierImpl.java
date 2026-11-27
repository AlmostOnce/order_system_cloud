package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.comment.service.PurchaseVerifier;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.api.vo.OrderReviewSnapshotVO;
import feign.FeignException;
import org.springframework.stereotype.Component;

/**
 * 评价资格的消费凭证校验实现。
 *
 * <p>评论服务不保存订单数据，必须向订单服务确认三件事：订单属于当前用户、订单已完成、
 * 订单明细确实包含待评价菜品。三者全部通过才返回订单的店铺编号，供上层判定评价归属。</p>
 *
 * <p>失败映射：下游 401 视为未登录、403/404 统一视为"没有这笔订单"（避免通过状态码枚举他人订单），
 * 其余异常返回 503；业务码异常一律不写入评价。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
@Component
public class PurchaseVerifierImpl implements PurchaseVerifier {

    /**
     * 订单服务内部客户端。
     */
    private final CommentOrderClient orders;

    /**
     * 创建校验器。
     *
     * @param orders 订单服务内部客户端
     */
    public PurchaseVerifierImpl(CommentOrderClient orders) {
        this.orders = orders;
    }

    public Long requireCompletedPurchase(Long orderId, Long productId, String bearer) {
        OrderReviewSnapshotVO order;
        try {
            var response = orders.getOrder(orderId, bearer);
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode()) || response.getData() == null)
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
                || order.items() == null)
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "订单信息不完整");
        if (!"已完成".equals(order.status()))
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单完成后才能评价");
        if (order.items().stream().noneMatch(i -> i != null && productId.equals(i.productId())
                && i.quantity() != null && i.quantity() > 0))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "该订单未购买此菜品");
        return order.windowId();
    }
}
