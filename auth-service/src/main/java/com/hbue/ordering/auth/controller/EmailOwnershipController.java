package com.hbue.ordering.auth.controller;
import com.hbue.ordering.auth.dto.request.EmailOwnershipSendRequest;
import com.hbue.ordering.auth.service.EmailOwnershipService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/auth")
public class EmailOwnershipController {
    private final EmailOwnershipService service;
    public EmailOwnershipController(EmailOwnershipService service){this.service=service;}
    @PostMapping("/registration/code") public ApiResponse<Void> send(@Valid @RequestBody EmailOwnershipSendRequest body,HttpServletRequest request){
        service.sendRegistration(body,com.hbue.ordering.auth.util.ClientIpResolver.resolve(request));return ApiResponse.success(null);
    }
    @PostMapping("/registration/verify") public ApiResponse<EmailRegistrationGrantVO> verify(@RequestBody RegistrationEmailVerifyCommand body,HttpServletRequest request){
        return ApiResponse.success(service.verifyRegistration(body,com.hbue.ordering.auth.util.ClientIpResolver.resolve(request)));
    }
    @PostMapping("/email-enrollment/code") public ApiResponse<Void> sendEnrollment(@Valid @RequestBody EmailOwnershipSendRequest body,HttpServletRequest request){
        service.sendEnrollment(body,com.hbue.ordering.auth.util.ClientIpResolver.resolve(request));return ApiResponse.success(null);
    }
    @PostMapping("/email-enrollment/confirm") public ApiResponse<Void> confirm(@RequestBody EmailEnrollmentConfirmCommand body,HttpServletRequest request){
        service.confirmEnrollment(body,com.hbue.ordering.auth.util.ClientIpResolver.resolve(request));return ApiResponse.success(null);
    }
}
