package com.hbue.ordering.coupon.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.api.client.fallback.CouponServiceClientFallbackFactory;
import com.hbue.ordering.coupon.api.dto.CouponLockRequest;
import com.hbue.ordering.coupon.api.dto.CouponReleaseRequest;
import com.hbue.ordering.coupon.api.vo.CouponLockVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 订单服务调用优惠券服务的内部契约。
 *
 * <p>锁券与释放必须在调用方现有 Seata 全局事务中完成；远程失败或业务拒绝时，
 * 调用方不得继续创建/取消订单。支付成功后的核销使用订单 Outbox 消息，避免
 * 余额已经扣除后因同步券服务故障回滚订单确认。</p>
 */
@FeignClient(name = "coupon-service", contextId = "couponServiceClient",
        fallbackFactory = CouponServiceClientFallbackFactory.class)
public interface CouponServiceClient {

    /** 以订单服务核实的用户、窗口与商品金额尝试锁定一张券。 */
    @PostMapping("/internal/coupons/lock")
    ApiResponse<CouponLockVO> lock(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody CouponLockRequest request
    );

    /** 只释放绑定到指定订单且仍处于 LOCKED 的券。 */
    @PostMapping("/internal/coupons/release")
    ApiResponse<Void> release(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody CouponReleaseRequest request
    );
}
