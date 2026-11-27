package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.api.ProductRatingVO;

/**
 * RatingSummaryService 业务能力边界。
 */
public interface RatingSummaryService {
    ProductRatingVO get(Long productId);

    /**
     * 删除指定菜品的评分读缓存。
     *
     * <p>评价新增、修改评分或删除成功后必须调用，否则 30–60 秒的短缓存会继续返回旧评分与旧条数。
     * 调用方应在本地事务提交之后再触发删除，避免删除后立刻被并发读者用旧库值回填。</p>
     *
     * @param productId 菜品编号
     */
    void evict(Long productId);
}
