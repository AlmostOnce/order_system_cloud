package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.ChangeOwnEmailRequest;
import com.hbue.ordering.user.dto.request.AuthorizeEmailChangeRequest;
import com.hbue.ordering.user.dto.request.SendEmailCodeRequest;
import com.hbue.ordering.user.dto.request.VerifyEmailCaptchaRequest;
import com.hbue.ordering.user.service.EmailVerification;
import com.hbue.ordering.user.service.UserService;
import com.hbue.ordering.user.vo.EmailChangeAuthorizationVO;
import com.hbue.ordering.user.vo.EmailCaptchaVerificationVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户的邮箱验证与换绑接口，与普通资料编辑分离。 */
@RestController
@RequestMapping("/users/me/email")
@PreAuthorize("isAuthenticated()")
public class UserEmailController {
    private final UserService users;
    private final EmailVerification emailVerification;

    public UserEmailController(UserService users, EmailVerification emailVerification) {
        this.users = users;
        this.emailVerification = emailVerification;
    }

    @PostMapping("/authorization")
    public ApiResponse<EmailChangeAuthorizationVO> authorize(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuthorizeEmailChangeRequest request) {
        String token = emailVerification.authorize(Long.valueOf(jwt.getSubject()), request.loginPassword());
        return ApiResponse.success(new EmailChangeAuthorizationVO(token, 600));
    }

    @PostMapping("/captcha")
    public ApiResponse<EmailCaptchaVerificationVO> verifyCaptcha(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyEmailCaptchaRequest request) {
        String token = emailVerification.verifyCaptcha(Long.valueOf(jwt.getSubject()), request);
        return ApiResponse.success(new EmailCaptchaVerificationVO(token, 300));
    }

    @PostMapping("/verification-code")
    public ApiResponse<Void> sendVerificationCode(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendEmailCodeRequest request) {
        emailVerification.send(Long.valueOf(jwt.getSubject()), request);
        return ApiResponse.success(null);
    }

    @PutMapping
    public ApiResponse<Void> changeOwnEmail(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeOwnEmailRequest request) {
        users.changeOwnEmail(Long.valueOf(jwt.getSubject()), request);
        return ApiResponse.success(null);
    }
}
