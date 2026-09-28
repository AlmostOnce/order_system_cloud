package com.hbue.ordering.product.service;

import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;

/**
 * 商品核价服务。
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface ProductQuoteService {

    /**
     * 查询启用窗口内可售菜品的当前价格。
     *
     * @param request 窗口 ID 和菜品 ID 列表
     * @return 窗口状态和可核价的菜品
     */
    ProductQuoteResponse quote(ProductQuoteRequest request);
}
