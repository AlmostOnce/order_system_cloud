package com.hbue.ordering.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.payment.channel.BalancePaymentChannel;
import com.hbue.ordering.payment.channel.MockChannelNotifier;
import com.hbue.ordering.payment.channel.PaymentChannel;
import com.hbue.ordering.payment.channel.PaymentChannelRegistry;
import com.hbue.ordering.payment.dto.OrderInternalDTO;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.dto.request.PaymentCreateRequest;
import com.hbue.ordering.payment.feign.OrderInternalClient;
import com.hbue.ordering.payment.mapper.PaymentMapper;
import com.hbue.ordering.payment.model.PaymentDO;
import com.hbue.ordering.payment.service.PaymentService;
import com.hbue.ordering.payment.vo.PaymentCreateVO;
import com.hbue.ordering.payment.vo.PaymentStatusVO;
import com.hbue.ordering.user.api.client.UserBalanceInternalClient;
import com.hbue.ordering.user.api.dto.UserBalanceDeductCommand;
import com.hbue.ordering.user.api.vo.UserBalanceDeductResultVO;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付核心流程测试：创建支付单幂等、渠道路由、余额同步结算、
 * 回调验签、金额校验、回调重放与迟到通知的幂等处理、订单推进编排。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Long USER_ID = 17L;
    private static final Long ORDER_ID = 501L;
    private static final String PAYMENT_NO = "PAY202610120001AABBCCDD";
    private static final BigDecimal ORDER_AMOUNT = new BigDecimal("18.50");
    private static final String INTERNAL_SCOPE_TOKEN = "internal_scope_token_test";

    @Mock
    private OrderInternalClient orderInternalClient;

    @Mock
    private PaymentChannel paymentChannel;

    @Mock
    private MockChannelNotifier mockChannelNotifier;

    @Mock
    private UserBalanceInternalClient userBalanceInternalClient;

    @Mock
    private PaymentMapper paymentMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        when(paymentChannel.channelCode()).thenReturn("MOCK");
        PaymentChannelRegistry channelRegistry = new PaymentChannelRegistry(
                List.of(paymentChannel, new BalancePaymentChannel())
        );
        paymentService = new PaymentServiceImpl(
                orderInternalClient,
                channelRegistry,
                mockChannelNotifier,
                userBalanceInternalClient,
                objectMapper,
                INTERNAL_SCOPE_TOKEN
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
        when(paymentChannel.createPayment(any(PaymentDO.class)))
                .thenReturn(new PaymentChannel.ChannelPaymentCreation(
                        "MOCK001",
                        "http://127.0.0.1:9101/mock/channel/checkout/x",
                        "mockpay://pay?paymentNo=x"
                ));

        // 请求未指定渠道时按缺省 MOCK 处理。
        PaymentCreateVO vo = paymentService.createPayment(
                USER_ID, createRequest(null)
        );

        assertEquals(0, ORDER_AMOUNT.compareTo(vo.getAmount()));
        assertTrue(vo.getPaymentNo().startsWith("PAY"));
        assertEquals("MOCK", vo.getChannelCode());
        assertEquals("http://127.0.0.1:9101/mock/channel/checkout/x",
                vo.getCheckoutUrl());
        verify(paymentMapper).insert(any(PaymentDO.class));
    }

    @Test
    void shouldRejectCreateWithUnsupportedChannel() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(
                        USER_ID, createRequest("WECHAT")
                ));

        assertEquals(
                CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code()
        );
        verify(paymentMapper, never()).insert(any(PaymentDO.class));
    }

    @Test
    void shouldRejectCreateWhenOrderMissing() {
        when(orderInternalClient.getOrder(ORDER_ID))
                .thenReturn(ApiResponse.success(null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(
                        USER_ID, createRequest(null)
                ));

        assertEquals(
                CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldHideOtherUsersOrderAsNotFound() {
        stubOrder(order("待支付", 999L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(
                        USER_ID, createRequest(null)
                ));

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
                () -> paymentService.createPayment(
                        USER_ID, createRequest(null)
                ));

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

        PaymentCreateVO vo = paymentService.createPayment(
                USER_ID, createRequest(null)
        );

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

        PaymentCreateVO vo = paymentService.createPayment(
                USER_ID, createRequest(null)
        );

        assertEquals(PAYMENT_NO, vo.getPaymentNo());
    }

    // ---------- 余额渠道同步结算 ----------

    @Test
    void shouldSettleBalancePaymentAndAdvanceOrder() {
        stubOrder(order("待支付", USER_ID));
        when(paymentMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.insert(any(PaymentDO.class))).thenReturn(1);
        when(userBalanceInternalClient.deduct(
                eq(USER_ID), eq(INTERNAL_SCOPE_TOKEN),
                any(UserBalanceDeductCommand.class)
        )).thenReturn(ApiResponse.success(idempotentFalse()));
        when(paymentMapper.markSuccessIfCreated(
                anyString(), eq(ORDER_AMOUNT), anyString()
        )).thenReturn(1);
        when(orderInternalClient.markPaid(ORDER_ID))
                .thenReturn(ApiResponse.success(null));
        when(paymentMapper.selectByPaymentNo(anyString()))
                .thenReturn(balancePayment("SUCCESS"));

        PaymentCreateVO vo = paymentService.createPayment(
                USER_ID, balanceRequest("pay-pass-123456")
        );

        assertEquals(BalancePaymentChannel.CHANNEL_CODE, vo.getChannelCode());
        assertEquals("SUCCESS", vo.getStatus());
        // 余额渠道为同步结算，不返回收银台凭证。
        assertNull(vo.getCheckoutUrl());
        assertNull(vo.getQrCodeContent());
        verify(userBalanceInternalClient).deduct(
                eq(USER_ID), eq(INTERNAL_SCOPE_TOKEN),
                any(UserBalanceDeductCommand.class)
        );
        verify(orderInternalClient).markPaid(ORDER_ID);
    }

    @Test
    void shouldRejectBalancePaymentWhenBalanceInsufficient() {
        stubOrder(order("待支付", USER_ID));
        when(paymentMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.insert(any(PaymentDO.class))).thenReturn(1);
        when(userBalanceInternalClient.deduct(
                eq(USER_ID), eq(INTERNAL_SCOPE_TOKEN),
                any(UserBalanceDeductCommand.class)
        )).thenThrow(feignFailure(409, "余额不足，请先充值"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(
                        USER_ID, balanceRequest("pay-pass-123456")
                ));

        assertEquals(
                CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code()
        );
        assertEquals("余额不足，请先充值", exception.getMessage());
        // 扣款失败时支付单保持 CREATED，不得标记成功、不得推进订单。
        verify(paymentMapper, never()).markSuccessIfCreated(
                anyString(), any(), anyString()
        );
        verify(orderInternalClient, never()).markPaid(any());
    }

    @Test
    void shouldMapLockedPayPasswordToTooManyRequests() {
        stubOrder(order("待支付", USER_ID));
        when(paymentMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.insert(any(PaymentDO.class))).thenReturn(1);
        when(userBalanceInternalClient.deduct(
                eq(USER_ID), eq(INTERNAL_SCOPE_TOKEN),
                any(UserBalanceDeductCommand.class)
        )).thenThrow(feignFailure(429, "支付密码已锁定，请稍后再试"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(
                        USER_ID, balanceRequest("wrong-pass")
                ));

        assertEquals(
                CommonErrorCode.TOO_MANY_REQUESTS.code(),
                exception.getErrorCode().code()
        );
        verify(paymentMapper, never()).markSuccessIfCreated(
                anyString(), any(), anyString()
        );
    }

    // ---------- 渠道回调 ----------

    @Test
    void shouldRejectNotifyWithInvalidSignature() {
        when(paymentMapper.selectByPaymentNo(PAYMENT_NO))
                .thenReturn(payment("CREATED"));
        stubSignatureVerify(false);

        ApiResponse<Void> response = paymentService.handleNotify(
                notify("18.50", "SUCCESS"), "bad-signature"
        );

        assertEquals(CommonErrorCode.UNAUTHORIZED.code(), response.getCode());
        verify(paymentMapper, never()).touchNotify(anyString());
    }

    @Test
    void shouldRejectNotifyForUnknownPayment() {
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
     * 构建创建支付单请求。
     */
    private PaymentCreateRequest createRequest(String channelCode) {
        PaymentCreateRequest request = new PaymentCreateRequest();
        request.setOrderId(ORDER_ID);
        request.setChannelCode(channelCode);
        return request;
    }

    /**
     * 构建余额渠道创建支付单请求。
     */
    private PaymentCreateRequest balanceRequest(String payPassword) {
        PaymentCreateRequest request = createRequest(
                BalancePaymentChannel.CHANNEL_CODE
        );
        request.setPayPassword(payPassword);
        return request;
    }

    /**
     * 构建非幂等重放的余额扣款结果。
     */
    private UserBalanceDeductResultVO idempotentFalse() {
        return UserBalanceDeductResultVO.builder()
                .userId(USER_ID)
                .bizRef(PAYMENT_NO)
                .amount(ORDER_AMOUNT)
                .balanceAfter(new BigDecimal("100.00"))
                .idempotent(false)
                .build();
    }

    /**
     * 构建余额渠道支付单。
     */
    private PaymentDO balancePayment(String status) {
        PaymentDO payment = payment(status);
        payment.setChannelCode(BalancePaymentChannel.CHANNEL_CODE);
        return payment;
    }

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
     * 构建下游业务失败异常（带统一响应体，供解析提示文案）。
     */
    private FeignException feignFailure(int status, String message) {
        String body = "{\"code\":\"COMMON_" + status + "\",\"message\":\""
                + message + "\",\"data\":null}";
        Response response = Response.builder()
                .status(status)
                .reason("Business Failure")
                .request(Request.create(
                        Request.HttpMethod.POST,
                        "http://user-service/internal/users/17/balance/deduct",
                        Collections.emptyMap(),
                        null,
                        StandardCharsets.UTF_8,
                        null
                ))
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("user-service", response);
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
