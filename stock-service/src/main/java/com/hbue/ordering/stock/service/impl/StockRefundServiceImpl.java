package com.hbue.ordering.stock.service.impl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.stock.api.dto.StockRefundRequest;
import com.hbue.ordering.stock.mapper.*;
import com.hbue.ordering.stock.model.*;
import com.hbue.ordering.stock.messaging.StockEventOutboxWriter;
import com.hbue.ordering.stock.service.StockRefundService;
import com.hbue.ordering.stock.support.StockRedisScript;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** 部分退款回补与库存流水及MQ Outbox同库提交，原订单关联可防止多次退款累计超补。 */
@Service
public class StockRefundServiceImpl implements StockRefundService {
    private static final Logger LOG=LoggerFactory.getLogger(StockRefundServiceImpl.class);
    private final StockMapper stocks; private final StockFlowMapper flows;
    private final StockEventOutboxWriter events; private final StockRedisScript snapshots;
    public StockRefundServiceImpl(StockMapper stocks,StockFlowMapper flows,StockEventOutboxWriter events,StockRedisScript snapshots) {
        this.stocks=stocks;this.flows=flows;this.events=events;this.snapshots=snapshots;
    }
    /** 先锁库存行，再判重及检查原扣减余额，所有退款请求按同一库存串行。 */
    @Override @Transactional(rollbackFor=Exception.class)
    public String restore(StockRefundRequest c) {
        if(c==null || c.orderNo()==null || !c.orderNo().matches("[A-Za-z0-9_-]{1,64}") || c.refundNo()==null
                || !c.refundNo().matches("[a-f0-9]{32}") || c.windowId()==null || c.windowId()<=0
                || c.productId()==null || c.productId()<=0 || c.quantity()==null || c.quantity()<=0) throw bad("库存退款参数无效");
        StockDO stock=stocks.selectByWindowProductForUpdate(c.windowId(),c.productId());
        if(stock==null) throw bad("原菜品库存记录不存在");
        StockFlowDO old=flows.selectRefund(c.refundNo(),stock.getStockId());
        if(old!=null) {
            if(!c.orderNo().equals(old.getOriginalBizRef()) || !c.quantity().equals(old.getChangeQty())) throw bad("退款回补引用不一致");
            return c.refundNo();
        }
        if(flows.refundableQuantity(stock.getStockId(),c.orderNo())<c.quantity()) throw bad("回补超过原订单扣减数量");
        if(stocks.restoreQuantity(stock.getStockId(),c.quantity())!=1) throw bad("库存回补失败");
        int after=Math.addExact(stock.getAvailableQty(),c.quantity());
        StockFlowDO row=new StockFlowDO();row.setBizType("REFUND_RESTORE");row.setBizRef(c.refundNo());row.setOriginalBizRef(c.orderNo());
        row.setStockId(stock.getStockId());row.setWindowId(c.windowId());row.setProductId(c.productId());
        row.setChangeQty(c.quantity());row.setBeforeQty(stock.getAvailableQty());row.setAfterQty(after);
        row.setRemark("订单菜品退款回补");row.setCreatedAt(LocalDateTime.now());
        if(flows.insert(row)!=1) throw new IllegalStateException("退款库存流水保存失败");
        events.write(row,stock.getVersion()+1,after);
        // 提交后再更新快照，回滚不会把未生效的回补数量暴露给Redis；MQ仍会按版本修正缓存。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {snapshots.resetSnapshot(c.windowId(),c.productId(),after);}
                catch(RuntimeException e) {LOG.warn("退款库存快照暂未同步，等待库存事件修正，refundNo={}",c.refundNo());}
            }
        });
        return c.refundNo();
    }
    private BusinessException bad(String text) {return new BusinessException(CommonErrorCode.CONFLICT,text);}
}
