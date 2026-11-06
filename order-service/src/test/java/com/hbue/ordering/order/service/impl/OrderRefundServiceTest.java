package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.RefundApplyRequest;
import com.hbue.ordering.order.dto.request.RefundDecisionRequest;
import com.hbue.ordering.order.feign.PaymentRefundClient;
import com.hbue.ordering.order.mapper.*;
import com.hbue.ordering.order.model.*;
import com.hbue.ordering.order.notification.OrderNotificationOutboxWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 测试真实编排分支；并发锁和SQL条件由临时表检查覆盖，本文件未运行Java测试。 */
class OrderRefundServiceTest {
    private final OrderMapper orders=mock(OrderMapper.class);
    private final OrderItemMapper items=mock(OrderItemMapper.class);
    private final OrderRefundMapper refunds=mock(OrderRefundMapper.class);
    private final RefundActionMapper actions=mock(RefundActionMapper.class);
    private final DataScopeService scopes=mock(DataScopeService.class);
    private final PaymentRefundClient payments=mock(PaymentRefundClient.class);
    private final OrderRefundServiceImpl service=new OrderRefundServiceImpl(orders,items,refunds,actions,scopes,payments,mock(OrderNotificationOutboxWriter.class),"internal-test",false);
    private final RefundApplyRequest request=new RefundApplyRequest(12L,1,"少一份");
    /** 最小实付快照，所有金额来自订单和支付事实，客户端没有金额字段。 */
    private void order(String status) {
        var order=new OrderDO();order.setOrderId(9L);order.setUserId(7L);order.setWindowId(1L);order.setStatus(status);order.setTotalAmount(new BigDecimal("30.00"));
        when(orders.lockRefundOrder(9L)).thenReturn(order);
        var item=new OrderItemDO();item.setItemId(12L);item.setOrderId(9L);item.setProductId(2L);item.setProductNameSnapshot("鸡肉饭");item.setQuantity(3);item.setRefundReservedQuantity(0);item.setLineAmount(new BigDecimal("30.00"));
        when(items.selectById(12L)).thenReturn(item);
        when(payments.fact(9L,"internal-test")).thenReturn(ApiResponse.success(new PaymentRefundClient.Fact(7L,new BigDecimal("30.00"),"BALANCE")));
        when(refunds.committedAmount(12L)).thenReturn(BigDecimal.ZERO);
        when(orders.bumpRefundVersion(9L)).thenReturn(1);
        when(actions.insert(any(RefundActionDO.class))).thenReturn(1);
        when(refunds.insert(any(OrderRefundDO.class))).thenAnswer(call->{OrderRefundDO r=call.getArgument(0);r.setRefundId(1L);return 1;});
        when(refunds.updateById(any(OrderRefundDO.class))).thenReturn(1);
    }
    /** 未接单即直接批；已接单不能借未接单文案绕开商家审批。 */
    @Test void lockedCurrentStatusSelectsApprovalBranch() {
        order("已支付");when(items.reserveRefund(12L,9L,1,1)).thenReturn(1);
        var direct=service.apply(7L,9L,"request-one",request);
        assertEquals("APPROVED",direct.status());assertTrue(direct.restock());assertEquals(new BigDecimal("10.00"),direct.amount());
        order("制作中");when(items.reserveRefund(12L,9L,1,0)).thenReturn(1);
        var pending=service.apply(7L,9L,"request-two",request);assertEquals("PENDING",pending.status());assertFalse(pending.restock());
    }
    /** 重试返回原退款单，既不重复占用份数，也不依赖新的支付查询。 */
    @Test void sameApplicationIsIdempotentAndForeignAccountCannotReadIt() {
        order("已支付");var old=new OrderRefundDO();old.setRefundId(1L);old.setOrderId(9L);old.setItemId(12L);old.setQuantity(1);old.setReason("少一份");old.setRestock(1);
        when(refunds.byRequest(7L,"request-one")).thenReturn(old);
        assertEquals(1L,service.apply(7L,9L,"request-one",request).refundId());
        verify(items,never()).reserveRefund(any(),any(),anyInt(),anyInt());verify(payments,never()).fact(any(),any());
        assertThrows(BusinessException.class,()->service.apply(8L,9L,"request-one",request));
    }
    /** 只有PENDING到期自动批，不把已拒绝/争议中的单据误当作商家未响应。 */
    @Test void onlyExpiredMerchantReviewAutomaticallyApprovesWithoutRestock() {
        order("待取货");var row=new OrderRefundDO();row.setRefundId(1L);row.setOrderId(9L);row.setItemId(12L);row.setQuantity(1);row.setStatus("PENDING");row.setDueAt(LocalDateTime.now().minusHours(1));
        when(refunds.selectById(1L)).thenReturn(row);when(refunds.lock(1L)).thenReturn(row);when(items.approveRefund(12L,1)).thenReturn(1);
        service.expire(1L);assertEquals("APPROVED",row.getStatus());assertEquals(0,row.getRestock());
        service.expire(1L);verify(items,times(1)).approveRefund(12L,1);
        row.setStatus("APPEALED");service.expire(1L);verify(items,times(1)).approveRefund(12L,1);
    }
    /** 已批准的数量不能撤回，越权店主不能改决定。 */
    @Test void approvedCannotWithdrawAndForeignMerchantCannotDecide() {
        order("制作中");var row=new OrderRefundDO();row.setRefundId(1L);row.setOrderId(9L);row.setUserId(7L);row.setStatus("PROCESSING");
        when(refunds.lock(1L)).thenReturn(row);when(refunds.selectById(1L)).thenReturn(row);
        assertThrows(BusinessException.class,()->service.withdraw(7L,9L,1L));
        assertThrows(BusinessException.class,()->service.decide(99L,false,false,1L,new RefundDecisionRequest(true,true,"同意")));
        verify(items,never()).releaseRefund(any(),anyInt());
    }
}
