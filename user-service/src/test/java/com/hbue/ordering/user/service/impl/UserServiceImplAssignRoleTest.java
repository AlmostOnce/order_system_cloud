package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.user.api.vo.UserBasicVO;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户角色分配业务测试。
 *
 * <p>覆盖硬约束①：禁止修改本人角色编码（自我提权），以及角色白名单、
 * 目标用户存在性校验。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplAssignRoleTest {

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
                auditLogger,
                org.mockito.Mockito.mock(com.hbue.ordering.user.service.EmailVerification.class)
        );
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    /**
     * 系统管理员不得修改本人角色编码。
     */
    @Test
    void shouldRejectChangingOwnRole() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.assignRole(
                        OPERATOR_ID,
                        OPERATOR_ID,
                        "GENERAL_MANAGER"
                )
        );

        assertEquals(CommonErrorCode.FORBIDDEN.code(),
                exception.getErrorCode().code());
        verify(userMapper, never()).selectAvailableById(any(), any());
    }

    /**
     * 角色编码必须在白名单内。
     */
    @Test
    void shouldRejectUnknownRoleCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.assignRole(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        "SUPER_ADMIN"
                )
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code());
        verify(userMapper, never()).selectAvailableById(any(), any());
    }

    /**
     * 目标用户不存在时返回资源不存在。
     */
    @Test
    void shouldReturnNotFoundWhenTargetUserMissing() {
        when(userMapper.selectAvailableById(TARGET_USER_ID, 1))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.assignRole(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        "STALL_MANAGER"
                )
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code());
    }

    /**
     * 合法角色分配只写 role_code 并记录审计。
     */
    @Test
    void shouldAssignWhitelistedRoleAndRecordAudit() {
        UserDO target = new UserDO();
        target.setUserId(TARGET_USER_ID);
        target.setUsername("staff");
        target.setRoleCode("CUSTOMER");
        when(userMapper.selectAvailableById(TARGET_USER_ID, 1))
                .thenReturn(target);

        UserBasicVO result = userService.assignRole(
                OPERATOR_ID,
                TARGET_USER_ID,
                "STALL_MANAGER"
        );

        assertEquals(TARGET_USER_ID, result.getUserId());
        assertEquals("STALL_MANAGER", result.getRoleCode());
        verify(userMapper).update(eq(null), any());
        verify(auditLogger).record(
                eq(OPERATOR_ID),
                eq("USER_ROLE_ASSIGN"),
                eq("USER"),
                eq(TARGET_USER_ID),
                eq("CUSTOMER"),
                eq("STALL_MANAGER"),
                eq(true)
        );
    }
}
