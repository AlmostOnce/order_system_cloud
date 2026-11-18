package com.hbue.ordering.coupon.controller;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.coupon.api.dto.CouponLockRequest;
import com.hbue.ordering.coupon.api.dto.CouponReleaseRequest;
import com.hbue.ordering.coupon.api.vo.CouponLockVO;
import com.hbue.ordering.coupon.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 仅供 order-service 使用的内部接口；入口以共享令牌鉴权，不暴露到 Gateway。
 * 业务失败沿用库存内部接口的约定，以 HTTP 200 返回错误业务码，避免 Feign 降级吞掉原因。
 */
@RestController
@RequestMapping("/internal/coupons")
public class CouponInternalController {
    /**
     * 复用项目统一的服务间共享令牌校验器。
     */
    private final InternalScopeTokenVerifier tokenVerifier;
    /**
     * 锁定、释放券状态的业务服务。
     */
    private final CouponService couponService;

    /**
     * 注入项目统一内部令牌校验器和优惠券业务服务。
     */
    public CouponInternalController(
            InternalScopeTokenVerifier tokenVerifier,
            CouponService couponService
    ) {
        this.tokenVerifier = tokenVerifier;
        this.couponService = couponService;
    }

    /**
     * 复核券归属、窗口、金额门槛和有效期后锁券；失败时订单必须整体回滚。
     */
    @PostMapping("/lock")
    public ApiResponse<CouponLockVO> lock(
            @RequestHeader("X-Internal-Token") String token,
            @Valid @RequestBody CouponLockRequest request
    ) {
        tokenVerifier.verify(token);
        try {
            return ApiResponse.success(couponService.lock(request));
        } catch (BusinessException exception) {
            return ApiResponse.failure(exception.getErrorCode(), exception.getMessage());
        }
    }

    /**
     * 只按订单号释放其当前锁定的券；是否确认关单由调用方订单流程负责。
     */
    @PostMapping("/release")
    public ApiResponse<Void> release(
            @RequestHeader("X-Internal-Token") String token,
            @Valid @RequestBody CouponReleaseRequest request
    ) {
        tokenVerifier.verify(token);
        try {
            couponService.release(request.getOrderNo());
        } catch (BusinessException exception) {
            return ApiResponse.failure(exception.getErrorCode(), exception.getMessage());
        }
        return ApiResponse.success(null);
    }
}
