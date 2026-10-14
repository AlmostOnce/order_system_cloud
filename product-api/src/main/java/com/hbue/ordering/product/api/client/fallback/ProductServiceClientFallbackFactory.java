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
 * 商品核价远程调用失败时创建统一降级客户端。
 *
 * <p>该工厂保留原始失败原因供日志排查，并向调用方返回服务不可用响应；
 * 降级结果不伪造价格或可售状态，调用方必须停止依赖该次核价继续创建订单。</p>
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
     * 根据本次远程调用的失败原因创建降级客户端。
     *
     * @param cause 远程调用失败原因
     * @return 调用核价方法时始终返回服务不可用响应的客户端
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
