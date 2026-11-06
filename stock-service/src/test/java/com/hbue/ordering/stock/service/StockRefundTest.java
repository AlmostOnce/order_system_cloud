package com.hbue.ordering.stock.service;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.stock.api.dto.StockRefundRequest;
import com.hbue.ordering.stock.mapper.StockMapper;
import com.hbue.ordering.stock.mapper.StockFlowMapper;
import com.hbue.ordering.stock.messaging.StockEventOutboxWriter;
import com.hbue.ordering.stock.model.StockDO;
import com.hbue.ordering.stock.model.StockFlowDO;
import com.hbue.ordering.stock.service.impl.StockRefundServiceImpl;
import com.hbue.ordering.stock.support.StockRedisScript;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证原扣减余额与稳定退款号；真实事务提交和并发仍需运行环境验证。 */
class StockRefundTest {
    private final StockMapper stocks=mock(StockMapper.class);
    private final StockFlowMapper flows=mock(StockFlowMapper.class);
    private final StockEventOutboxWriter events=mock(StockEventOutboxWriter.class);
    private final StockRefundServiceImpl service=new StockRefundServiceImpl(stocks,flows,events,mock(StockRedisScript.class));
    private final StockRefundRequest request=new StockRefundRequest("ORDER9","a".repeat(32),1L,2L,1);
    private void stock() {var stock=new StockDO();stock.setStockId(3L);stock.setAvailableQty(7);stock.setVersion(1);when(stocks.selectByWindowProductForUpdate(1L,2L)).thenReturn(stock);}
    /** 已记回补流水后重试不能再次更新库存，也不能因为原可退额度已经用完而拒绝成功事实。 */
    @Test void duplicateRefundConfirmsOldFactAndValidatesOrigin() {
        stock();var old=new StockFlowDO();old.setOriginalBizRef("ORDER9");old.setChangeQty(1);when(flows.selectRefund("a".repeat(32),3L)).thenReturn(old);
        assertEquals("a".repeat(32),service.restore(request));verify(stocks,never()).restoreQuantity(any(),anyInt());verifyNoInteractions(events);
        old.setOriginalBizRef("OTHER");assertThrows(BusinessException.class,()->service.restore(request));
    }
    /** 取消回补和先前部分退款已经用尽原扣减时，不能继续加库存。 */
    @Test void exhaustedOriginalDeductionCannotRestoreAgain() {
        stock();when(flows.refundableQuantity(3L,"ORDER9")).thenReturn(0);
        assertThrows(BusinessException.class,()->service.restore(request));verify(stocks,never()).restoreQuantity(any(),anyInt());verifyNoInteractions(events);
    }
}
