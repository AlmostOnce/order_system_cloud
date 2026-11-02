package com.hbue.ordering.auth.controller;

import com.hbue.ordering.auth.dto.request.PasswordRecoveryCodeRequest;
import com.hbue.ordering.auth.service.PasswordRecoveryService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/password-reset")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    public PasswordRecoveryController(PasswordRecoveryService recovery){this.recovery=recovery;}
    @PostMapping("/code")
    public ApiResponse<Void> send(@Valid @RequestBody PasswordRecoveryCodeRequest body,HttpServletRequest request){
        recovery.send(body,source(request));return ApiResponse.success(null);
    }
    @PostMapping("/verify")
    public ApiResponse<PasswordRecoveryGrantVO> verify(@RequestBody PasswordRecoveryVerifyCommand body,HttpServletRequest request){
        return ApiResponse.success(recovery.verify(body,source(request)));
    }
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestBody PasswordRecoveryConfirmCommand body,HttpServletRequest request){
        recovery.confirm(body,source(request));return ApiResponse.success(null);
    }
    private String source(HttpServletRequest request){
        String forwarded=request.getHeader("X-Forwarded-For");
        return forwarded==null||forwarded.isBlank()?request.getRemoteAddr():forwarded.split(",")[0].strip();
    }
}
