package com.hbue.ordering.coupon.api.client.fallback;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.api.client.CouponServiceClient;
import com.hbue.ordering.coupon.api.dto.CouponLockRequest;
import com.hbue.ordering.coupon.api.dto.CouponReleaseRequest;
import com.hbue.ordering.coupon.api.vo.CouponLockVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 优惠券内部调用的失败关闭降级。
 *
 * <p>优惠券服务不可用不能被解释为“未选券”或“锁券成功”，否则订单金额与用户选择不一致；
 * 所以本工厂统一返回服务不可用，让下单/取消的 Seata 事务按失败回滚。</p>
 */
@Component
public class CouponServiceClientFallbackFactory implements FallbackFactory<CouponServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(CouponServiceClientFallbackFactory.class);

    /** 记录远程失败原因，并返回不可能伪装成功的内部客户端。 */
    @Override
    public CouponServiceClient create(Throwable cause) {
        log.error("调用 coupon-service 内部接口失败", cause);
        return new CouponServiceClient() {
            @Override
            public ApiResponse<CouponLockVO> lock(String internalToken, CouponLockRequest request) {
                return unavailable();
            }

            @Override
            public ApiResponse<Void> release(String internalToken, CouponReleaseRequest request) {
                return unavailable();
            }

            private <T> ApiResponse<T> unavailable() {
                return ApiResponse.failure(CommonErrorCode.SERVICE_UNAVAILABLE, "优惠券服务暂时不可用");
            }
        };
    }
}
