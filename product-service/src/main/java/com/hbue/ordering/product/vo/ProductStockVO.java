package com.hbue.ordering.product.vo;

/** 顾客可见的实时可售库存，不暴露库存流水或管理信息。 */
public record ProductStockVO(Long productId, int availableQty, boolean configured) {
}
