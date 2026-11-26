package com.hbue.ordering.comment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.api.ProductRatingChangedEvent;
import com.hbue.ordering.comment.mapper.CommentWriteMapper;
import com.hbue.ordering.comment.model.CommentRowDO;
import com.hbue.ordering.comment.service.CommentMetadataWriter;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * MySQL元数据、评分和outbox在同一个本地事务内提交，不包含Mongo事务。
 *
 * <p>汇总更新使用"增量 + 条件"而不是先读后写：`adjustRating` 自带非负上界，
 * 命中不到行就抛异常回滚，因此并发删除同一条评价时不会把汇总扣成负数，
 * 也不会出现评价已删、汇总没扣的中间状态。</p>
 *
 * <p>每次汇总变更后重新读取版本并写一条全量快照事件。事件编号统一使用新生成的 UUID：
 * 同一条评价在新增、修改、删除过程中会产生多条事件，若沿用评价标识会撞主键。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
@Service
public class CommentMetadataWriterImpl implements CommentMetadataWriter {

    /**
     * 评价与评分汇总的写入 Mapper。
     */
    private final CommentWriteMapper mapper;

    /**
     * JSON 编解码器，用于序列化评分事件快照。
     */
    private final ObjectMapper json;

    /**
     * 创建元数据写入器。
     *
     * @param mapper 评价与评分汇总写入
     * @param json   JSON 编解码器
     */
    public CommentMetadataWriterImpl(CommentWriteMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    /**
     * 新增评价。
     *
     * @param row 待写入的评价元数据
     */
    @Override
    @Transactional(timeout = 15)
    public void commit(CommentRowDO row) {
        mapper.insertComment(row);
        mapper.updateRating(row.productId(), row.score());
        // 上面的写入已持有该菜品行锁；同菜品并发提交的版本和快照保持一致。
        publishSnapshot(row.productId());
    }

    /**
     * 修改评价。
     *
     * @param commentId     公开评价标识
     * @param productId     菜品编号
     * @param previousScore 修改前评分
     * @param score         修改后评分
     * @param hasImages     修改后是否带图
     */
    @Override
    @Transactional(timeout = 15)
    public void revise(String commentId, Long productId, int previousScore, int score, boolean hasImages) {
        if (mapper.updateComment(commentId, score, hasImages) != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "评价已被删除或状态已变化，请刷新后重试");
        }
        // 评分未变化时只改了正文，汇总与版本都不需要动，也就没有下游事件。
        if (previousScore == score) {
            return;
        }
        if (mapper.adjustRating(productId, 0, score - previousScore) != 1) {
            throw new IllegalStateException("评分统计调整失败，事务必须回滚");
        }
        publishSnapshot(productId);
    }

    /**
     * 删除评价。
     *
     * @param row 被删除评价的元数据
     */
    @Override
    @Transactional(timeout = 15)
    public void remove(CommentRowDO row) {
        if (mapper.deleteComment(row.commentId()) != 1) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评价不存在或已删除");
        }
        if (mapper.adjustRating(row.productId(), -1, -row.score()) != 1) {
            throw new IllegalStateException("评分统计调整失败，事务必须回滚");
        }
        publishSnapshot(row.productId());
    }

    /**
     * 读取当前评分版本并写入一条待发布的零依赖全量快照。
     *
     * <p>必须在汇总更新之后调用：此时行锁已在本事务内持有，读到的条数、总分与版本号一致，
     * 下游按版本覆盖读模型，重复或乱序投递不会造成累加错误。</p>
     *
     * @param productId 菜品编号
     */
    private void publishSnapshot(Long productId) {
        CommentWriteMapper.RatingSnapshot snapshot = mapper.lockRating(productId);
        if (snapshot == null) throw new IllegalStateException("评分统计不存在");
        ProductRatingChangedEvent event = new ProductRatingChangedEvent(1, UUID.randomUUID().toString(), productId,
                snapshot.revision(), snapshot.reviewCount(), snapshot.scoreTotal());
        try {
            mapper.insertOutbox(event.eventId(), event.productId(), event.revision(), json.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("评分事件编码失败", exception);
        }
    }
}
