package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.RechargeRequest;
import com.hbue.ordering.user.service.UserWalletService;
import com.hbue.ordering.user.vo.BalanceRecordPageVO;
import com.hbue.ordering.user.vo.BalanceRecordVO;
import com.hbue.ordering.user.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 本人账户接口；用户归属仅取已认证 JWT，不接收客户端指定用户 ID。 */
@RestController
@Tag(name = "账户中心与充值")
@RequestMapping("/users/me")
public class UserWalletController {
    private final UserWalletService walletService;

    public UserWalletController(UserWalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserProfileVO> profile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(walletService.getMyProfile(Long.valueOf(jwt.getSubject())));
    }

    @PostMapping("/recharges")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<BalanceRecordVO> recharge(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RechargeRequest request) {
        return ApiResponse.success(walletService.recharge(Long.valueOf(jwt.getSubject()), idempotencyKey, request));
    }

    @GetMapping("/balance/records")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<BalanceRecordPageVO> records(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResponse.success(walletService.listRecords(Long.valueOf(jwt.getSubject()), type, beforeId, limit));
    }
}
