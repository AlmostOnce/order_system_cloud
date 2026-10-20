package com.hbue.ordering.stock.api.client.fallback;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.stock.api.client.StockServiceClient;
import com.hbue.ordering.stock.api.dto.StockDeductRequest;
import com.hbue.ordering.stock.api.dto.StockRestoreRequest;
import com.hbue.ordering.stock.api.vo.StockVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 库存服务内部接口调用失败时创建统一降级客户端。
 *
 * <p>库存扣减是下单事务的关键分支：远程失败时必须显式失败，让全局事务回滚，
 * 绝不能被降级成「扣减成功」。因此本工厂一律返回服务不可用业务码，
 * 由调用方按失败关闭处理。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Component
public class StockServiceClientFallbackFactory
        implements FallbackFactory<StockServiceClient> {

    /** 降级日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(
            StockServiceClientFallbackFactory.class
    );

    /**
     * 根据本次远程调用的失败原因创建降级客户端。
     *
     * @param cause 远程调用失败原因
     * @return 所有方法均返回服务不可用响应的客户端
     */
    @Override
    public StockServiceClient create(Throwable cause) {
        log.error("调用 stock-service 库存内部接口失败", cause);
        return new StockServiceClient() {

            /**
             * 扣减降级处理：返回失败码，迫使调用方回滚全局事务。
             *
             * @param internalToken 服务间共享密钥
             * @param request 扣减请求
             * @return 服务不可用响应
             */
            @Override
            public ApiResponse<Void> deduct(
                    String internalToken,
                    StockDeductRequest request
            ) {
                return serviceUnavailable();
            }

            /**
             * 回补降级处理。
             *
             * @param internalToken 服务间共享密钥
             * @param request 回补请求
             * @return 服务不可用响应
             */
            @Override
            public ApiResponse<Void> restore(
                    String internalToken,
                    StockRestoreRequest request
            ) {
                return serviceUnavailable();
            }

            /**
             * 库存查询降级处理。
             *
             * @param windowId 档口 ID
             * @param productId 菜品 ID
             * @param internalToken 服务间共享密钥
             * @return 服务不可用响应
             */
            @Override
            public ApiResponse<StockVO> getStock(
                    Long windowId,
                    Long productId,
                    String internalToken
            ) {
                return serviceUnavailable();
            }
        };
    }

    /**
     * 构造统一的服务不可用响应。
     *
     * @param <T> 返回数据类型
     * @return 服务不可用响应
     */
    private <T> ApiResponse<T> serviceUnavailable() {
        // 不向前端暴露库存服务内部异常。
        return ApiResponse.failure(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "库存服务暂时不可用"
        );
    }
}
