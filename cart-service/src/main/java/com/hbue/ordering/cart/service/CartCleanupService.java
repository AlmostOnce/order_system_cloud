package com.hbue.ordering.cart.service;

import com.hbue.ordering.cart.dto.request.CartCleanupRequest;
import com.hbue.ordering.cart.vo.CartCleanupVO;

/** 购物车下单后清理契约；与普通增删改查分离，避免购物车主服务继续膨胀。 */
public interface CartCleanupService {
    /**
     * 核对真实订单后，按提交版本清理当前用户的购物车，重复请求不会重复下单。
     * @param userId 已验签 JWT 提取的用户 ID
     * @param orderId 已创建成功的订单 ID
     * @param request 原始提交快照，重试必须保持不变
     * @return 实际删除与安全跳过的数量
     */
    CartCleanupVO cleanup(Long userId, Long orderId, CartCleanupRequest request);
}
