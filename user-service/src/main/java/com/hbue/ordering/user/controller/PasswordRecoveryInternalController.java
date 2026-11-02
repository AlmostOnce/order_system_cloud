package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
import com.hbue.ordering.user.service.PasswordRecoveryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users/password-recovery")
public class PasswordRecoveryInternalController {
    private final InternalScopeTokenVerifier credentials;
    private final PasswordRecoveryService recovery;
    public PasswordRecoveryInternalController(InternalScopeTokenVerifier credentials, PasswordRecoveryService recovery) {
        this.credentials=credentials; this.recovery=recovery;
    }
    @PostMapping("/code")
    public ApiResponse<Void> send(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @RequestBody PasswordRecoveryCodeCommand body) {
        credentials.verify(token); recovery.send(body.email()); return ApiResponse.success(null);
    }
    @PostMapping("/verify")
    public ApiResponse<PasswordRecoveryGrantVO> verify(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @RequestBody PasswordRecoveryVerifyCommand body) {
        credentials.verify(token); return ApiResponse.success(recovery.verify(body.email(),body.code()));
    }
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @RequestBody PasswordRecoveryConfirmCommand body) {
        credentials.verify(token); recovery.confirm(body.resetToken(),body.newPassword()); return ApiResponse.success(null);
    }
}
