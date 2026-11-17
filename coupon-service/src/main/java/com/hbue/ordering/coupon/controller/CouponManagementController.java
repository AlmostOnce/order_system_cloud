package com.hbue.ordering.coupon.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.dto.CouponTemplateCreateRequest;
import com.hbue.ordering.coupon.service.CouponService;
import com.hbue.ordering.coupon.vo.CouponTemplateVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台券管理接口；总经理角色门禁之外还要由 Service 核实 PLATFORM 数据范围。 */
@Validated
@RestController
@Tag(name = "平台优惠券管理接口")
@RequestMapping("/coupons/admin/templates")
@PreAuthorize("hasRole('GENERAL_MANAGER')")
public class CouponManagementController {
    /** 管理模板及生命周期状态的业务服务。 */
    private final CouponService couponService;

    /** 注入优惠券业务服务。 */
    public CouponManagementController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 查询有硬上限的平台券模板管理清单。 */
    @GetMapping
    public ApiResponse<List<CouponTemplateVO>> listTemplates(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(couponService.listAdminTemplates(operatorId(jwt)));
    }

    /** 创建平台预算承担的满减券草稿，不接受客户端指定发行方或出资方。 */
    @PostMapping
    public ApiResponse<CouponTemplateVO> createTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CouponTemplateCreateRequest request
    ) {
        return ApiResponse.success(couponService.createTemplate(operatorId(jwt), request));
    }

    /** 发布草稿，领取是否开放仍受模板时间窗和总量限制。 */
    @PostMapping("/{templateId}/publish")
    public ApiResponse<Void> publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("templateId") @Positive Long templateId
    ) {
        couponService.publishTemplate(operatorId(jwt), templateId);
        return ApiResponse.success(null);
    }

    /** 只停止新增领取，不影响已经领取的券。 */
    @PostMapping("/{templateId}/stop-claims")
    public ApiResponse<Void> stopClaims(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("templateId") @Positive Long templateId
    ) {
        couponService.stopClaims(operatorId(jwt), templateId);
        return ApiResponse.success(null);
    }

    /** 停止新锁券并撤销尚未锁定的用户券。 */
    @PostMapping("/{templateId}/stop-use")
    public ApiResponse<Void> stopUse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("templateId") @Positive Long templateId
    ) {
        couponService.stopUse(operatorId(jwt), templateId);
        return ApiResponse.success(null);
    }

    /** 从验签 JWT 主体提取管理操作者 ID。 */
    private Long operatorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
