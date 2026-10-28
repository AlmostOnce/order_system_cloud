package com.hbue.ordering.comment.service;



/** PurchaseVerifier 业务能力边界。 */
public interface PurchaseVerifier {
    Long requireCompletedPurchase(Long orderId, Long productId, String bearer);
}
