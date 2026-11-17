package com.hbue.ordering.product.service;

import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;

/**
 * 商品核价服务契约。
 *
 * <p>仅供后端服务在订单创建前核验商品事实，不能把客户端提交价格作为可信输入；
 * 实现应以商品服务数据库中的窗口、售卖、分类和启用规格为准。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface ProductQuoteService {

    /**
     * 查询启用窗口内可售菜品的当前价格。
     *
     * @param request 窗口及菜品选项行，兼容旧无规格菜品ID列表
     * @return 窗口状态与按商品ID和选择键区分的权威价格；规格无效整体拒绝
     * @throws com.hbue.ordering.common.core.exception.BusinessException 请求参数无效时抛出
     */
    ProductQuoteResponse quote(ProductQuoteRequest request);
}
