package com.hbue.ordering.user.controller;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.*;
import com.hbue.ordering.user.service.EmailOwnershipService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/users/email-ownership")
public class EmailOwnershipInternalController {
    private final EmailOwnershipService service;
    private final InternalScopeTokenVerifier tokens;
    public EmailOwnershipInternalController(EmailOwnershipService service,InternalScopeTokenVerifier tokens){this.service=service;this.tokens=tokens;}
    @PostMapping("/registration/code") public ApiResponse<Void> send(@RequestHeader(value="X-Internal-Token",required=false) String token,@RequestBody RegistrationEmailSendCommand body){
        tokens.verify(token);service.sendRegistration(body.email());return ApiResponse.success(null);
    }
    @PostMapping("/registration/verify") public ApiResponse<EmailRegistrationGrantVO> verify(@RequestHeader(value="X-Internal-Token",required=false) String token,@RequestBody RegistrationEmailVerifyCommand body){
        tokens.verify(token);return ApiResponse.success(service.verifyRegistration(body.email(),body.code()));
    }
    @PostMapping("/enrollment/challenge") public ApiResponse<EmailEnrollmentChallengeVO> challenge(@RequestHeader(value="X-Internal-Token",required=false) String token,@RequestBody EmailEnrollmentChallengeCommand body){
        tokens.verify(token);return ApiResponse.success(service.challenge(body.userId(),body.credentialVersion()));
    }
    @PostMapping("/enrollment/code") public ApiResponse<Void> sendEnrollment(@RequestHeader(value="X-Internal-Token",required=false) String token,@RequestBody EmailEnrollmentSendCommand body){
        tokens.verify(token);service.sendEnrollment(body.authorizationToken(),body.email());return ApiResponse.success(null);
    }
    @PostMapping("/enrollment/confirm") public ApiResponse<Void> confirm(@RequestHeader(value="X-Internal-Token",required=false) String token,@RequestBody EmailEnrollmentConfirmCommand body){
        tokens.verify(token);service.confirmEnrollment(body.authorizationToken(),body.email(),body.code());return ApiResponse.success(null);
    }
}
