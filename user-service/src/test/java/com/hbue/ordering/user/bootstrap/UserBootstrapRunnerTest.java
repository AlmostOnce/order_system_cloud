package com.hbue.ordering.user.bootstrap;

import com.hbue.ordering.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户账号引导测试。
 *
 * @author order-system
 * @date 2026-10-13
 */
@ExtendWith(MockitoExtension.class)
class UserBootstrapRunnerTest {

    @Mock
    private UserService userService;

    private UserBootstrapRunner runner(
            boolean enabled,
            String adminUsername,
            String adminEmail,
            String adminPassword,
            String managerUsername,
            String managerEmail,
            String managerPassword
    ) {
        return new UserBootstrapRunner(
                userService,
                enabled,
                adminUsername,
                adminEmail,
                adminPassword,
                managerUsername,
                managerEmail,
                managerPassword
        );
    }

    @Test
    void shouldSkipEverythingWhenDisabled() {
        runner(false, "", "", "", "", "", "").run(null);

        verifyNoInteractions(userService);
    }

    @Test
    void shouldFailWhenEnabledButConfigMissing() {
        UserBootstrapRunner bootstrapRunner = runner(
                true,
                "",
                "",
                "",
                "manager",
                "100000002@qq.com",
                "manager_pass_2026"
        );

        assertThrows(IllegalStateException.class, () -> bootstrapRunner.run(null));
        verifyNoInteractions(userService);
    }

    @Test
    void shouldRejectPasswordOutsideTwelveToSixtyFour() {
        UserBootstrapRunner bootstrapRunner = runner(
                true,
                "admin",
                "100000001@qq.com",
                "admin_pass_2026",
                "manager",
                "100000002@qq.com",
                "too_short"
        );

        assertThrows(IllegalStateException.class, () -> bootstrapRunner.run(null));
        verifyNoInteractions(userService);
    }

    @Test
    void shouldRejectInvalidEmailFormat() {
        UserBootstrapRunner bootstrapRunner = runner(
                true,
                "admin",
                "not-an-email",
                "admin_pass_2026",
                "manager",
                "100000002@qq.com",
                "manager_pass_2026"
        );

        assertThrows(IllegalStateException.class, () -> bootstrapRunner.run(null));
        verifyNoInteractions(userService);
    }

    @Test
    void shouldCreateBothAccountsWhenConfigured() {
        when(userService.bootstrapSystemAdmin(
                "admin",
                "100000001@qq.com",
                "admin_pass_2026"
        )).thenReturn(true);
        when(userService.bootstrapGeneralManager(
                "manager",
                "100000002@qq.com",
                "manager_pass_2026"
        )).thenReturn(true);

        runner(
                true,
                "admin",
                "100000001@qq.com",
                "admin_pass_2026",
                "manager",
                "100000002@qq.com",
                "manager_pass_2026"
        ).run(null);

        verify(userService).bootstrapSystemAdmin(
                "admin",
                "100000001@qq.com",
                "admin_pass_2026"
        );
        verify(userService).bootstrapGeneralManager(
                "manager",
                "100000002@qq.com",
                "manager_pass_2026"
        );
    }

    @Test
    void shouldNotThrowWhenAccountsAlreadyExist() {
        when(userService.bootstrapSystemAdmin(
                "admin",
                "100000001@qq.com",
                "admin_pass_2026"
        )).thenReturn(false);
        when(userService.bootstrapGeneralManager(
                "manager",
                "100000002@qq.com",
                "manager_pass_2026"
        )).thenReturn(false);

        assertDoesNotThrow(() -> runner(
                true,
                "admin",
                "100000001@qq.com",
                "admin_pass_2026",
                "manager",
                "100000002@qq.com",
                "manager_pass_2026"
        ).run(null));

        verify(userService, never()).register(org.mockito.ArgumentMatchers.any());
    }
}
