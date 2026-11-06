package com.hbue.ordering.order.task;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.feign.PaymentRefundClient;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.mapper.OrderRefundMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderRefundDO;
import com.hbue.ordering.order.service.OrderRefundService;
import com.hbue.ordering.stock.api.client.StockRefundClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

/** 远程结果未知时必须保留处理中，不能提前给学生显示成功。 */
class OrderRefundTaskTest {
    @Test void moneyOrStockFailureDoesNotConfirmSuccessAndRetryUsesSameRefundNumber() {
        var refunds=mock(OrderRefundMapper.class);var orders=mock(OrderMapper.class);var workflow=mock(OrderRefundService.class);
        var payments=mock(PaymentRefundClient.class);var stocks=mock(StockRefundClient.class);
        var task=new OrderRefundTask(refunds,orders,workflow,payments,stocks,"test");
        var row=new OrderRefundDO();row.setRefundNo("a".repeat(32));row.setOrderId(9L);row.setAmount(BigDecimal.TEN);row.setRestock(1);row.setProductId(2L);row.setQuantity(1);
        var order=new OrderDO();order.setOrderNo("ORDER9");order.setWindowId(1L);
        when(refunds.claim(1L)).thenReturn(1);when(refunds.selectById(1L)).thenReturn(row);when(orders.selectById(9L)).thenReturn(order);
        when(payments.refund(eq(9L),eq("test"),any())).thenThrow(new IllegalStateException("timeout"));
        task.executeOne(1L);verifyNoInteractions(stocks);verify(workflow,never()).complete(any());
        doReturn(ApiResponse.success(row.getRefundNo())).when(payments).refund(eq(9L),eq("test"),any());
        task.executeOne(1L);verify(workflow,never()).complete(any());
        when(stocks.restore(eq("test"),any())).thenReturn(ApiResponse.success(row.getRefundNo()));
        task.executeOne(1L);verify(workflow).complete(1L);
        verify(payments,times(3)).refund(9L,"test",new PaymentRefundClient.Command(row.getRefundNo(),BigDecimal.TEN));
    }
}
