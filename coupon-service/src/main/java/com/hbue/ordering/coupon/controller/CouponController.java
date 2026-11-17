package com.hbue.ordering.coupon.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.service.CouponService;
import com.hbue.ordering.coupon.vo.CouponTemplateVO;
import com.hbue.ordering.coupon.vo.UserCouponVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 学生端优惠券接口；每个用户 ID 均从已验签 JWT 主体提取。 */
@Validated
@RestController
@Tag(name = "学生优惠券接口")
@RequestMapping("/coupons")
@PreAuthorize("hasRole('CUSTOMER')")
public class CouponController {
    /** 优惠券业务服务。 */
    private final CouponService couponService;

    /** 注入优惠券业务服务。 */
    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 查询当前开放领取且仍有库存的券模板。 */
    @GetMapping("/claimable")
    public ApiResponse<List<CouponTemplateVO>> listClaimable() {
        return ApiResponse.success(couponService.listClaimable());
    }

    /** 为登录学生领取券；请求中不接受 userId，避免代领或越权领取。 */
    @PostMapping("/{templateId}/claim")
    public ApiResponse<UserCouponVO> claim(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("templateId") @Positive Long templateId
    ) {
        return ApiResponse.success(couponService.claim(currentUserId(jwt), templateId));
    }

    /** 查询登录学生本人持券；状态过滤由服务端按枚举校验并限制返回数量。 */
    @GetMapping("/mine")
    public ApiResponse<List<UserCouponVO>> listMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ApiResponse.success(couponService.listMine(currentUserId(jwt), status));
    }

    /** 从资源服务器已验签 JWT 读取数字用户 ID。 */
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
