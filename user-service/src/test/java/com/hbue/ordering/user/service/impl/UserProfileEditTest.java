package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.user.dto.request.UpdateProfileRequest;
import com.hbue.ordering.user.dto.request.ChangeOwnEmailRequest;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserProfileEditTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final com.hbue.ordering.user.service.EmailVerification codes = mock(com.hbue.ordering.user.service.EmailVerification.class);
    private final UserServiceImpl service = new UserServiceImpl(passwords,
            mock(UserDataScopeService.class), mock(ManagementAuditLogger.class), codes);
    private final UserDO current = new UserDO();

    UserProfileEditTest() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        current.setUserId(7L); current.setStatus(1); current.setUsername("旧昵称");
        current.setEmail("old@example.com"); current.setPasswordHash("hash");
        current.setUserNo("1234567890"); current.setUserUuid("public-uuid");
        when(mapper.selectProfileForUpdate(7L)).thenReturn(current);
        when(mapper.selectOwnProfile(7L)).thenReturn(current);
        when(mapper.updateOwnProfile(anyLong(), anyString())).thenReturn(1);
        when(mapper.updateOwnEmail(anyLong(), anyString())).thenReturn(1);
    }

    @Test void nicknameOnlyDoesNotRequirePasswordOrModifyIdentity() {
        var result = service.updateOwnProfile(7L, new UpdateProfileRequest(" 新昵称 "));
        verify(mapper).updateOwnProfile(7L, "新昵称");
        verifyNoInteractions(passwords, codes);
        assertEquals("1234567890", result.userNo());
        assertEquals("public-uuid", result.userUuid());
    }

    @Test void changingEmailRequiresPriorPasswordAndCaptchaAuthorization() {
        String token = "a".repeat(64);
        service.changeOwnEmail(7L, new ChangeOwnEmailRequest(" NEW@Example.com ", token, "123456"));
        verify(codes).verifyAuthorization(7L, token);
        verify(codes).consume(7L, token, "new@example.com", "123456");
        verify(codes).consumeAuthorization(7L, token);
        verify(mapper).updateOwnEmail(7L, "new@example.com");
        verify(mapper, never()).updateOwnProfile(anyLong(), anyString());
    }

    @Test void rejectsOccupiedEmailAndUnavailableAccounts() {
        UserDO other = new UserDO(); other.setUserId(8L);
        when(mapper.selectByEmail("new@example.com")).thenReturn(other);
        String token = "b".repeat(64);
        assertThrows(BusinessException.class, () -> service.changeOwnEmail(7L,
                new ChangeOwnEmailRequest("new@example.com", token, "123456")));
        current.setStatus(0);
        assertThrows(BusinessException.class, () -> service.changeOwnEmail(7L,
                new ChangeOwnEmailRequest("old@example.com", token, null)));
        verify(mapper, never()).updateOwnEmail(anyLong(), anyString());
    }

    @Test void cannotChangeEmailWithoutValidCode() {
        String token = "c".repeat(64);
        doThrow(new BusinessException(com.hbue.ordering.common.core.error.CommonErrorCode.BAD_REQUEST))
                .when(codes).consume(7L, token, "new@example.com", null);
        assertThrows(BusinessException.class, () -> service.changeOwnEmail(7L,
                new ChangeOwnEmailRequest("new@example.com", token, null)));
        verify(mapper, never()).updateOwnEmail(anyLong(), anyString());
    }
}
