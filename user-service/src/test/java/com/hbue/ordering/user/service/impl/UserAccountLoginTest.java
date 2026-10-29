package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证登录标识、密码和新注册账户号，不依赖远程服务。 */
class UserAccountLoginTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final UserServiceImpl service = new UserServiceImpl(passwords,
            mock(UserDataScopeService.class), mock(ManagementAuditLogger.class),
            mock(com.hbue.ordering.user.service.EmailVerification.class));

    UserAccountLoginTest() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void shouldAuthenticateNumberAndNormalizedEmailWithPassword() {
        UserDO user = new UserDO();
        user.setUserNo("1234567890"); user.setStatus(1); user.setPasswordHash("hash");
        when(mapper.selectByAccountForLogin(anyString())).thenReturn(user);
        when(passwords.matches("password", "hash")).thenReturn(true);
        for (String account : new String[]{"1234567890", " USER@Example.com "}) {
            UserPasswordVerifyCommand command = new UserPasswordVerifyCommand();
            command.setAccount(account); command.setPassword("password");
            assertEquals("1234567890", service.authenticate(command).getUserNo());
        }
        verify(mapper).selectByAccountForLogin("user@example.com");
        UserPasswordVerifyCommand wrong = new UserPasswordVerifyCommand();
        wrong.setAccount("1234567890"); wrong.setPassword("incorrect");
        assertThrows(BusinessException.class, () -> service.authenticate(wrong));
    }

    @Test
    void shouldRegisterEmailAndAssignIndependentNumber() {
        UserRegisterCommand command = new UserRegisterCommand();
        command.setUsername("校园用户"); command.setEmail(" USER@Example.com "); command.setPassword("password");
        when(passwords.encode("password")).thenReturn("hash");
        when(mapper.insert(any(UserDO.class))).thenReturn(1);
        var result = service.register(command);
        assertTrue(result.getUserNo().matches("[1-9][0-9]{9}"));
        ArgumentCaptor<UserDO> saved = ArgumentCaptor.forClass(UserDO.class);
        verify(mapper).insert(saved.capture());
        assertEquals("user@example.com", saved.getValue().getEmail());
        assertEquals("hash", saved.getValue().getPasswordHash());
    }
}
