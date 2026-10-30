package com.hbue.ordering.product.api.client.fallback;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.product.api.vo.RestaurantInfoVO;
import com.hbue.ordering.product.api.vo.WindowInfoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 档口与餐厅内部查询失败时创建统一降级客户端。
 *
 * <p>数据范围校验依赖这些查询确认档口归属和餐厅存在性，因此远程失败时
 * 不能伪造资源信息或放行操作；本工厂返回服务不可用结果，让调用方按失败关闭处理。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Component
public class WindowInternalClientFallbackFactory
        implements FallbackFactory<WindowInternalClient> {

    /** 降级日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(
            WindowInternalClientFallbackFactory.class
    );

    /**
     * 根据本次远程调用的失败原因创建降级客户端。
     *
     * @param cause 远程调用失败原因
     * @return 两个查询方法均返回服务不可用响应的客户端
     */
    @Override
    public WindowInternalClient create(Throwable cause) {
        log.error("调用 product-service 档口/餐厅内部查询接口失败", cause);
        return new WindowInternalClient() {
            @Override
            public ApiResponse<java.util.Map<Long, String>> getWindowNames(java.util.List<Long> ids, String token) {
                return serviceUnavailable();
            }


            /**
             * 档口查询降级处理。
             *
             * @param windowId 档口 ID
             * @param internalToken 服务间共享密钥
             * @return 服务不可用响应
             */
            @Override
            public ApiResponse<WindowInfoVO> getWindow(
                    Long windowId,
                    String internalToken
            ) {
                return serviceUnavailable();
            }

            /**
             * 餐厅查询降级处理。
             *
             * @param restaurantId 餐厅 ID
             * @param internalToken 服务间共享密钥
             * @return 服务不可用响应
             */
            @Override
            public ApiResponse<RestaurantInfoVO> getRestaurant(
                    Long restaurantId,
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
        // 不向前端暴露远程服务内部异常。
        return ApiResponse.failure(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "商品服务暂时不可用"
        );
    }
}
