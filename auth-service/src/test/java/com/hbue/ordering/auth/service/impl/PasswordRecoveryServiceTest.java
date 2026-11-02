package com.hbue.ordering.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.auth.dto.request.PasswordRecoveryCodeRequest;
import com.hbue.ordering.auth.service.CaptchaService;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.PasswordRecoveryClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordRecoveryServiceTest {
    @Test void captchaMustPassBeforeAnyEmailRequest() {
        var users=mock(PasswordRecoveryClient.class);var captcha=mock(CaptchaService.class);var redis=mock(RedisOperationsService.class);
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(1L);
        var service=new PasswordRecoveryServiceImpl(users,captcha,redis,new ObjectMapper());
        assertThrows(BusinessException.class,()->service.send(new PasswordRecoveryCodeRequest("user@example.com","id","wrong"),"127.0.0.1"));
        verifyNoInteractions(users);
    }
    @Test void validCaptchaUsesExistingInternalMailBoundary() {
        var users=mock(PasswordRecoveryClient.class);var captcha=mock(CaptchaService.class);var redis=mock(RedisOperationsService.class);
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(1L);when(captcha.verify("id","abcd")).thenReturn(true);
        when(users.send(any())).thenReturn(ApiResponse.success(null));
        new PasswordRecoveryServiceImpl(users,captcha,redis,new ObjectMapper()).send(new PasswordRecoveryCodeRequest("user@example.com","id","abcd"),"127.0.0.1");
        verify(users).send(new com.hbue.ordering.user.api.dto.PasswordRecoveryCodeCommand("user@example.com"));
    }
}
