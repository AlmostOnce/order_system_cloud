package com.hbue.ordering.stock.messaging;

import com.hbue.ordering.stock.model.StockFlowDO;
import org.apache.seata.core.context.RootContext;
import org.springframework.stereotype.Component;

/** 在写入库存流水的同一数据库事务中持久化待发布事件。 */
@Component
public class StockEventOutboxWriter {
    private final StockEventOutboxMapper outboxMapper;

    public StockEventOutboxWriter(StockEventOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    public void write(StockFlowDO flow, int stockVersion, int availableQty) {
        StockEventOutboxRow row = new StockEventOutboxRow();
        row.setEventId(StockChangedEvent.eventId(
                flow.getBizType(), flow.getBizRef(), flow.getStockId()
        ));
        row.setBizType(flow.getBizType());
        row.setBizRef(flow.getBizRef());
        row.setStockId(flow.getStockId());
        row.setWindowId(flow.getWindowId());
        row.setProductId(flow.getProductId());
        row.setAvailableQty(availableQty);
        row.setStockVersion(stockVersion);
        outboxMapper.insert(row, RootContext.getXID());
    }
}
