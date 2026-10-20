package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.dto.request.OrderItemCreateRequest;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.service.OrderPersistenceService;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import com.hbue.ordering.stock.api.client.StockServiceClient;
import com.hbue.ordering.stock.api.error.StockErrorCode;
import com.hbue.ordering.user.api.client.UserServiceClient;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单创建与幂等逻辑测试。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long USER_ID = 17L;
    private static final String IDEMPOTENCY_KEY = "create-order-001";

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private ManagementAuditLogger auditLogger;

    @Mock
    private StockServiceClient stockServiceClient;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                userServiceClient,
                productServiceClient,
                orderPersistenceService,
                orderItemMapper,
                dataScopeService,
                auditLogger,
                stockServiceClient,
                "test-internal-token"
        );
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        // 默认库存扣减成功；不关心库存的用例不会因此产生多余打桩失败。
        lenient().when(stockServiceClient.deduct(any(), any()))
                .thenReturn(ApiResponse.success(null));
    }

    @Test
    void shouldPriceItemsOnServerAndPersistSnapshots() {
        AtomicReference<OrderDO> persistedOrder = new AtomicReference<>();
        AtomicReference<List<OrderItemDO>> persistedItems = new AtomicReference<>();
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenAnswer(invocation -> persistedOrder.get());
        stubQuotes(
                new ProductQuoteVO(101L, "A101", "鸡肉饭", new BigDecimal("5.25")),
                new ProductQuoteVO(202L, "B202", "豆浆", new BigDecimal("6.00"))
        );
        stubUser();
        doAnswer(invocation -> {
            OrderDO order = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            List<OrderItemDO> items = invocation.getArgument(1);
            order.setOrderId(501L);
            items.forEach(item -> item.setOrderId(501L));
            persistedOrder.set(order);
            persistedItems.set(List.copyOf(items));
            return order;
        }).when(orderPersistenceService).saveOrderWithItems(
                any(OrderDO.class),
                any()
        );
        OrderDetailVO response = orderService.createOrder(
                USER_ID,
                IDEMPOTENCY_KEY,
                createRequest("少辣", item(101L, 2), item(202L, 1))
        );

        assertEquals(0, new BigDecimal("16.50").compareTo(response.getTotalAmount()));
        assertEquals(2, response.getItems().size());
        assertEquals("鸡肉饭", response.getItems().getFirst().getProductName());
        assertEquals(0, new BigDecimal("5.25").compareTo(
                response.getItems().getFirst().getUnitPrice()
        ));
        assertEquals(2, response.getItems().getFirst().getQuantity());

        ArgumentCaptor<OrderDO> orderCaptor = ArgumentCaptor.forClass(OrderDO.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItemDO>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderPersistenceService).saveOrderWithItems(
                orderCaptor.capture(),
                itemsCaptor.capture()
        );
        assertEquals(0, new BigDecimal("16.50").compareTo(
                orderCaptor.getValue().getTotalAmount()
        ));
        assertEquals("A101", itemsCaptor.getValue().getFirst().getProductCodeSnapshot());
        assertEquals(0, new BigDecimal("10.50").compareTo(
                itemsCaptor.getValue().getFirst().getLineAmount()
        ));
    }

    @Test
    void shouldReplaySameBasketRegardlessOfItemOrderWithoutRepricing() {
        AtomicReference<OrderDO> persistedOrder = new AtomicReference<>();
        AtomicReference<List<OrderItemDO>> persistedItems = new AtomicReference<>();
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenAnswer(invocation -> persistedOrder.get());
        stubQuotes(
                new ProductQuoteVO(101L, "A101", "鸡肉饭", new BigDecimal("5.25")),
                new ProductQuoteVO(202L, "B202", "豆浆", new BigDecimal("6.00"))
        );
        stubUser();
        doAnswer(invocation -> {
            OrderDO order = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            List<OrderItemDO> items = invocation.getArgument(1);
            order.setOrderId(502L);
            persistedOrder.set(order);
            persistedItems.set(List.copyOf(items));
            return order;
        }).when(orderPersistenceService).saveOrderWithItems(
                any(OrderDO.class),
                any()
        );
        when(orderItemMapper.selectByOrderId(502L)).thenAnswer(
                invocation -> persistedItems.get()
        );

        OrderDetailVO first = orderService.createOrder(
                USER_ID,
                IDEMPOTENCY_KEY,
                createRequest("少辣", item(101L, 2), item(202L, 1))
        );
        OrderDetailVO replay = orderService.createOrder(
                USER_ID,
                IDEMPOTENCY_KEY,
                createRequest("少辣", item(202L, 1), item(101L, 2))
        );

        assertEquals(502L, first.getOrderId());
        assertEquals(502L, replay.getOrderId());
        assertEquals(2, replay.getItems().size());
        verify(productServiceClient, times(1)).quote(any());
        verify(orderPersistenceService, times(1)).saveOrderWithItems(any(), any());
    }

    @Test
    void shouldRejectDifferentRequestForSameKey() {
        OrderDO existingOrder = new OrderDO();
        existingOrder.setOrderId(600L);
        existingOrder.setRequestHash("different-hash");
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(existingOrder);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        USER_ID,
                        IDEMPOTENCY_KEY,
                        createRequest(null, item(101L, 1))
                )
        );

        assertEquals(CommonErrorCode.CONFLICT.code(), exception.getErrorCode().code());
        verify(productServiceClient, never()).quote(any());
        verify(orderPersistenceService, never()).saveOrderWithItems(any(), any());
    }

    @Test
    void shouldRejectUnavailableProducts() {
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(null);
        when(productServiceClient.quote(any())).thenReturn(
                ApiResponse.success(new ProductQuoteResponse(true, List.of()))
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        USER_ID,
                        IDEMPOTENCY_KEY,
                        createRequest(null, item(101L, 1))
                )
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(), exception.getErrorCode().code());
        verify(userServiceClient, never()).getUserById(USER_ID);
        verify(orderPersistenceService, never()).saveOrderWithItems(any(), any());
    }

    @Test
    void shouldReturnWinnerOrderAfterConcurrentUniqueKeyConflict() {
        OrderCreateRequest request = createRequest(null, item(101L, 1));
        AtomicInteger lookupCount = new AtomicInteger();
        OrderDO winnerOrder = new OrderDO();
        winnerOrder.setOrderId(707L);
        winnerOrder.setUserId(USER_ID);
        winnerOrder.setWindowId(3L);
        winnerOrder.setTotalAmount(new BigDecimal("5.25"));
        winnerOrder.setIdempotencyKey(IDEMPOTENCY_KEY);
        winnerOrder.setRequestHash(ReflectionTestUtils.invokeMethod(
                orderService,
                "calculateRequestHash",
                request
        ));
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenAnswer(invocation -> lookupCount.getAndIncrement() == 0
                ? null
                : winnerOrder);
        stubQuotes(new ProductQuoteVO(
                101L,
                "A101",
                "鸡肉饭",
                new BigDecimal("5.25")
        ));
        stubUser();
        when(orderItemMapper.selectByOrderId(707L)).thenReturn(List.of());
        doThrow(new DuplicateKeyException("duplicate idempotency key"))
                .when(orderPersistenceService)
                .saveOrderWithItems(any(), any());

        OrderDetailVO response = orderService.createOrder(
                USER_ID,
                IDEMPOTENCY_KEY,
                request
        );

        assertEquals(707L, response.getOrderId());
        assertEquals(2, lookupCount.get());
    }

    @Test
    void shouldRejectDuplicateProductIdsBeforeCallingServices() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        USER_ID,
                        IDEMPOTENCY_KEY,
                        createRequest(null, item(101L, 1), item(101L, 2))
                )
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(), exception.getErrorCode().code());
        verify(productServiceClient, never()).quote(any());
        verify(userServiceClient, never()).getUserById(USER_ID);
    }

    @Test
    void shouldRejectMissingIdempotencyKey() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        USER_ID,
                        null,
                        createRequest(null, item(101L, 1))
                )
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(), exception.getErrorCode().code());
        verify(orderMapper, never()).selectByUserIdAndIdempotencyKey(any(), any());
    }

    @Test
    void shouldFailCreationWhenStockNotEnough() {
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(null);
        stubQuotes(new ProductQuoteVO(
                101L,
                "A101",
                "鸡肉饭",
                new BigDecimal("5.25")
        ));
        stubUser();
        doAnswer(invocation -> {
            OrderDO order = invocation.getArgument(0);
            order.setOrderId(808L);
            return order;
        }).when(orderPersistenceService).saveOrderWithItems(any(OrderDO.class), any());
        // 库存服务返回库存不足：订单创建必须失败，由全局事务回滚订单写入。
        when(stockServiceClient.deduct(any(), any())).thenReturn(
                ApiResponse.failure(
                        StockErrorCode.STOCK_NOT_ENOUGH,
                        "档口 3 的菜品 101 库存不足：可售 1，需要 2"
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        USER_ID,
                        IDEMPOTENCY_KEY,
                        createRequest(null, item(101L, 2))
                )
        );

        assertEquals(CommonErrorCode.CONFLICT.code(), exception.getErrorCode().code());
        verify(stockServiceClient, times(1)).deduct(any(), any());
    }

    @Test
    void shouldFailCreationWhenStockServiceUnavailable() {
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(null);
        stubQuotes(new ProductQuoteVO(
                101L,
                "A101",
                "鸡肉饭",
                new BigDecimal("5.25")
        ));
        stubUser();
        doAnswer(invocation -> {
            OrderDO order = invocation.getArgument(0);
            order.setOrderId(809L);
            return order;
        }).when(orderPersistenceService).saveOrderWithItems(any(OrderDO.class), any());
        // 库存服务不可用（如降级工厂返回）时同样必须失败，不能当作扣减成功。
        when(stockServiceClient.deduct(any(), any())).thenReturn(
                ApiResponse.failure(
                        CommonErrorCode.SERVICE_UNAVAILABLE,
                        "库存服务暂时不可用"
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        USER_ID,
                        IDEMPOTENCY_KEY,
                        createRequest(null, item(101L, 2))
                )
        );

        assertEquals(
                CommonErrorCode.SERVICE_UNAVAILABLE.code(),
                exception.getErrorCode().code()
        );
    }

    private void stubQuotes(ProductQuoteVO... quotes) {
        when(productServiceClient.quote(any())).thenReturn(
                ApiResponse.success(new ProductQuoteResponse(true, List.of(quotes)))
        );
    }

    private void stubUser() {
        when(userServiceClient.getUserById(USER_ID)).thenReturn(
                ApiResponse.success(UserBasicVO.builder()
                        .userId(USER_ID)
                        .username("order-user")
                        .roleCode("CUSTOMER")
                        .build())
        );
    }

    private OrderCreateRequest createRequest(
            String remark,
            OrderItemCreateRequest... items
    ) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setWindowId(3L);
        request.setItems(List.of(items));
        request.setRemark(remark);
        return request;
    }

    private OrderItemCreateRequest item(Long productId, Integer quantity) {
        OrderItemCreateRequest item = new OrderItemCreateRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}
