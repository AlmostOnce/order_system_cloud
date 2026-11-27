package com.hbue.ordering.product.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.fallback.ProductServiceClientFallbackFactory;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 订单等后端服务调用商品服务的核价契约。
 *
 * <p>调用方提交窗口及逐行菜品、选项ID（兼容旧菜品ID列表），商品服务以自身数据库中的在售状态与当前价格为准，
 * 返回可参与订单金额计算的价格快照来源。本客户端只负责服务间 HTTP 契约，
 * 不承载订单价格计算，也不应由浏览器直接调用；远程调用失败由降级工厂转换为
 * 统一的服务不可用响应。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@FeignClient(
        name = "product-service",
        fallbackFactory = ProductServiceClientFallbackFactory.class
)
public interface ProductServiceClient {

    /**
     * 批量核验窗口和菜品状态并取得商品服务当前价格。
     *
     * <p>返回集合只包含属于指定窗口、处于可售状态且分类有效的菜品；
     * 未出现在结果中的请求菜品由调用方视为不可下单；响应按菜品ID与选择键区分，
     * 规格无效会整体返回400，不得用客户端金额兜底。请求数量和ID合法性
     * 由共享请求对象及商品服务共同校验。</p>
     *
     * @param request 窗口及待核价的菜品选项行
     * @return 商品核价响应
     */
    @PostMapping("/internal/products/quote")
    ApiResponse<ProductQuoteResponse> quote(
            @RequestBody ProductQuoteRequest request
    );
}
