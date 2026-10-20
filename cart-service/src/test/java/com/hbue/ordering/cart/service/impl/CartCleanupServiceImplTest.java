package com.hbue.ordering.cart.service.impl;

import com.hbue.ordering.cart.dto.request.CartCleanupRequest;
import com.hbue.ordering.cart.dto.request.CartSubmittedItemRequest;
import com.hbue.ordering.cart.repository.CartRedisRepository;
import com.hbue.ordering.cart.vo.CartCleanupVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.client.OrderServiceClient;
import com.hbue.ordering.order.api.vo.OrderCartSnapshotVO;
import com.hbue.ordering.order.api.vo.OrderPurchasedItemVO;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 清理编排的安全边界：没有真实订单证明不得删除购物车，依赖故障也不得误删。
 * 外部订单 HTTP 与 Redis 使用替身；原子删除行为由真实 Redis 集成测试覆盖。
 */
class CartCleanupServiceImplTest {
    /** 外部订单服务替身。 */
    private OrderServiceClient orders;
    /** Redis 边界替身。 */
    private CartRedisRepository repository;
    /** 被测清理实现。 */
    private CartCleanupServiceImpl service;

    /** 各用例使用独立依赖，避免上个请求的订单证明被复用。 */
    @BeforeEach
    void setUp() {
        orders = mock(OrderServiceClient.class);
        repository = mock(CartRedisRepository.class);
        service = new CartCleanupServiceImpl(orders, repository);
    }

    /** 只对订单中真实购买的数量清理，结果区分实际删除和跳过。 */
    @Test
    void shouldCleanOnlyAfterVerifiedOrder() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.success(order(5L, 1L, 2)));
        CartCleanupRequest request = request(2);
        when(repository.removeSubmitted(5L, 1L, request.items())).thenReturn(1L);
        CartCleanupVO result = service.cleanup(5L, 123L, request);
        assertEquals(123L, result.orderId());
        assertEquals(1, result.removedCount());
        assertEquals(0, result.skippedCount());
    }

    /** 已删除条目可以再次提交，不能把重复清理误认为订单失败。 */
    @Test
    void shouldAllowIdempotentCleanupRetry() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.success(order(5L, 1L, 2)));
        assertEquals(1, service.cleanup(5L, 123L, request(2)).skippedCount());
    }

    /** 即使远程接口错误返回别人订单，本地仍必须检查归属。 */
    @Test
    void shouldRejectOtherUsersOrder() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.success(order(99L, 1L, 2)));
        assertError(CommonErrorCode.NOT_FOUND, request(2));
        verifyNoInteractions(repository);
    }

    /** 不允许用窗口一的订单清理另一个窗口。 */
    @Test
    void shouldRejectDifferentWindow() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.success(order(5L, 10L, 2)));
        assertError(CommonErrorCode.CONFLICT, request(2));
        verifyNoInteractions(repository);
    }

    /** 请求数量必须与已保存的订单明细一致，不能把未购买的数量整项清除。 */
    @Test
    void shouldRejectDifferentPurchasedQuantity() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.success(order(5L, 1L, 1)));
        assertError(CommonErrorCode.CONFLICT, request(2));
        verifyNoInteractions(repository);
    }

    /** 成功包装但缺少订单明细属于上游契约异常，不能退化成无条件删除。 */
    @Test
    void shouldRejectMalformedOrderResponse() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.success(
                new OrderCartSnapshotVO(123L, 5L, 1L, null)));
        assertError(CommonErrorCode.SERVICE_UNAVAILABLE, request(2));
        verifyNoInteractions(repository);
    }

    /** 远程订单不存在或不属于当前用户统一返回 404。 */
    @Test
    void shouldPreserveRemoteNotFound() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.failure(CommonErrorCode.NOT_FOUND));
        assertError(CommonErrorCode.NOT_FOUND, request(2));
        verifyNoInteractions(repository);
    }

    /** 订单服务不可用时保留购物车，允许订单成功后单独重试清理。 */
    @Test
    void shouldNotDeleteOnOrderServiceFailure() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.failure(CommonErrorCode.SERVICE_UNAVAILABLE));
        assertError(CommonErrorCode.SERVICE_UNAVAILABLE, request(2));
        verifyNoInteractions(repository);
    }

    /** 重复商品 ID 和缺少版本必须在任何远程调用之前拒绝。 */
    @Test
    void shouldRejectInvalidSnapshotBeforeDependencies() {
        CartSubmittedItemRequest item = new CartSubmittedItemRequest(11L, 2, "revision-original");
        assertError(CommonErrorCode.BAD_REQUEST, new CartCleanupRequest(1L, List.of(item, item)));
        assertError(CommonErrorCode.BAD_REQUEST, new CartCleanupRequest(1L,
                List.of(new CartSubmittedItemRequest(11L, 2, " "))));
        assertError(CommonErrorCode.BAD_REQUEST, new CartCleanupRequest(1L, List.of()));
        verifyNoInteractions(orders, repository);
    }

    /** 直接 HTTP 失败和 Sentinel 降级返回错误码时保持相同的身份、归属和故障语义。 */
    @Test
    void shouldTranslateHttpFailuresWithoutDeleting() {
        for (int status : new int[]{401, 403, 404, 500}) {
            Request request = Request.create(Request.HttpMethod.GET, "http://order-service/orders/123",
                    Map.of(), (byte[]) null, null, null);
            FeignException exception = FeignException.errorStatus("getOrder",
                    Response.builder().request(request).status(status).headers(Map.of()).build());
            doThrow(exception).when(orders).getOrder(123L);
            CommonErrorCode expected = switch (status) {
                case 401 -> CommonErrorCode.UNAUTHORIZED;
                case 403 -> CommonErrorCode.FORBIDDEN;
                case 404 -> CommonErrorCode.NOT_FOUND;
                default -> CommonErrorCode.SERVICE_UNAVAILABLE;
            };
            assertError(expected, request(2));
        }
        verifyNoInteractions(repository);
    }

    /** 商品集合必须完整相同，数量相同但商品不同也不能被误判为一次合法清理。 */
    @Test
    void shouldRejectDifferentOrIncompletePurchasedItems() {
        when(orders.getOrder(123L)).thenReturn(ApiResponse.success(new OrderCartSnapshotVO(123L, 5L, 1L,
                List.of(new OrderPurchasedItemVO(12L, 2)))));
        assertError(CommonErrorCode.CONFLICT, request(2));
        when(orders.getOrder(123L)).thenReturn(ApiResponse.success(new OrderCartSnapshotVO(123L, 5L, 1L,
                List.of(new OrderPurchasedItemVO(11L, 2), new OrderPurchasedItemVO(12L, 1)))));
        assertError(CommonErrorCode.CONFLICT, request(2));
        verifyNoInteractions(repository);
    }

    /**
     * 构造原始提交快照；重试时必须沿用此内容，不能用新查询结果替换版本。
     * @param quantity 下单数量
     * @return 单窗口提交请求
     */
    private CartCleanupRequest request(int quantity) {
        return new CartCleanupRequest(1L,
                List.of(new CartSubmittedItemRequest(11L, quantity, "revision-original")));
    }

    /**
     * 构造订单服务保存的购买事实。
     * @param userId 订单归属
     * @param windowId 订单窗口
     * @param quantity 实际购买数量
     * @return 订单详情所需字段投影
     */
    private OrderCartSnapshotVO order(Long userId, Long windowId, int quantity) {
        return new OrderCartSnapshotVO(123L, userId, windowId,
                List.of(new OrderPurchasedItemVO(11L, quantity)));
    }

    /**
     * 验证失败对调用者呈现为正确业务错误，而非空指针或错误成功响应。
     * @param expected 预期业务码
     * @param request 清理快照
     */
    private void assertError(CommonErrorCode expected, CartCleanupRequest request) {
        assertEquals(expected, assertThrows(BusinessException.class,
                () -> service.cleanup(5L, 123L, request)).getErrorCode());
    }
}
