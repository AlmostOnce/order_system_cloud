package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.user.api.client.UserServiceClient;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单创建幂等逻辑测试。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long USER_ID = 17L;
    private static final String IDEMPOTENCY_KEY = "create-order-001";

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private OrderMapper orderMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        // 注入 MyBatis Mapper，使测试覆盖 ServiceImpl 的真实 save 调用路径。
        orderService = new OrderServiceImpl(userServiceClient);
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
    }

    /**
     * 验证同键同请求返回原订单，且金额小数位差异不改变请求语义。
     */
    @Test
    void shouldReplayExistingOrderForSameKeyAndRequest() {
        AtomicReference<OrderDO> persistedOrder = new AtomicReference<>();
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenAnswer(invocation -> persistedOrder.get());
        when(userServiceClient.getUserById(USER_ID)).thenReturn(
                ApiResponse.success(UserBasicVO.builder()
                        .userId(USER_ID)
                        .username("order-user")
                        .roleCode("CUSTOMER")
                        .build())
        );
        when(orderMapper.insert(any(OrderDO.class))).thenAnswer(invocation -> {
            OrderDO orderDO = invocation.getArgument(0);
            orderDO.setOrderId(501L);
            persistedOrder.set(orderDO);
            return 1;
        });

        OrderDetailVO firstResponse = orderService.createOrder(
                USER_ID,
                IDEMPOTENCY_KEY,
                createRequest("12.0", "少辣")
        );
        OrderDetailVO replayResponse = orderService.createOrder(
                USER_ID,
                IDEMPOTENCY_KEY,
                createRequest("12.00", "少辣")
        );

        assertEquals(501L, firstResponse.getOrderId());
        assertEquals(501L, replayResponse.getOrderId());
        assertNotNull(persistedOrder.get().getRequestHash());
        assertEquals(IDEMPOTENCY_KEY, persistedOrder.get().getIdempotencyKey());
        verify(orderMapper, times(1)).insert(any(OrderDO.class));
    }

    /**
     * 验证同一幂等键不能用于不同订单内容。
     */
    @Test
    void shouldRejectDifferentRequestForSameKey() {
        OrderDO existingOrder = new OrderDO();
        existingOrder.setIdempotencyKey(IDEMPOTENCY_KEY);
        existingOrder.setRequestHash("a-different-request-hash");
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(existingOrder);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        USER_ID,
                        IDEMPOTENCY_KEY,
                        createRequest("12.00", "少辣")
                )
        );

        assertEquals(CommonErrorCode.CONFLICT.code(), exception.getErrorCode().code());
        verify(orderMapper, never()).insert(any(OrderDO.class));
        verify(userServiceClient, never()).getUserById(USER_ID);
    }

    /**
     * 验证并发请求由数据库唯一约束裁决后，失败方返回赢家订单。
     */
    @Test
    void shouldReturnWinnerOrderAfterConcurrentUniqueKeyConflict() {
        AtomicInteger lookupCount = new AtomicInteger();
        AtomicReference<OrderDO> winnerOrder = new AtomicReference<>();
        when(orderMapper.selectByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenAnswer(invocation -> lookupCount.getAndIncrement() == 0
                ? null
                : winnerOrder.get());
        when(userServiceClient.getUserById(USER_ID)).thenReturn(
                ApiResponse.success(UserBasicVO.builder()
                        .userId(USER_ID)
                        .username("order-user")
                        .roleCode("CUSTOMER")
                        .build())
        );
        doAnswer(invocation -> {
            OrderDO attemptedOrder = invocation.getArgument(0);
            OrderDO persistedOrder = new OrderDO();
            persistedOrder.setOrderId(707L);
            persistedOrder.setUserId(USER_ID);
            persistedOrder.setWindowId(attemptedOrder.getWindowId());
            persistedOrder.setTotalAmount(attemptedOrder.getTotalAmount());
            persistedOrder.setRemark(attemptedOrder.getRemark());
            persistedOrder.setIdempotencyKey(IDEMPOTENCY_KEY);
            persistedOrder.setRequestHash(attemptedOrder.getRequestHash());
            winnerOrder.set(persistedOrder);
            throw new DuplicateKeyException("duplicate idempotency key");
        }).when(orderMapper).insert(any(OrderDO.class));

        OrderDetailVO response = orderService.createOrder(
                USER_ID,
                IDEMPOTENCY_KEY,
                createRequest("25.50", null)
        );

        assertEquals(707L, response.getOrderId());
        assertEquals(2, lookupCount.get());
        verify(orderMapper, times(1)).insert(any(OrderDO.class));
    }

    /**
     * 验证缺失幂等键时拒绝创建订单。
     */
    @Test
    void shouldRejectMissingIdempotencyKey() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.createOrder(
                        USER_ID,
                        null,
                        createRequest("12.00", null)
                )
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(), exception.getErrorCode().code());
        verify(orderMapper, never()).selectByUserIdAndIdempotencyKey(
                any(),
                any()
        );
    }

    private OrderCreateRequest createRequest(String amount, String remark) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setWindowId(3L);
        request.setTotalAmount(new BigDecimal(amount));
        request.setRemark(remark);
        return request;
    }
}
