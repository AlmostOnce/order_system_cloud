package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.dto.request.PayPasswordSetRequest;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.PayPasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付密码业务测试：首次设置身份校验、修改校验、失败计数与锁定、
 * 锁定优先、Redis 不可用时 fail-closed。
 */
@ExtendWith(MockitoExtension.class)
class PayPasswordServiceImplTest {

    private static final Long USER_ID = 17L;
    private static final String LOGIN_PASSWORD = "login-pass";
    private static final String LOGIN_HASH = "login-hash";
    private static final String PAY_PASSWORD = "pay-pass-123456";
    private static final String FAIL_KEY = "user:pay-password:fail:17";
    private static final String LOCK_KEY = "user:pay-password:lock:17";
    private static final int MAX_FAILURES = 5;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RedisOperationsService redisOperationsService;

    private PayPasswordService payPasswordService;

    @BeforeEach
    void setUp() {
        payPasswordService = new PayPasswordServiceImpl(
                userMapper,
                passwordEncoder,
                redisOperationsService,
                MAX_FAILURES,
                900L,
                900L
        );
    }

    // ---------- 设置支付密码 ----------

    @Test
    void shouldRejectFirstSetWithoutLoginPassword() {
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user(null));

        PayPasswordSetRequest request = setRequest(null, null, PAY_PASSWORD);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.setPayPassword(USER_ID, request));

        assertEquals(
                CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code()
        );
        verify(userMapper, never()).updatePayPassword(any(), anyString());
    }

    @Test
    void shouldRejectFirstSetWhenLoginPasswordWrong() {
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user(null));
        when(passwordEncoder.matches(LOGIN_PASSWORD, LOGIN_HASH))
                .thenReturn(false);

        PayPasswordSetRequest request = setRequest(
                LOGIN_PASSWORD, null, PAY_PASSWORD
        );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.setPayPassword(USER_ID, request));

        assertEquals("登录密码不正确", exception.getMessage());
        verify(userMapper, never()).updatePayPassword(any(), anyString());
    }

    @Test
    void shouldRejectPayPasswordSameAsLoginPassword() {
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user(null));
        when(passwordEncoder.matches(LOGIN_PASSWORD, LOGIN_HASH))
                .thenReturn(true);
        when(passwordEncoder.matches(PAY_PASSWORD, LOGIN_HASH))
                .thenReturn(true);

        PayPasswordSetRequest request = setRequest(
                LOGIN_PASSWORD, null, PAY_PASSWORD
        );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.setPayPassword(USER_ID, request));

        assertEquals("支付密码不得与登录密码相同", exception.getMessage());
        verify(userMapper, never()).updatePayPassword(any(), anyString());
    }

    @Test
    void shouldSetPayPasswordOnFirstSet() {
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user(null));
        when(passwordEncoder.matches(LOGIN_PASSWORD, LOGIN_HASH))
                .thenReturn(true);
        when(passwordEncoder.matches(PAY_PASSWORD, LOGIN_HASH))
                .thenReturn(false);
        when(passwordEncoder.encode(PAY_PASSWORD)).thenReturn("encoded-pay");

        payPasswordService.setPayPassword(
                USER_ID,
                setRequest(LOGIN_PASSWORD, null, PAY_PASSWORD)
        );

        verify(userMapper).updatePayPassword(USER_ID, "encoded-pay");
        // 设置成功后应清空失败计数。
        verify(redisOperationsService).delete(FAIL_KEY);
    }

    @Test
    void shouldRejectModifyWithoutOldPayPassword() {
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user("old-hash"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.setPayPassword(
                        USER_ID, setRequest(null, null, PAY_PASSWORD)
                ));

        assertEquals("修改支付密码需要提供原支付密码", exception.getMessage());
        verify(userMapper, never()).updatePayPassword(any(), anyString());
    }

    // ---------- 校验支付密码 ----------

    @Test
    void shouldPassVerificationAndClearFailures() {
        when(redisOperationsService.hasKey(LOCK_KEY)).thenReturn(false);
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user("pay-hash"));
        when(passwordEncoder.matches(PAY_PASSWORD, "pay-hash")).thenReturn(true);

        payPasswordService.verifyPayPassword(USER_ID, PAY_PASSWORD);

        verify(redisOperationsService).delete(FAIL_KEY);
    }

    @Test
    void shouldRejectWhenPayPasswordNotSet() {
        when(redisOperationsService.hasKey(LOCK_KEY)).thenReturn(false);
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user(null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.verifyPayPassword(USER_ID, PAY_PASSWORD));

        assertEquals(
                CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldCountFailureAndReportRemainingAttempts() {
        when(redisOperationsService.hasKey(LOCK_KEY)).thenReturn(false);
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user("pay-hash"));
        when(passwordEncoder.matches(PAY_PASSWORD, "pay-hash"))
                .thenReturn(false);
        // 非首次计数：不应重置 TTL。
        when(redisOperationsService.increment(FAIL_KEY)).thenReturn(2L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.verifyPayPassword(USER_ID, PAY_PASSWORD));

        assertEquals(
                CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code()
        );
        assertEquals("支付密码错误，还可尝试 3 次", exception.getMessage());
        verify(redisOperationsService, never()).expire(anyString(), any());
    }

    @Test
    void shouldSetFailureTtlOnlyOnFirstCount() {
        when(redisOperationsService.hasKey(LOCK_KEY)).thenReturn(false);
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user("pay-hash"));
        when(passwordEncoder.matches(PAY_PASSWORD, "pay-hash"))
                .thenReturn(false);
        when(redisOperationsService.increment(FAIL_KEY)).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> payPasswordService.verifyPayPassword(USER_ID, PAY_PASSWORD));

        verify(redisOperationsService).expire(
                FAIL_KEY, Duration.ofSeconds(900L)
        );
    }

    @Test
    void shouldLockAccountAfterReachingMaxFailures() {
        when(redisOperationsService.hasKey(LOCK_KEY)).thenReturn(false);
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user("pay-hash"));
        when(passwordEncoder.matches(PAY_PASSWORD, "pay-hash"))
                .thenReturn(false);
        when(redisOperationsService.increment(FAIL_KEY))
                .thenReturn((long) MAX_FAILURES);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.verifyPayPassword(USER_ID, PAY_PASSWORD));

        assertEquals(
                CommonErrorCode.TOO_MANY_REQUESTS.code(),
                exception.getErrorCode().code()
        );
        verify(redisOperationsService).setValue(
                LOCK_KEY, "1", Duration.ofSeconds(900L)
        );
    }

    @Test
    void shouldRejectWhenLockedWithoutComparingPassword() {
        when(redisOperationsService.hasKey(LOCK_KEY)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.verifyPayPassword(USER_ID, PAY_PASSWORD));

        assertEquals(
                CommonErrorCode.TOO_MANY_REQUESTS.code(),
                exception.getErrorCode().code()
        );
        verify(userMapper, never()).selectBalanceById(any());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldFailClosedWhenRedisUnavailable() {
        when(redisOperationsService.hasKey(LOCK_KEY))
                .thenThrow(new RuntimeException("redis down"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> payPasswordService.verifyPayPassword(USER_ID, PAY_PASSWORD));

        assertEquals(
                CommonErrorCode.SERVICE_UNAVAILABLE.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldReportPayPasswordSetState() {
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user("pay-hash"));

        assertEquals(
                true,
                payPasswordService.isPayPasswordSet(USER_ID)
        );
    }

    // ---------- 辅助方法 ----------

    /**
     * 构建启用状态用户。
     */
    private UserDO user(String payPasswordHash) {
        UserDO user = new UserDO();
        user.setUserId(USER_ID);
        user.setStatus(1);
        user.setPasswordHash(LOGIN_HASH);
        user.setPayPasswordHash(payPasswordHash);
        return user;
    }

    /**
     * 构建支付密码设置请求。
     */
    private PayPasswordSetRequest setRequest(
            String loginPassword,
            String oldPayPassword,
            String newPayPassword
    ) {
        PayPasswordSetRequest request = new PayPasswordSetRequest();
        request.setLoginPassword(loginPassword);
        request.setOldPayPassword(oldPayPassword);
        request.setNewPayPassword(newPayPassword);
        return request;
    }
}
