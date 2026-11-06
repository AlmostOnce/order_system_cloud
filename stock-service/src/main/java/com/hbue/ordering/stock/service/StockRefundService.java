package com.hbue.ordering.stock.service;
import com.hbue.ordering.stock.api.dto.StockRefundRequest;
/** 关联原扣减的部分回补，不允许累计数量超过扣减数量。 */
public interface StockRefundService { String restore(StockRefundRequest command); }
