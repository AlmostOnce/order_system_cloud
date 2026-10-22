package com.hbue.ordering.product.service;

import com.hbue.ordering.product.vo.ProductStockVO;

import java.util.List;

/** 商品服务聚合的顾客可见实时库存查询。 */
public interface ProductStockService {
    List<ProductStockVO> getWindowStocks(Long windowId);
}
