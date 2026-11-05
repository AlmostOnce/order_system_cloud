package com.hbue.ordering.product.vo;

/** 当前可售菜品的展示图片，供排行榜等批量展示使用。 */
public record ProductImageVO(Long productId, Long windowId, String imageUrl) {}
