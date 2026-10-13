package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.service.OrderPersistenceService;
import com.hbue.ordering.order.vo.OrderStatusVO;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.user.api.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付回调驱动的订单推进测试：待支付到已支付、幂等与冲突场景。
 */
@ExtendWith(MockitoExtension.class)
class OrderMarkOrderPaidTest {

    private static final Long ORDER_ID = 501L;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private ManagementAuditLogger auditLogger;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                userServiceClient,
                productServiceClient,
                orderPersistenceService,
                orderItemMapper,
                dataScopeService,
                auditLogger
        );
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
    }

    @Test
    void shouldAdvancePendingPaymentOrderToPaid() {
        OrderDO order = order("待支付", 0);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(orderPersistenceService.updateStatusIfUnchanged(
                ORDER_ID, "待支付", 0, "已支付"
        )).thenReturn(true);

        OrderStatusVO vo = orderService.markOrderPaid(ORDER_ID);

        assertEquals("已支付", vo.getStatus());
        verify(orderPersistenceService).updateStatusIfUnchanged(
                ORDER_ID, "待支付", 0, "已支付"
        );
    }

    @Test
    void shouldBeIdempotentWhenOrderAlreadyPaid() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order("已支付", 1));

        OrderStatusVO vo = orderService.markOrderPaid(ORDER_ID);

        assertEquals("已支付", vo.getStatus());
        verify(orderPersistenceService, never())
                .updateStatusIfUnchanged(any(), any(), any(), any());
    }

    @Test
    void shouldRejectCancelledOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order("已取消", 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.markOrderPaid(ORDER_ID));

        assertEquals(
                CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code()
        );
        verify(orderPersistenceService, never())
                .updateStatusIfUnchanged(any(), any(), any(), any());
    }

    @Test
    void shouldBeIdempotentWhenConcurrentWinnerAlreadyPaid() {
        // 并发回调竞争失败（乐观锁更新 0 行），回读发现订单已被推进为已支付。
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(order("待支付", 0))
                .thenReturn(order("已支付", 1));
        when(orderPersistenceService.updateStatusIfUnchanged(
                ORDER_ID, "待支付", 0, "已支付"
        )).thenReturn(false);

        OrderStatusVO vo = orderService.markOrderPaid(ORDER_ID);

        assertEquals("已支付", vo.getStatus());
    }

    /**
     * 构建订单持久化对象。
     */
    private OrderDO order(String status, Integer version) {
        OrderDO order = new OrderDO();
        order.setOrderId(ORDER_ID);
        order.setOrderNo("ORD202610120001AABBCCDD");
        order.setUserId(17L);
        order.setTotalAmount(new BigDecimal("18.50"));
        order.setStatus(status);
        order.setVersion(version);
        return order;
    }
}
