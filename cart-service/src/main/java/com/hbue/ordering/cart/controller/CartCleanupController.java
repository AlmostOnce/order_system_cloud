package com.hbue.ordering.cart.controller;

import com.hbue.ordering.cart.dto.request.CartCleanupRequest;
import com.hbue.ordering.cart.service.CartCleanupService;
import com.hbue.ordering.cart.vo.CartCleanupVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车下单后清理入口，受现有 CUSTOMER 安全规则保护，用户身份只来自 JWT。
 * 不提供清理他人购物车的 userId 参数，不接受客户端自行声明订单成功。
 */
@RestController
@RequestMapping("/cart/orders")
@Tag(name = "购物车接口")
public class CartCleanupController {
    /** 负责订单验证与版本清理的业务接口。 */
    private final CartCleanupService cleanupService;

    /**
     * 创建清理控制器。
     * @param cleanupService 下单后清理服务
     */
    public CartCleanupController(CartCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /**
     * 清理已成功下单的原始快照；同一个订单与快照可安全重试。
     * @param jwt 已验证顾客 JWT
     * @param orderId 已成功创建的订单 ID
     * @param request 下单前保存的快照，不得在重试时刷新 revision
     * @return 实际删除与跳过的数量，跳过不代表下单失败
     */
    @PostMapping("/{orderId}/cleanup")
    public ApiResponse<CartCleanupVO> cleanup(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("orderId") Long orderId, @Valid @RequestBody CartCleanupRequest request) {
        long userId;
        try {
            // 验签后的 subject 仍需符合系统正整数用户主键约定。
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录用户身份无效");
        }
        return ApiResponse.success(cleanupService.cleanup(userId, orderId, request));
    }
}
