package com.hbue.ordering.payment.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.payment.channel.MockChannelNotifier;
import com.hbue.ordering.payment.channel.PaymentChannel;
import com.hbue.ordering.payment.dto.OrderInternalDTO;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.feign.OrderInternalClient;
import com.hbue.ordering.payment.mapper.PaymentMapper;
import com.hbue.ordering.payment.model.PaymentDO;
import com.hbue.ordering.payment.service.PaymentService;
import com.hbue.ordering.payment.vo.PaymentCreateVO;
import com.hbue.ordering.payment.vo.PaymentStatusVO;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付核心流程测试：创建支付单幂等、回调验签、金额校验、
 * 回调重放与迟到通知的幂等处理、订单推进编排。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Long USER_ID = 17L;
    private static final Long ORDER_ID = 501L;
    private static final String PAYMENT_NO = "PAY202610120001AABBCCDD";
    private static final BigDecimal ORDER_AMOUNT = new BigDecimal("18.50");

    @Mock
    private OrderInternalClient orderInternalClient;

    @Mock
    private PaymentChannel paymentChannel;

    @Mock
    private MockChannelNotifier mockChannelNotifier;

    @Mock
    private PaymentMapper paymentMapper;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                orderInternalClient,
                paymentChannel,
                mockChannelNotifier
        );
        ReflectionTestUtils.setField(
                paymentService, "baseMapper", paymentMapper
        );
    }

    // ---------- 创建支付单 ----------

    @Test
    void shouldCreatePaymentWithOrderSnapshotAmount() {
        stubOrder(order("待支付", USER_ID));
        when(paymentMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.insert(any(PaymentDO.class))).thenReturn(1);
        when(paymentChannel.channelCode()).thenReturn("MOCK");
        when(paymentChannel.createPayment(any(PaymentDO.class)))
                .thenReturn(new PaymentChannel.ChannelPaymentCreation(
                        "MOCK001",
                        "http://127.0.0.1:9101/mock/channel/checkout/x",
                        "mockpay://pay?paymentNo=x"
                ));

        PaymentCreateVO vo = paymentService.createPayment(USER_ID, ORDER_ID);

        assertEquals(0, ORDER_AMOUNT.compareTo(vo.getAmount()));
        assertTrue(vo.getPaymentNo().startsWith("PAY"));
        assertEquals("http://127.0.0.1:9101/mock/channel/checkout/x",
                vo.getCheckoutUrl());
        verify(paymentMapper).insert(any(PaymentDO.class));
    }

    @Test
    void shouldRejectCreateWhenOrderMissing() {
        when(orderInternalClient.getOrder(ORDER_ID))
                .thenReturn(ApiResponse.success(null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(USER_ID, ORDER_ID));

        assertEquals(
                CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldHideOtherUsersOrderAsNotFound() {
        stubOrder(order("待支付", 999L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(USER_ID, ORDER_ID));

        assertEquals(
                CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code()
        );
        verify(paymentMapper, never()).insert(any(PaymentDO.class));
    }

    @Test
    void shouldRejectCreateWhenOrderNotPendingPayment() {
        stubOrder(order("已取消", USER_ID));
        when(paymentMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(USER_ID, ORDER_ID));

        assertEquals(
                CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code()
        );
        verify(paymentMapper, never()).insert(any(PaymentDO.class));
    }

    @Test
    void shouldReturnExistingActivePaymentIdempotently() {
        stubOrder(order("待支付", USER_ID));
        PaymentDO active = payment("CREATED");
        when(paymentMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(active);
        when(paymentChannel.createPayment(active))
                .thenReturn(new PaymentChannel.ChannelPaymentCreation(
                        "MOCK001", "url", "qr"
                ));

        PaymentCreateVO vo = paymentService.createPayment(USER_ID, ORDER_ID);

        assertEquals(PAYMENT_NO, vo.getPaymentNo());
        verify(paymentMapper, never()).insert(any(PaymentDO.class));
    }

    @Test
    void shouldReturnConcurrentWinnerOnDuplicateKey() {
        stubOrder(order("待支付", USER_ID));
        when(paymentMapper.selectActiveByOrderId(ORDER_ID))
                .thenReturn(null)
                .thenReturn(payment("CREATED"));
        when(paymentMapper.insert(any(PaymentDO.class)))
                .thenThrow(new DuplicateKeyException("uk_payments_order_active"));
        when(paymentChannel.createPayment(any(PaymentDO.class)))
                .thenReturn(new PaymentChannel.ChannelPaymentCreation(
                        "MOCK001", "url", "qr"
                ));

        PaymentCreateVO vo = paymentService.createPayment(USER_ID, ORDER_ID);

        assertEquals(PAYMENT_NO, vo.getPaymentNo());
    }

    // ---------- 渠道回调 ----------

    @Test
    void shouldRejectNotifyWithInvalidSignature() {
        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "SUCCESS"), "bad-signature"
        );

        assertEquals(CommonErrorCode.UNAUTHORIZED.code(), response.getCode());
        verify(paymentMapper, never()).touchNotify(anyString());
    }

    @Test
    void shouldRejectNotifyForUnknownPayment() {
        stubSignatureVerify(true);
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO)).thenReturn(null);

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "SUCCESS"), "sig"
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(), response.getCode());
        verify(paymentMapper, never()).touchNotify(anyString());
    }

    @Test
    void shouldRejectNotifyWithMismatchedAmount() {
        stubSignatureVerify(true);
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO))
                .thenReturn(payment("CREATED"));

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("0.01", "SUCCESS"), "sig"
        );

        assertEquals(CommonErrorCode.CONFLICT.code(), response.getCode());
        verify(paymentMapper).touchNotify(PAYMENT_NO);
        verify(paymentMapper, never()).markSuccessIfCreated(
                anyString(), any(), anyString()
        );
        verify(orderInternalClient, never()).markPaid(any());
    }

    @Test
    void shouldMarkSuccessAndAdvanceOrderOnceOnFirstNotify() {
        stubSignatureVerify(true);
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO))
                .thenReturn(payment("CREATED"));
        when(paymentMapper.markSuccessIfCreated(
                eq(PAYMENT_NO), any(), anyString()
        )).thenReturn(1);
        when(orderInternalClient.markPaid(ORDER_ID))
                .thenReturn(ApiResponse.success(null));

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "SUCCESS"), "sig"
        );

        assertEquals(CommonErrorCode.SUCCESS.code(), response.getCode());
        verify(paymentMapper).touchNotify(PAYMENT_NO);
        verify(paymentMapper).markSuccessIfCreated(
                eq(PAYMENT_NO), eq(ORDER_AMOUNT), anyString()
        );
        verify(orderInternalClient).markPaid(ORDER_ID);
    }

    @Test
    void shouldTreatDuplicatedSuccessNotifyIdempotently() {
        stubSignatureVerify(true);
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO))
                .thenReturn(payment("CREATED"))
                .thenReturn(payment("SUCCESS"));
        when(paymentMapper.markSuccessIfCreated(
                eq(PAYMENT_NO), any(), anyString()
        )).thenReturn(0);

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "SUCCESS"), "sig"
        );

        assertEquals(CommonErrorCode.SUCCESS.code(), response.getCode());
        verify(orderInternalClient, never()).markPaid(any());
    }

    @Test
    void shouldIgnoreLateSuccessNotifyForTerminalPayment() {
        stubSignatureVerify(true);
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO))
                .thenReturn(payment("CREATED"))
                .thenReturn(payment("FAILED"));
        when(paymentMapper.markSuccessIfCreated(
                eq(PAYMENT_NO), any(), anyString()
        )).thenReturn(0);

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "SUCCESS"), "sig"
        );

        // 返回成功让渠道停止重试，但不推进订单。
        assertEquals(CommonErrorCode.SUCCESS.code(), response.getCode());
        verify(orderInternalClient, never()).markPaid(any());
    }

    @Test
    void shouldMarkFailedWithoutAdvancingOrder() {
        stubSignatureVerify(true);
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO))
                .thenReturn(payment("CREATED"));
        when(paymentMapper.markFailedIfCreated(eq(PAYMENT_NO), anyString()))
                .thenReturn(1);

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "FAILED"), "sig"
        );

        assertEquals(CommonErrorCode.SUCCESS.code(), response.getCode());
        verify(paymentMapper).markFailedIfCreated(eq(PAYMENT_NO), anyString());
        verify(orderInternalClient, never()).markPaid(any());
    }

    @Test
    void shouldKeepPaymentSuccessWhenOrderAdvanceThrows() {
        stubSignatureVerify(true);
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO))
                .thenReturn(payment("CREATED"));
        when(paymentMapper.markSuccessIfCreated(
                eq(PAYMENT_NO), any(), anyString()
        )).thenReturn(1);
        when(orderInternalClient.markPaid(ORDER_ID))
                .thenThrow(feignServiceUnavailable());

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "SUCCESS"), "sig"
        );

        // 支付事实保持 SUCCESS 不回滚，订单推进失败仅留痕人工处置。
        assertEquals(CommonErrorCode.SUCCESS.code(), response.getCode());
        verify(orderInternalClient).markPaid(ORDER_ID);
    }

    @Test
    void shouldKeepPaymentSuccessWhenOrderRejectedConflict() {
        stubSignatureVerify(true);
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO))
                .thenReturn(payment("CREATED"));
        when(paymentMapper.markSuccessIfCreated(
                eq(PAYMENT_NO), any(), anyString()
        )).thenReturn(1);
        when(orderInternalClient.markPaid(ORDER_ID))
                .thenReturn(ApiResponse.failure(
                        CommonErrorCode.CONFLICT, "订单当前状态不允许执行该操作"
                ));

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "SUCCESS"), "sig"
        );

        assertEquals(CommonErrorCode.SUCCESS.code(), response.getCode());
    }

    // ---------- 状态查询 ----------

    @Test
    void shouldHideOtherUsersPaymentAsNotFound() {
        when(paymentMapper.selectById(9L)).thenReturn(payment("SUCCESS"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.getPaymentStatus(999L, 9L));

        assertEquals(
                CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldReturnLatestPaymentByOrder() {
        PaymentDO paymentDO = payment("SUCCESS");
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(paymentDO);

        PaymentStatusVO vo = paymentService.getLatestByOrder(USER_ID, ORDER_ID);

        assertEquals(PAYMENT_NO, vo.getPaymentNo());
        assertEquals("SUCCESS", vo.getStatus());
    }

    // ---------- 辅助方法 ----------

    /**
     * 桩定渠道验签结果。
     */
    private void stubSignatureVerify(boolean valid) {
        when(paymentChannel.verifyNotify(any(), anyString())).thenReturn(valid);
    }

    /**
     * 桩定订单内部信息响应。
     */
    private void stubOrder(OrderInternalDTO order) {
        when(orderInternalClient.getOrder(ORDER_ID))
                .thenReturn(ApiResponse.success(order));
    }

    /**
     * 构建订单内部信息。
     */
    private OrderInternalDTO order(String status, Long userId) {
        OrderInternalDTO order = new OrderInternalDTO();
        order.setOrderId(ORDER_ID);
        order.setOrderNo("ORD202610120001AABBCCDD");
        order.setUserId(userId);
        order.setTotalAmount(ORDER_AMOUNT);
        order.setStatus(status);
        return order;
    }

    /**
     * 构建进行中支付单。
     */
    private PaymentDO payment(String status) {
        PaymentDO payment = new PaymentDO();
        payment.setPaymentId(9L);
        payment.setPaymentNo(PAYMENT_NO);
        payment.setOrderId(ORDER_ID);
        payment.setOrderNo("ORD202610120001AABBCCDD");
        payment.setUserId(USER_ID);
        payment.setChannelCode("MOCK");
        payment.setAmount(ORDER_AMOUNT);
        payment.setStatus(status);
        payment.setNotifyCount(0);
        return payment;
    }

    /**
     * 构建渠道通知请求。
     */
    private ChannelNotifyRequest notify(String paidAmount, String status) {
        ChannelNotifyRequest request = new ChannelNotifyRequest();
        request.setPaymentNo(PAYMENT_NO);
        request.setChannelTradeNo("MOCK001");
        request.setPaidAmount(paidAmount);
        request.setStatus(status);
        request.setTimestamp(System.currentTimeMillis());
        return request;
    }

    /**
     * 构建 Feign 服务不可用异常。
     */
    private FeignException feignServiceUnavailable() {
        Response response = Response.builder()
                .status(503)
                .reason("Service Unavailable")
                .request(Request.create(
                        Request.HttpMethod.PATCH,
                        "http://order-service/internal/orders/501/paid",
                        Collections.emptyMap(),
                        null,
                        StandardCharsets.UTF_8,
                        null
                ))
                .build();
        return FeignException.errorStatus("order-service", response);
    }
}
