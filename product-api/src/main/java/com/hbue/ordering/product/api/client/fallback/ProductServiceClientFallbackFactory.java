package com.hbue.ordering.product.api.client.fallback;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商品服务不可用时返回统一的降级结果。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Component
public class ProductServiceClientFallbackFactory
        implements FallbackFactory<ProductServiceClient> {

    /** 降级日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(
            ProductServiceClientFallbackFactory.class
    );

    /**
     * 创建商品服务降级客户端。
     *
     * @param cause 远程调用失败原因
     * @return 返回服务不可用响应的客户端
     */
    @Override
    public ProductServiceClient create(Throwable cause) {
        log.error("调用 product-service 核价接口失败", cause);
        return request -> ApiResponse.failure(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "商品服务暂时不可用"
        );
    }
}
