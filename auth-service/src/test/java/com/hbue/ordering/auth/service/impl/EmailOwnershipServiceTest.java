package com.hbue.ordering.auth.service.impl;
import com.hbue.ordering.auth.dto.request.EmailOwnershipSendRequest;
import com.hbue.ordering.auth.service.CaptchaService;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.api.client.EmailOwnershipClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class EmailOwnershipServiceTest {
    @Test void registrationCannotSendMailBeforeHumanVerification() {
        var users=mock(EmailOwnershipClient.class);var captcha=mock(CaptchaService.class);var redis=mock(RedisOperationsService.class);
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(1L);
        when(captcha.verify("image","wrong")).thenReturn(false);
        var service=new EmailOwnershipServiceImpl(users,captcha,redis,new ObjectMapper());
        assertThrows(BusinessException.class,()->service.sendRegistration(new EmailOwnershipSendRequest("user@example.com","image","wrong",null),"127.0.0.1"));
        verifyNoInteractions(users);
    }
    @Test void enrollmentCannotSendMailBeforeHumanVerification() {
        var users=mock(EmailOwnershipClient.class);var captcha=mock(CaptchaService.class);var redis=mock(RedisOperationsService.class);
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(1L);
        var service=new EmailOwnershipServiceImpl(users,captcha,redis,new ObjectMapper());
        assertThrows(BusinessException.class,()->service.sendEnrollment(new EmailOwnershipSendRequest("user@example.com","image","wrong","a".repeat(64)),"127.0.0.1"));
        verifyNoInteractions(users);
    }
}
