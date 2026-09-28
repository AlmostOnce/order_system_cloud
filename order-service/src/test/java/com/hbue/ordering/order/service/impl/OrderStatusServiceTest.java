package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.enums.OrderStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单状态业务服务测试。
 *
 * @author order-system
 * @date 2026-09-28
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusServiceTest {

    private static final Long ORDER_ID = 701L;
    private static final Long CUSTOMER_ID = 37L;

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

    private OrderServiceImpl orderService;

    /**
     * 创建使用隔离依赖的订单业务服务。
     */
    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                userServiceClient,
                productServiceClient,
                orderPersistenceService,
                orderItemMapper
        );
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
    }

    /**
     * 顾客只能取消自己的待支付订单。
     */
    @Test
    void shouldCancelOwnPendingPaymentOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(CUSTOMER_ID, "待支付", 4)
        );
        when(orderPersistenceService.updateStatusIfUnchanged(
                ORDER_ID,
                "待支付",
                4,
                "已取消"
        )).thenReturn(true);

        OrderStatusVO result = orderService.cancelOrder(
                CUSTOMER_ID,
                ORDER_ID
        );

        assertEquals(ORDER_ID, result.getOrderId());
        assertEquals("已取消", result.getStatus());
        verify(orderPersistenceService).updateStatusIfUnchanged(
                ORDER_ID,
                "待支付",
                4,
                "已取消"
        );
    }

    /**
     * 取消不存在的订单必须返回资源不存在。
     */
    @Test
    void shouldReturnNotFoundWhenCancellingMissingOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.cancelOrder(CUSTOMER_ID, ORDER_ID)
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code());
        verify(orderPersistenceService, never())
                .updateStatusIfUnchanged(ORDER_ID, "待支付", 0, "已取消");
    }

    /**
     * 不属于当前顾客的订单按不存在处理，避免泄露订单信息。
     */
    @Test
    void shouldHideAnotherCustomersOrderWhenCancelling() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(88L, "待支付", 2)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.cancelOrder(CUSTOMER_ID, ORDER_ID)
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code());
        verify(orderPersistenceService, never())
                .updateStatusIfUnchanged(ORDER_ID, "待支付", 2, "已取消");
    }

    /**
     * 已支付订单不能通过顾客取消接口直接取消。
     */
    @Test
    void shouldRejectCancellingPaidOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(CUSTOMER_ID, "已支付", 5)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.cancelOrder(CUSTOMER_ID, ORDER_ID)
        );

        assertEquals(CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code());
        verify(orderPersistenceService, never())
                .updateStatusIfUnchanged(ORDER_ID, "已支付", 5, "已取消");
    }

    /**
     * 管理员可以将已支付订单推进到制作中。
     */
    @Test
    void shouldAdvancePaidOrderToPreparing() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(CUSTOMER_ID, "已支付", 6)
        );
        when(orderPersistenceService.updateStatusIfUnchanged(
                ORDER_ID,
                "已支付",
                6,
                "制作中"
        )).thenReturn(true);

        OrderStatusVO result = orderService.updateStatusByAdmin(
                ORDER_ID,
                OrderStatus.PREPARING
        );

        assertEquals("制作中", result.getStatus());
        verify(orderPersistenceService).updateStatusIfUnchanged(
                ORDER_ID,
                "已支付",
                6,
                "制作中"
        );
    }

    /**
     * 管理员不能跳过履约步骤或手动伪造支付成功。
     */
    @Test
    void shouldRejectSkippedAndPaymentStatusTransitionsByAdmin() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(CUSTOMER_ID, "已支付", 6)
        );

        BusinessException skippedTransition = assertThrows(
                BusinessException.class,
                () -> orderService.updateStatusByAdmin(
                        ORDER_ID,
                        OrderStatus.COMPLETED
                )
        );
        BusinessException fakePayment = assertThrows(
                BusinessException.class,
                () -> orderService.updateStatusByAdmin(
                        ORDER_ID,
                        OrderStatus.PAID
                )
        );

        assertEquals(CommonErrorCode.CONFLICT.code(),
                skippedTransition.getErrorCode().code());
        assertEquals(CommonErrorCode.CONFLICT.code(),
                fakePayment.getErrorCode().code());
        verify(orderPersistenceService, never())
                .updateStatusIfUnchanged(ORDER_ID, "已支付", 6, "已完成");
    }

    /**
     * 管理员请求缺少目标状态时必须在查询订单前返回参数错误。
     */
    @Test
    void shouldRejectMissingAdminTargetStatus() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.updateStatusByAdmin(ORDER_ID, null)
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code());
        verify(orderMapper, never()).selectById(ORDER_ID);
    }

    /**
     * 数据库中出现未登记状态时应按服务端数据异常处理。
     */
    @Test
    void shouldRejectUnknownStoredOrderStatus() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(CUSTOMER_ID, "未知状态", 1)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.cancelOrder(CUSTOMER_ID, ORDER_ID)
        );

        assertEquals(CommonErrorCode.INTERNAL_ERROR.code(),
                exception.getErrorCode().code());
        verify(orderPersistenceService, never())
                .updateStatusIfUnchanged(ORDER_ID, "未知状态", 1, "已取消");
    }

    /**
     * 并发状态更新中落后的请求返回冲突，不覆盖先完成的迁移。
     */
    @Test
    void shouldReturnConflictWhenOptimisticStatusUpdateLosesRace() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(CUSTOMER_ID, "待支付", 7)
        );
        when(orderPersistenceService.updateStatusIfUnchanged(
                ORDER_ID,
                "待支付",
                7,
                "已取消"
        )).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.cancelOrder(CUSTOMER_ID, ORDER_ID)
        );

        assertEquals(CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code());
    }

    /**
     * 构造订单状态业务测试数据。
     *
     * @param userId 订单所属顾客 ID
     * @param status 当前状态
     * @param version 乐观锁版本号
     * @return 订单持久化对象
     */
    private OrderDO order(Long userId, String status, Integer version) {
        OrderDO orderDO = new OrderDO();
        orderDO.setOrderId(ORDER_ID);
        orderDO.setUserId(userId);
        orderDO.setStatus(status);
        orderDO.setVersion(version);
        return orderDO;
    }
}
