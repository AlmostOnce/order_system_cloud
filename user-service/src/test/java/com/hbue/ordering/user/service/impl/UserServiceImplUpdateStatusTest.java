package com.hbue.ordering.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户账号启用/禁用业务测试。
 *
 * <p>覆盖硬约束③：禁止禁用最后一个处于启用状态的 {@code SYSTEM_ADMIN}（对外 409），
 * 以及目标状态合法性、目标用户存在性、状态未变时的幂等与审计。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplUpdateStatusTest {

    private static final Long OPERATOR_ID = 1L;
    private static final Long TARGET_USER_ID = 9L;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserDataScopeService userDataScopeService;

    @Mock
    private ManagementAuditLogger auditLogger;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                passwordEncoder,
                userDataScopeService,
                auditLogger
        );
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    /**
     * 目标状态不是 0/1 时返回参数错误，且不查询数据库。
     */
    @Test
    void shouldRejectInvalidStatus() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUserStatus(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        2
                )
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code());
        verify(userMapper, never()).selectNotDeletedById(any());
    }

    /**
     * 目标用户不存在（或已删除）时返回资源不存在。
     */
    @Test
    void shouldReturnNotFoundWhenTargetUserMissing() {
        when(userMapper.selectNotDeletedById(TARGET_USER_ID))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUserStatus(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        0
                )
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code());
        verify(userMapper, never()).update(any(), any());
    }

    /**
     * 禁用目标用户与当前状态一致时幂等返回，不写库也不审计。
     */
    @Test
    void shouldBeIdempotentWhenStatusUnchanged() {
        UserDO target = new UserDO();
        target.setUserId(TARGET_USER_ID);
        target.setRoleCode("CUSTOMER");
        target.setStatus(1);
        when(userMapper.selectNotDeletedById(TARGET_USER_ID))
                .thenReturn(target);

        userService.updateUserStatus(OPERATOR_ID, TARGET_USER_ID, 1);

        verify(userMapper, never()).update(any(), any());
        verify(auditLogger, never()).record(
                any(), any(), any(), any(), any(), any(), anyBoolean()
        );
    }

    /**
     * 硬约束③：禁用最后一个启用的系统管理员时返回状态冲突。
     */
    @Test
    void shouldRejectDisablingLastEnabledSystemAdmin() {
        UserDO target = new UserDO();
        target.setUserId(TARGET_USER_ID);
        target.setRoleCode("SYSTEM_ADMIN");
        target.setStatus(1);
        when(userMapper.selectNotDeletedById(TARGET_USER_ID))
                .thenReturn(target);
        when(userMapper.selectCount(any(Wrapper.class)))
                .thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUserStatus(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        0
                )
        );

        assertEquals(CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code());
        verify(userMapper, never()).update(any(), any());
        verify(auditLogger, never()).record(
                any(), any(), any(), any(), any(), any(), anyBoolean()
        );
    }

    /**
     * 存在第二个启用的系统管理员时允许禁用，并记录审计。
     */
    @Test
    void shouldDisableAdminWhenAnotherEnabledAdminExists() {
        UserDO target = new UserDO();
        target.setUserId(TARGET_USER_ID);
        target.setRoleCode("SYSTEM_ADMIN");
        target.setStatus(1);
        when(userMapper.selectNotDeletedById(TARGET_USER_ID))
                .thenReturn(target);
        when(userMapper.selectCount(any(Wrapper.class)))
                .thenReturn(2L);

        userService.updateUserStatus(OPERATOR_ID, TARGET_USER_ID, 0);

        verify(userMapper).update(eq(null), any());
        verify(auditLogger).record(
                eq(OPERATOR_ID),
                eq("USER_STATUS_UPDATE"),
                eq("USER"),
                eq(TARGET_USER_ID),
                eq("1"),
                eq("0"),
                eq(true)
        );
    }

    /**
     * 重新启用已禁用账号时成功，并记录审计。
     */
    @Test
    void shouldEnableDisabledAccount() {
        UserDO target = new UserDO();
        target.setUserId(TARGET_USER_ID);
        target.setRoleCode("CUSTOMER");
        target.setStatus(0);
        when(userMapper.selectNotDeletedById(TARGET_USER_ID))
                .thenReturn(target);

        userService.updateUserStatus(OPERATOR_ID, TARGET_USER_ID, 1);

        verify(userMapper).update(eq(null), any());
        verify(auditLogger).record(
                eq(OPERATOR_ID),
                eq("USER_STATUS_UPDATE"),
                eq("USER"),
                eq(TARGET_USER_ID),
                eq("0"),
                eq("1"),
                eq(true)
        );
        // 启用普通账号无需统计系统管理员数量。
        verify(userMapper, never()).selectCount(any(Wrapper.class));
    }
}
