package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailChangeAuthorizationServiceTest {
    private final UserMapper users = mock(UserMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final RedisOperationsService redis = mock(RedisOperationsService.class);
    private final EmailChangeAuthorizationService service = new EmailChangeAuthorizationService(users, passwords, redis);

    @Test void correctLoginPasswordIssuesShortLivedUserScopedOpaqueToken() {
        UserDO user = new UserDO();
        user.setUserId(7L); user.setStatus(1); user.setPasswordHash("bcrypt-hash");
        when(users.selectPasswordsById(7L)).thenReturn(user);
        when(passwords.matches("correct-password", "bcrypt-hash")).thenReturn(true);

        String token = service.authorize(7L, "correct-password");

        assertTrue(token.matches("[a-f0-9]{64}"));
        verify(passwords).matches("correct-password", "bcrypt-hash");
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(redis).setValue(eq("email:change:authorization:7"), digest.capture(), eq(Duration.ofMinutes(10)));
        assertNotEquals(token, digest.getValue());
    }

    @Test void wrongPasswordDoesNotIssueAuthorization() {
        UserDO user = new UserDO();
        user.setUserId(7L); user.setStatus(1); user.setPasswordHash("bcrypt-hash");
        when(users.selectPasswordsById(7L)).thenReturn(user);
        when(passwords.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.authorize(7L, "wrong-password"));
        verify(redis, never()).setValue(anyString(), anyString(), any(Duration.class));
    }
}
