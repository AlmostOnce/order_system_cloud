package com.hbue.ordering.user.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="user-service", contextId="passwordRecovery", configuration=UserServiceInternalConfiguration.class)
public interface PasswordRecoveryClient {
    @PostMapping("/internal/users/password-recovery/code")
    ApiResponse<Void> send(@RequestBody PasswordRecoveryCodeCommand body);
    @PostMapping("/internal/users/password-recovery/verify")
    ApiResponse<PasswordRecoveryGrantVO> verify(@RequestBody PasswordRecoveryVerifyCommand body);
    @PostMapping("/internal/users/password-recovery/confirm")
    ApiResponse<Void> confirm(@RequestBody PasswordRecoveryConfirmCommand body);
}
