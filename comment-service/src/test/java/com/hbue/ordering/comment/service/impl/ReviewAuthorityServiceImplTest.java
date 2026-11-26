package com.hbue.ordering.comment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.client.CommentScopeClient;
import com.hbue.ordering.comment.client.CommentWindowClient;
import com.hbue.ordering.comment.client.vo.CommentUserScopeVO;
import com.hbue.ordering.comment.client.vo.CommentWindowInfoVO;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 评价修改与删除的鉴权回归。
 *
 * <p>只验证"谁能改哪条评价"的判定顺序与拒绝语义，不覆盖远程调用的重试与缓存细节；
 * 测试不访问真实用户服务、商品服务或 Redis。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
class ReviewAuthorityServiceImplTest {

    /**
     * 用户数据范围客户端。
     */
    private final CommentScopeClient scopeClient = mock(CommentScopeClient.class);

    /**
     * 档口归属客户端。
     */
    private final CommentWindowClient windowClient = mock(CommentWindowClient.class);

    /**
     * Redis 操作服务。
     */
    private final RedisOperationsService redis = mock(RedisOperationsService.class);

    /**
     * 被测服务；内部令牌固定为测试值。
     */
    private final ReviewAuthorityServiceImpl authority = new ReviewAuthorityServiceImpl(
            scopeClient, windowClient, redis, new ObjectMapper(), "internal-token");

    /**
     * 作者本人可以修改自己的评价，不需要查询任何数据范围。
     */
    @Test
    void authorCanManageOwnReviewWithoutScopeLookup() {
        assertDoesNotThrow(() -> authority.requireManageable(9L, List.of("CUSTOMER"), 9L, 2L));
        verifyNoInteractions(scopeClient, windowClient, redis);
    }

    /**
     * 普通学生修改他人评价直接拒绝，且不触发远程范围查询。
     */
    @Test
    void customerCannotManageOthersReview() {
        assertThrows(BusinessException.class,
                () -> authority.requireManageable(9L, List.of("CUSTOMER"), 10L, 2L));
        verifyNoInteractions(scopeClient, windowClient);
    }

    /**
     * 系统管理员按业务约定可以操作任意评价，即使没有任何数据范围。
     */
    @Test
    void systemAdminCanManageAnyReview() {
        assertDoesNotThrow(() -> authority.requireManageable(9L, List.of("SYSTEM_ADMIN"), 10L, 2L));
        verifyNoInteractions(scopeClient, windowClient);
    }

    /**
     * 餐厅经理持有该档口所属餐厅的范围时可以操作。
     */
    @Test
    void restaurantManagerCoveredByRestaurantScopeCanManage() {
        when(redis.getValue(anyString())).thenReturn(null);
        when(scopeClient.getScopes(9L, "internal-token")).thenReturn(ApiResponse.success(
                new CommentUserScopeVO(false, List.of(), List.of(3L))));
        when(windowClient.getWindow(2L, "internal-token")).thenReturn(ApiResponse.success(
                new CommentWindowInfoVO(2L, 3L)));

        assertDoesNotThrow(() -> authority.requireManageable(9L, List.of("RESTAURANT_MANAGER"), 10L, 2L));
        verify(redis).setValue(anyString(), anyString(), any());
    }

    /**
     * 餐厅经理的范围不覆盖该档口时拒绝。
     */
    @Test
    void restaurantManagerOutsideScopeIsRejected() {
        when(redis.getValue(anyString())).thenReturn(null);
        when(scopeClient.getScopes(9L, "internal-token")).thenReturn(ApiResponse.success(
                new CommentUserScopeVO(false, List.of(), List.of(4L))));
        when(windowClient.getWindow(2L, "internal-token")).thenReturn(ApiResponse.success(
                new CommentWindowInfoVO(2L, 3L)));

        assertThrows(BusinessException.class,
                () -> authority.requireManageable(9L, List.of("RESTAURANT_MANAGER"), 10L, 2L));
    }

    /**
     * 总经理按角色定位覆盖全平台，不受档口范围限制，也不触发范围查询。
     */
    @Test
    void generalManagerCanManageAnyReview() {
        assertDoesNotThrow(() -> authority.requireManageable(9L, List.of("GENERAL_MANAGER"), 10L, 2L));
        verifyNoInteractions(scopeClient, windowClient, redis);
    }

    /**
     * 档口负责人持有该档口直接授权时可以操作，无需查询档口归属。
     */
    @Test
    void stallManagerCoveredByWindowScopeCanManage() {
        when(redis.getValue(anyString())).thenReturn(null);
        when(scopeClient.getScopes(9L, "internal-token")).thenReturn(ApiResponse.success(
                new CommentUserScopeVO(false, List.of(2L), List.of())));

        assertDoesNotThrow(() -> authority.requireManageable(9L, List.of("STALL_MANAGER"), 10L, 2L));
        verify(windowClient, never()).getWindow(anyLong(), anyString());
    }
}
