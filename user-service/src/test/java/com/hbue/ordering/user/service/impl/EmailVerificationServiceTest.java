package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.dto.request.SendEmailCodeRequest;
import com.hbue.ordering.user.dto.request.VerifyEmailCaptchaRequest;
import com.hbue.ordering.user.service.EmailChangeAuthorization;
import com.hbue.ordering.user.service.EmailCodeSender;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 邮箱换绑的人机验证、一次性发送凭据和验证码边界。 */
class EmailVerificationServiceTest {
    private final RedisOperationsService redis = mock(RedisOperationsService.class);
    private final EmailCodeSender sender = mock(EmailCodeSender.class);
    private final EmailChangeAuthorization authorization = mock(EmailChangeAuthorization.class);
    private final EmailVerificationService service = new EmailVerificationService(redis, sender, authorization,
            "0123456789abcdef0123456789abcdef");
    private final String authToken = "a".repeat(64);
    private final String captchaTicket = "b".repeat(64);

    @Test void imageCaptchaMustPassBeforeEmailSendCredentialIsIssued() {
        when(redis.getAndDelete("auth:captcha:image-id")).thenReturn("ABCD");
        String ticket = service.verifyCaptcha(7L,
                new VerifyEmailCaptchaRequest("USER@example.com", authToken, "image-id", "abcd"));
        assertTrue(ticket.matches("[a-f0-9]{64}"));
        verify(authorization).verify(7L, authToken);
        verify(redis).setValue(eq("email:captcha:verified:7"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test void wrongOrExpiredImageCaptchaDoesNotIssueTicket() {
        when(redis.getAndDelete("auth:captcha:image-id")).thenReturn("ABCD");
        assertThrows(BusinessException.class, () -> service.verifyCaptcha(7L,
                new VerifyEmailCaptchaRequest("user@example.com", authToken, "image-id", "WRONG")));
        verify(redis, never()).setValue(eq("email:captcha:verified:7"), anyString(), any());
    }

    @Test void sendingRequiresAOneTimeCaptchaTicketAndAuthorization() {
        when(redis.executeScript(any(), eq(List.of("email:captcha:verified:7")), any())).thenReturn(1L);
        when(redis.increment(anyString(), any(Duration.class))).thenReturn(1L);
        service.send(7L, new SendEmailCodeRequest("USER@example.com", authToken, captchaTicket));
        verify(authorization).verify(7L, authToken);
        verify(redis).executeScript(any(), eq(List.of("email:captcha:verified:7")), any());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("user@example.com"), code.capture());
        assertTrue(code.getValue().matches("[0-9]{6}"));
        verify(redis).setValue(eq("email:verification:user:7"), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test void sendingWithoutVerifiedCaptchaDoesNotSendMailOrConsumeRateQuota() {
        when(redis.executeScript(any(), anyList(), any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> service.send(7L,
                new SendEmailCodeRequest("user@example.com", authToken, captchaTicket)));
        verifyNoInteractions(sender);
        verify(redis, never()).increment(anyString(), any(Duration.class));
    }

    @Test void emailCodeIsBoundToTheSameAuthorizationAndEmail() {
        when(redis.executeScript(any(), anyList(), any())).thenReturn(1L);
        when(redis.executeScript(any(), anyList(), any(), eq("5"))).thenReturn(1L);
        service.consume(7L, authToken, "first@example.com", "123456");
        service.consume(7L, authToken, "other@example.com", "123456");
        verify(authorization, times(2)).verify(7L, authToken);
        ArgumentCaptor<Object> digests = ArgumentCaptor.forClass(Object.class);
        verify(redis, times(2)).executeScript(any(), eq(List.of("email:verification:user:7")), digests.capture(), eq("5"));
        assertNotEquals(digests.getAllValues().get(0), digests.getAllValues().get(1));
    }
}
