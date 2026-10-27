package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.user.dto.request.UpdateProfileRequest;
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
    private final UserServiceImpl service = new UserServiceImpl(passwords,
            mock(UserDataScopeService.class), mock(ManagementAuditLogger.class));
    private final UserDO current = new UserDO();

    UserProfileEditTest() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        current.setUserId(7L); current.setStatus(1); current.setUsername("旧昵称");
        current.setEmail("old@example.com"); current.setPasswordHash("hash");
        current.setUserNo("1234567890"); current.setUserUuid("public-uuid");
        when(mapper.selectProfileForUpdate(7L)).thenReturn(current);
        when(mapper.selectOwnProfile(7L)).thenReturn(current);
        when(mapper.updateOwnProfile(anyLong(), anyString(), any())).thenReturn(1);
    }

    @Test void nicknameOnlyDoesNotRequirePasswordOrModifyIdentity() {
        var result = service.updateOwnProfile(7L, new UpdateProfileRequest(" 新昵称 ", "old@example.com", null));
        verify(mapper).updateOwnProfile(7L, "新昵称", "old@example.com");
        verifyNoInteractions(passwords);
        assertEquals("1234567890", result.userNo());
        assertEquals("public-uuid", result.userUuid());
    }

    @Test void changingEmailRequiresCorrectLoginPassword() {
        assertThrows(BusinessException.class, () -> service.updateOwnProfile(7L,
                new UpdateProfileRequest("新昵称", "new@example.com", "wrong")));
        verify(mapper, never()).updateOwnProfile(anyLong(), anyString(), any());
        when(passwords.matches("correct", "hash")).thenReturn(true);
        service.updateOwnProfile(7L, new UpdateProfileRequest("新昵称", " NEW@Example.com ", "correct"));
        verify(mapper).updateOwnProfile(7L, "新昵称", "new@example.com");
    }

    @Test void rejectsOccupiedEmailAndUnavailableAccounts() {
        when(passwords.matches("correct", "hash")).thenReturn(true);
        UserDO other = new UserDO(); other.setUserId(8L);
        when(mapper.selectByEmail("new@example.com")).thenReturn(other);
        assertThrows(BusinessException.class, () -> service.updateOwnProfile(7L,
                new UpdateProfileRequest("新昵称", "new@example.com", "correct")));
        current.setStatus(0);
        assertThrows(BusinessException.class, () -> service.updateOwnProfile(7L,
                new UpdateProfileRequest("新昵称", "old@example.com", "")));
        verify(mapper, never()).updateOwnProfile(anyLong(), anyString(), any());
    }
}
