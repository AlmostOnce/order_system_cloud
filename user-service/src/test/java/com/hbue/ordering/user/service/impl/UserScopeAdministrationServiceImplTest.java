package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.product.api.vo.RestaurantInfoVO;
import com.hbue.ordering.product.api.vo.WindowInfoVO;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.user.dto.request.UserScopeSaveRequest;
import com.hbue.ordering.user.model.UserDataScopeDO;
import com.hbue.ordering.user.service.UserDataScopeService;
import com.hbue.ordering.user.service.UserService;
import com.hbue.ordering.user.vo.UserScopeAdminVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据范围授予业务测试。
 *
 * <p>覆盖设计文档验收表中的关键不变量：不得对本人授予、不得越出自身范围、
 * 引用对象必须真实存在、全平台范围只有持有全平台范围的授予者才能继续授予。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@ExtendWith(MockitoExtension.class)
class UserScopeAdministrationServiceImplTest {

    /** 测试用服务间共享密钥。 */
    private static final String INTERNAL_TOKEN = "test-internal-token";

    private static final Long OPERATOR_ID = 5L;
    private static final Long TARGET_USER_ID = 9L;

    @Mock
    private UserDataScopeService userDataScopeService;

    @Mock
    private UserService userService;

    @Mock
    private WindowInternalClient windowInternalClient;

    @Mock
    private ManagementAuditLogger auditLogger;

    private UserScopeAdministrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserScopeAdministrationServiceImpl(
                userDataScopeService,
                userService,
                windowInternalClient,
                auditLogger,
                INTERNAL_TOKEN
        );
    }

    /**
     * 不得对本人授予或撤销范围（自授权禁令）。
     */
    @Test
    void shouldRejectGrantingToSelf() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.replaceScopes(
                        OPERATOR_ID,
                        OPERATOR_ID,
                        request("RESTAURANT", 1L)
                )
        );

        assertEquals(CommonErrorCode.FORBIDDEN.code(),
                exception.getErrorCode().code());
        verify(userDataScopeService, never()).replaceScopes(any(), anyList());
    }

    /**
     * 授予者不得越出自身范围授予。
     */
    @Test
    void shouldRejectGrantBeyondOperatorScope() {
        stubTargetUser();
        // 操作者只持有餐厅 1 的范围，却试图授予餐厅 2。
        when(userDataScopeService.loadSnapshot(OPERATOR_ID)).thenReturn(
                snapshot(false, Set.of(1L))
        );
        when(windowInternalClient.getRestaurant(2L, INTERNAL_TOKEN))
                .thenReturn(ApiResponse.success(restaurant(2L)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.replaceScopes(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        request("RESTAURANT", 2L)
                )
        );

        assertEquals(CommonErrorCode.FORBIDDEN.code(),
                exception.getErrorCode().code());
        verify(userDataScopeService, never()).replaceScopes(any(), anyList());
    }

    /**
     * 全平台范围只有本身持有全平台范围的授予者才能继续授予。
     */
    @Test
    void shouldRejectPlatformGrantWithoutPlatformScope() {
        stubTargetUser();
        when(userDataScopeService.loadSnapshot(OPERATOR_ID)).thenReturn(
                snapshot(false, Set.of(1L))
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.replaceScopes(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        request("PLATFORM", null)
                )
        );

        assertEquals(CommonErrorCode.FORBIDDEN.code(),
                exception.getErrorCode().code());
    }

    /**
     * 未知范围类型返回参数错误。
     */
    @Test
    void shouldRejectUnknownScopeType() {
        stubTargetUser();
        when(userDataScopeService.loadSnapshot(OPERATOR_ID)).thenReturn(
                snapshot(true, Set.of())
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.replaceScopes(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        request("GALAXY", 1L)
                )
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code());
    }

    /**
     * 引用对象不存在时返回参数错误，拒绝写入。
     */
    @Test
    void shouldRejectNonexistentWindowReference() {
        stubTargetUser();
        when(userDataScopeService.loadSnapshot(OPERATOR_ID)).thenReturn(
                snapshot(false, Set.of(1L))
        );
        when(windowInternalClient.getWindow(77L, INTERNAL_TOKEN))
                .thenReturn(ApiResponse.success(null));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.replaceScopes(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        request("WINDOW", 77L)
                )
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code());
    }

    /**
     * product-service 不可用时返回服务不可用。
     */
    @Test
    void shouldReturnServiceUnavailableWhenProductServiceDown() {
        stubTargetUser();
        when(userDataScopeService.loadSnapshot(OPERATOR_ID)).thenReturn(
                snapshot(false, Set.of(1L))
        );
        when(windowInternalClient.getWindow(7L, INTERNAL_TOKEN))
                .thenReturn(ApiResponse.failure(
                        CommonErrorCode.SERVICE_UNAVAILABLE
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.replaceScopes(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        request("WINDOW", 7L)
                )
        );

        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.code(),
                exception.getErrorCode().code());
    }

    /**
     * 目标用户不存在时返回资源不存在。
     */
    @Test
    void shouldRejectWhenTargetUserMissing() {
        when(userService.getUserById(TARGET_USER_ID)).thenThrow(
                new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.replaceScopes(
                        OPERATOR_ID,
                        TARGET_USER_ID,
                        request("RESTAURANT", 1L)
                )
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code());
    }

    /**
     * 授予本餐厅下属档口范围成功，并记录审计。
     */
    @Test
    void shouldGrantWindowWithinOperatorRestaurant() {
        stubTargetUser();
        when(userDataScopeService.loadSnapshot(OPERATOR_ID)).thenReturn(
                snapshot(false, Set.of(1L))
        );
        when(windowInternalClient.getWindow(7L, INTERNAL_TOKEN))
                .thenReturn(ApiResponse.success(
                        new WindowInfoVO(7L, 1L, 1)
                ));
        UserDataScopeDO persisted = scope(100L, "WINDOW", 7L);
        when(userDataScopeService.listByUserId(TARGET_USER_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(persisted));

        List<UserScopeAdminVO> result = service.replaceScopes(
                OPERATOR_ID,
                TARGET_USER_ID,
                request("WINDOW", 7L)
        );

        assertEquals(1, result.size());
        assertEquals(100L, result.getFirst().getScopeId());
        assertEquals("WINDOW", result.getFirst().getScopeType());
        assertEquals(7L, result.getFirst().getScopeRef());

        ArgumentCaptor<List<UserDataScopeDO>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(userDataScopeService).replaceScopes(
                eq(TARGET_USER_ID),
                captor.capture()
        );
        assertEquals(1, captor.getValue().size());
        assertEquals("WINDOW", captor.getValue().getFirst().getScopeType());
        assertEquals(7L, captor.getValue().getFirst().getScopeRef());

        verify(auditLogger).record(
                eq(OPERATOR_ID),
                eq("USER_SCOPE_REPLACE"),
                eq("USER"),
                eq(TARGET_USER_ID),
                any(),
                any(),
                eq(true)
        );
    }

    /**
     * 撤销时目标范围记录不存在返回资源不存在。
     */
    @Test
    void shouldRejectRevokingMissingScope() {
        stubTargetUser();
        when(userDataScopeService.listByUserId(TARGET_USER_ID))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.revokeScope(OPERATOR_ID, TARGET_USER_ID, 404L)
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code());
        verify(userDataScopeService, never()).revokeScope(any(), any());
    }

    /**
     * 构造单条范围的授予请求。
     */
    private UserScopeSaveRequest request(String scopeType, Long scopeRef) {
        UserScopeSaveRequest.ScopeItem item =
                new UserScopeSaveRequest.ScopeItem();
        item.setScopeType(scopeType);
        item.setScopeRef(scopeRef);

        UserScopeSaveRequest request = new UserScopeSaveRequest();
        request.setScopes(List.of(item));
        return request;
    }

    /**
     * 构造范围快照。
     */
    private UserScopeSnapshot snapshot(
            boolean platform,
            Set<Long> restaurantIds
    ) {
        return new UserScopeSnapshot(
                OPERATOR_ID,
                platform,
                Set.of(),
                restaurantIds
        );
    }

    /**
     * 构造餐厅内部视图。
     */
    private RestaurantInfoVO restaurant(Long restaurantId) {
        return RestaurantInfoVO.builder()
                .restaurantId(restaurantId)
                .restaurantCode("R-" + restaurantId)
                .restaurantName("测试餐厅")
                .status(1)
                .build();
    }

    /**
     * 构造数据范围持久化对象。
     */
    private UserDataScopeDO scope(Long scopeId, String type, Long ref) {
        UserDataScopeDO scope = new UserDataScopeDO();
        scope.setScopeId(scopeId);
        scope.setUserId(TARGET_USER_ID);
        scope.setScopeType(type);
        scope.setScopeRef(ref);
        return scope;
    }

    /**
     * 让目标用户存在。
     */
    private void stubTargetUser() {
        when(userService.getUserById(TARGET_USER_ID)).thenReturn(
                UserBasicVO.builder()
                        .userId(TARGET_USER_ID)
                        .username("target-user")
                        .roleCode("CUSTOMER")
                        .build()
        );
    }
}
