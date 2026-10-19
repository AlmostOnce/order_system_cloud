package com.hbue.ordering.order.api.client.fallback;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.client.OrderServiceClient;
import com.hbue.ordering.order.api.vo.OrderCartSnapshotVO;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * 订单读取降级：明确区分身份错误、不存在与暂时不可用，绝不伪造订单成功。
 * 不记录原始 HTTP 异常，避免异常内容携带用户订单详情或凭据。
 */
public class OrderServiceClientFallbackFactory implements FallbackFactory<OrderServiceClient> {
    /**
     * 根据本次调用失败创建安全的只读降级响应。
     * @param cause 网络、HTTP 或 Sentinel 阻断异常
     * @return 每次读取均返回明确失败状态的客户端
     */
    @Override
    public OrderServiceClient create(Throwable cause) {
        CommonErrorCode code = CommonErrorCode.SERVICE_UNAVAILABLE;
        if (cause instanceof FeignException exception) {
            // 保留安全边界的 HTTP 语义，其余异常统一要求调用方稍后重试清理。
            code = switch (exception.status()) {
                case 401 -> CommonErrorCode.UNAUTHORIZED;
                case 403 -> CommonErrorCode.FORBIDDEN;
                case 404 -> CommonErrorCode.NOT_FOUND;
                default -> CommonErrorCode.SERVICE_UNAVAILABLE;
            };
        }
        CommonErrorCode failureCode = code;
        return new OrderServiceClient() {
            /** 依赖失败时只返回错误，不提供可供删除购物车的订单证明。 */
            @Override
            public ApiResponse<OrderCartSnapshotVO> getOrder(Long orderId) {
                return ApiResponse.failure(failureCode);
            }
        };
    }
}
