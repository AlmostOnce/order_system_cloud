package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.model.CommentRowDO;

/**
 * 菜品评价元数据、评分汇总与待发事件的本地事务边界。
 *
 * <p>三个方法都在同一个本地事务内完成"改评价 / 改汇总 / 写 Outbox"，
 * 不包含 MongoDB 正文写入（跨库无法本地事务），Mongo 的补偿由调用方负责。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
public interface CommentMetadataWriter {

    /**
     * 新增评价：写入评价行、累加评分汇总并记录评分事件。
     *
     * @param row 待写入的评价元数据
     */
    void commit(CommentRowDO row);

    /**
     * 修改评价：更新评分与带图标记，评分变化时同步调整汇总并记录事件。
     *
     * @param commentId     公开评价标识
     * @param productId     菜品编号
     * @param previousScore 修改前评分，用于计算汇总增量
     * @param score         修改后评分
     * @param hasImages     修改后是否带图
     */
    void revise(String commentId, Long productId, int previousScore, int score, boolean hasImages);

    /**
     * 删除评价：软删除评价行，扣减评分汇总并记录事件。
     *
     * <p>删除某菜品的最后一条评价时，汇总会归零并向商品服务推送零值快照，
     * 让评分投影回到"暂无评分"。</p>
     *
     * @param row 被删除评价的元数据，用于取菜品编号与原评分
     */
    void remove(CommentRowDO row);
}
