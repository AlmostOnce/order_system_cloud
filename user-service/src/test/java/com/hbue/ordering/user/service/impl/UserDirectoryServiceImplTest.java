package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import com.hbue.ordering.user.vo.AdminUserPageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端账号检索业务测试。
 *
 * <p>覆盖三类关键不变量：角色/状态/游标参数校验必须拒绝非法输入；
 * 游标分页必须多取一条判断 hasMore 且不越界返回；数据范围候选必须按操作者
 * 快照收敛，餐厅经理不能借候选接口枚举全平台账号。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceImplTest {

    private static final Long OPERATOR_ID = 5L;

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserDataScopeService userDataScopeService;

    @Mock
    private WindowInternalClient windowInternalClient;

    private UserDirectoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDirectoryServiceImpl(
                userMapper,
                userDataScopeService,
                windowInternalClient,
                INTERNAL_TOKEN
        );
    }

    /**
     * 账号目录应把筛选条件规范化后下传到 Mapper，并按多取一条计算分页。
     */
    @Test
    void shouldPageDirectoryAndExposeMaskedFields() {
        when(userMapper.selectAdminDirectoryPage(
                eq("%ali%"),
                eq("ali"),
                eq("STALL_MANAGER"),
                eq(1),
                isNull(),
                eq(3)
        )).thenReturn(List.of(
                user(9L, "alice@example.com"),
                user(8L, "bob@example.com"),
                user(7L, "carol@example.com")
        ));

        AdminUserPageVO page = service.listDirectory(
                OPERATOR_ID,
                "  ali  ",
                "STALL_MANAGER",
                1,
                null,
                2
        );

        assertEquals(2, page.items().size());
        assertTrue(page.hasMore());
        assertEquals("8", page.nextCursor());
        assertEquals("al***@example.com", page.items().getFirst().email());
        assertEquals(1, page.items().getFirst().status());
    }

    /**
     * 角色编码必须在白名单内，非法编码不触发数据库查询。
     */
    @Test
    void shouldRejectUnknownRoleCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.listDirectory(OPERATOR_ID, null, "SUPER_ADMIN", null, null, 20)
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code());
        verify(userMapper, never()).selectAdminDirectoryPage(
                any(), any(), any(), any(), any(), anyInt());
    }

    /**
     * 状态只接受 0 或 1。
     */
    @Test
    void shouldRejectInvalidStatus() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.listDirectory(OPERATOR_ID, null, null, 2, null, 20)
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code());
    }

    /**
     * 游标必须是正整数格式，防止把任意字符串拼进查询。
     */
    @Test
    void shouldRejectInvalidCursor() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.listDirectory(OPERATOR_ID, null, null, null, "abc", 20)
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code());
    }

    /**
     * 关键词中的通配符必须被转义，避免扩大检索范围。
     */
    @Test
    void shouldEscapeLikeWildcardsInKeyword() {
        when(userMapper.selectAdminDirectoryPage(
                anyString(), anyString(), isNull(), isNull(), isNull(), anyInt()
        )).thenReturn(List.of());

        service.listDirectory(OPERATOR_ID, "a%b_c!", null, null, null, 20);

        verify(userMapper).selectAdminDirectoryPage(
                eq("%a!%b!_c!!%"),
                eq("a%b_c!"),
                isNull(),
                isNull(),
                isNull(),
                eq(21)
        );
    }

    /**
     * 总经理持有全平台范围时可检索全平台账号，不应调用商品服务展开档口。
     */
    @Test
    void shouldSearchAllUsersForPlatformScope() {
        when(userDataScopeService.loadSnapshot(OPERATOR_ID))
                .thenReturn(new UserScopeSnapshot(
                        OPERATOR_ID, true, Set.of(), Set.of()));
        when(userMapper.selectAdminDirectoryPage(
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(21)
        )).thenReturn(List.of());

        AdminUserPageVO page = service.listScopeCandidates(
                OPERATOR_ID, null, null, 20);

        assertFalse(page.hasMore());
        verify(windowInternalClient, never())
                .getWindowIdsByRestaurants(anyList(), anyString());
        verify(userMapper, never()).selectScopedUserCandidates(
                anyList(), anyList(), any(), any(), any(), anyInt());
    }

    /**
     * 餐厅经理的餐厅范围应展开为档口集合，并只查询已在该范围内的账号。
     */
    @Test
    void shouldRestrictCandidatesToManagerScope() {
        when(userDataScopeService.loadSnapshot(OPERATOR_ID))
                .thenReturn(new UserScopeSnapshot(
                        OPERATOR_ID, false, Set.of(9L), Set.of(1L)));
        when(windowInternalClient.getWindowIdsByRestaurants(
                List.of(1L), INTERNAL_TOKEN
        )).thenReturn(ApiResponse.success(List.of(7L)));
        when(userMapper.selectScopedUserCandidates(
                eq(List.of(1L)),
                eq(List.of(9L, 7L)),
                isNull(),
                isNull(),
                isNull(),
                eq(21)
        )).thenReturn(List.of());

        AdminUserPageVO page = service.listScopeCandidates(
                OPERATOR_ID, null, null, 20);

        assertFalse(page.hasMore());
        verify(userMapper).selectScopedUserCandidates(
                eq(List.of(1L)),
                eq(List.of(9L, 7L)),
                isNull(),
                isNull(),
                isNull(),
                eq(21)
        );
    }

    /**
     * 经理没有任何范围时返回空页，不执行范围查询。
     */
    @Test
    void shouldReturnEmptyPageWhenManagerHasNoScope() {
        when(userDataScopeService.loadSnapshot(OPERATOR_ID))
                .thenReturn(new UserScopeSnapshot(
                        OPERATOR_ID, false, Set.of(), Set.of()));

        AdminUserPageVO page = service.listScopeCandidates(
                OPERATOR_ID, null, null, 20);

        assertEquals(0, page.items().size());
        assertFalse(page.hasMore());
        verify(userMapper, never()).selectScopedUserCandidates(
                anyList(), anyList(), any(), any(), any(), anyInt());
    }

    /**
     * 商品服务不可用时失败关闭，不返回可能越权的候选。
     */
    @Test
    void shouldFailClosedWhenProductServiceUnavailable() {
        when(userDataScopeService.loadSnapshot(OPERATOR_ID))
                .thenReturn(new UserScopeSnapshot(
                        OPERATOR_ID, false, Set.of(), Set.of(1L)));
        when(windowInternalClient.getWindowIdsByRestaurants(
                List.of(1L), INTERNAL_TOKEN
        )).thenReturn(ApiResponse.failure(
                CommonErrorCode.SERVICE_UNAVAILABLE));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.listScopeCandidates(OPERATOR_ID, null, null, 20)
        );

        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.code(),
                exception.getErrorCode().code());
        verify(userMapper, never()).selectScopedUserCandidates(
                anyList(), anyList(), any(), any(), any(), anyInt());
    }

    /**
     * 构造用户记录，供分页与脱敏断言使用。
     */
    private UserDO user(Long userId, String email) {
        UserDO user = new UserDO();
        user.setUserId(userId);
        user.setUserUuid("uuid-" + userId);
        user.setUserNo("1000" + userId);
        user.setUsername("user-" + userId);
        user.setRoleCode("CUSTOMER");
        user.setStatus(1);
        user.setEmail(email);
        user.setEmailVerifiedAt(java.time.LocalDateTime.now());
        user.setCreatedAt(java.time.LocalDateTime.now());
        return user;
    }
}
