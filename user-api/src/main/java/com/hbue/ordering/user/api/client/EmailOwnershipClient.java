package com.hbue.ordering.user.api.client;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="user-service",contextId="emailOwnership",configuration=UserServiceInternalConfiguration.class)
public interface EmailOwnershipClient {
    @PostMapping("/internal/users/email-ownership/registration/code")
    ApiResponse<Void> sendRegistration(@RequestBody RegistrationEmailSendCommand body);
    @PostMapping("/internal/users/email-ownership/registration/verify")
    ApiResponse<EmailRegistrationGrantVO> verifyRegistration(@RequestBody RegistrationEmailVerifyCommand body);
    @PostMapping("/internal/users/email-ownership/enrollment/challenge")
    ApiResponse<EmailEnrollmentChallengeVO> challenge(@RequestBody EmailEnrollmentChallengeCommand body);
    @PostMapping("/internal/users/email-ownership/enrollment/code")
    ApiResponse<Void> sendEnrollment(@RequestBody EmailEnrollmentSendCommand body);
    @PostMapping("/internal/users/email-ownership/enrollment/confirm")
    ApiResponse<Void> confirmEnrollment(@RequestBody EmailEnrollmentConfirmCommand body);
}
