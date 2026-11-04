package com.hbue.ordering.user.controller;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.*;
import com.hbue.ordering.user.service.PayPasswordRecoveryService;
import com.hbue.ordering.user.vo.PayPasswordRecoveryAuthorizationVO;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/pay-password/recovery")
@PreAuthorize("hasRole('CUSTOMER')")
public class PayPasswordRecoveryController {
    private final PayPasswordRecoveryService recovery;
    public PayPasswordRecoveryController(PayPasswordRecoveryService recovery) { this.recovery = recovery; }
    @PostMapping("/authorization")
    public ApiResponse<PayPasswordRecoveryAuthorizationVO> authorize(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PayPasswordRecoveryAuthorizeRequest request) {
        return ApiResponse.success(recovery.authorize(Long.valueOf(jwt.getSubject()), request.loginPassword()));
    }
    @PostMapping("/code")
    public ApiResponse<Void> send(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PayPasswordRecoveryCodeRequest request) {
        recovery.send(Long.valueOf(jwt.getSubject()), request.authorizationToken(), request.captchaId(), request.captchaCode());
        return ApiResponse.success(null);
    }
    @PostMapping("/verify")
    public ApiResponse<PasswordRecoveryGrantVO> verify(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PayPasswordRecoveryVerifyRequest request) {
        return ApiResponse.success(recovery.verify(Long.valueOf(jwt.getSubject()), request.authorizationToken(), request.code()));
    }
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PayPasswordRecoveryConfirmRequest request) {
        recovery.confirm(Long.valueOf(jwt.getSubject()), request.resetToken(), request.newPayPassword());
        return ApiResponse.success(null);
    }
}
