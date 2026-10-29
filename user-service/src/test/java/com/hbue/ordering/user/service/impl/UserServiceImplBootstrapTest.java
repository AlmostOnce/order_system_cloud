package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户账号引导业务测试。
 *
 * @author order-system
 * @date 2026-10-13
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplBootstrapTest {

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

    @Test
    void shouldCreateSystemAdminWhenAccountAbsent() {
        when(userMapper.selectByUsername("admin")).thenReturn(null);
        when(userMapper.selectByEmail("100000001@qq.com")).thenReturn(null);
        when(passwordEncoder.encode("admin_password_dev_2026"))
                .thenReturn("$2a$10$encoded-hash");
        when(userMapper.insert(any(UserDO.class))).thenReturn(1);

        boolean created = userService.bootstrapSystemAdmin(
                "admin",
                "100000001@qq.com",
                "admin_password_dev_2026"
        );

        assertTrue(created);
        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("admin", captor.getValue().getUsername());
        assertEquals("100000001@qq.com", captor.getValue().getEmail());
        assertEquals("SYSTEM_ADMIN", captor.getValue().getRoleCode());
        assertEquals(Integer.valueOf(1), captor.getValue().getStatus());
        assertEquals(Integer.valueOf(0), captor.getValue().getVersion());
        assertEquals("$2a$10$encoded-hash", captor.getValue().getPasswordHash());

        // 系统管理员不需要业务数据范围。
        verify(userDataScopeService, never()).grantPlatformScope(any());
    }

    @Test
    void shouldCreateGeneralManagerAndGrantPlatformScope() {
        when(userMapper.selectByUsername("manager")).thenReturn(null);
        when(userMapper.selectByEmail("100000002@qq.com")).thenReturn(null);
        when(passwordEncoder.encode("manager_password_dev_2026"))
                .thenReturn("$2a$10$manager-hash");
        // 模拟自增主键回填，让范围写入拿到真实用户 ID。
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            UserDO inserted = invocation.getArgument(0);
            inserted.setUserId(42L);
            return 1;
        });

        boolean created = userService.bootstrapGeneralManager(
                "manager",
                "100000002@qq.com",
                "manager_password_dev_2026"
        );

        assertTrue(created);
        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("GENERAL_MANAGER", captor.getValue().getRoleCode());

        // 总经理必须同时获得全平台范围，才能作为授权链的根。
        verify(userDataScopeService).grantPlatformScope(42L);
    }

    @Test
    void shouldSkipAndNotOverwriteExistingRoleAccount() {
        UserDO existingUser = existingUser("admin", "SYSTEM_ADMIN", 1);
        when(userMapper.selectByUsername("admin")).thenReturn(existingUser);

        boolean created = userService.bootstrapSystemAdmin(
                "admin",
                "100000001@qq.com",
                "admin_password_dev_2026"
        );

        assertFalse(created);
        verify(userMapper, never()).insert(any(UserDO.class));
    }

    @Test
    void shouldRejectWhenAccountOccupiedByAnotherRole() {
        UserDO existingUser = existingUser("manager", "CUSTOMER", 1);
        when(userMapper.selectByUsername("manager")).thenReturn(existingUser);

        assertThrows(
                BusinessException.class,
                () -> userService.bootstrapGeneralManager(
                        "manager",
                        "100000002@qq.com",
                        "manager_password_dev_2026"
                )
        );

        verify(userMapper, never()).insert(any(UserDO.class));
        verify(userDataScopeService, never()).grantPlatformScope(anyLong());
    }

    @Test
    void shouldRejectWhenEmailOccupiedByAnotherAccount() {
        // 目标用户名可用，但邮箱已被另一个账号占用。
        when(userMapper.selectByUsername("manager")).thenReturn(null);
        when(userMapper.selectByEmail("100000002@qq.com"))
                .thenReturn(existingUser("gm01", "GENERAL_MANAGER", 1));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.bootstrapGeneralManager(
                        "manager",
                        "100000002@qq.com",
                        "manager_password_dev_2026"
                )
        );

        // 必须显式报错，不能让信任根账号静默缺失。
        assertEquals(
                CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code()
        );
        verify(userMapper, never()).insert(any(UserDO.class));
        verify(userDataScopeService, never()).grantPlatformScope(anyLong());
    }

    @Test
    void shouldNotGrantScopeWhenManagerAlreadyExists() {
        UserDO existingUser = existingUser("manager", "GENERAL_MANAGER", 1);
        when(userMapper.selectByUsername("manager")).thenReturn(existingUser);

        boolean created = userService.bootstrapGeneralManager(
                "manager",
                "100000002@qq.com",
                "manager_password_dev_2026"
        );

        assertFalse(created);
        verify(userMapper, never()).insert(any(UserDO.class));
        verify(userDataScopeService, never()).grantPlatformScope(anyLong());
    }

    @Test
    void shouldReturnFalseWhenConcurrentCreationConflicts() {
        when(userMapper.selectByUsername("manager")).thenReturn(null);
        when(userMapper.selectByEmail("100000002@qq.com")).thenReturn(null);
        when(userMapper.insert(any(UserDO.class)))
                .thenThrow(new DuplicateKeyException("uk_users_username"));

        boolean created = userService.bootstrapGeneralManager(
                "manager",
                "100000002@qq.com",
                "manager_password_dev_2026"
        );

        assertFalse(created);
        verify(userDataScopeService, never()).grantPlatformScope(anyLong());
    }

    @Test
    void shouldRejectBlankParameters() {
        assertFalse(userService.bootstrapSystemAdmin("", "", "admin_password_dev_2026"));
        assertFalse(userService.bootstrapSystemAdmin("admin", "100000001@qq.com", ""));
        assertFalse(userService.bootstrapGeneralManager(null, null, null));

        verify(userMapper, never()).selectByUsername(any());
        verify(userMapper, never()).selectByEmail(any());
        verify(userMapper, never()).insert(any(UserDO.class));
    }

    /**
     * 构造一个已存在用户，仅填充查重判定所需字段。
     *
     * @param username 用户名
     * @param roleCode 角色编码
     * @param status 状态
     * @return 用户持久化对象
     */
    private UserDO existingUser(String username, String roleCode, Integer status) {
        UserDO user = new UserDO();
        user.setUserId(9L);
        user.setUsername(username);
        user.setRoleCode(roleCode);
        user.setStatus(status);
        return user;
    }
}
