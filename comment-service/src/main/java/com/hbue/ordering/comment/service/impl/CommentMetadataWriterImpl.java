package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.service.CommentMetadataWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.api.ProductRatingChangedEvent;
import com.hbue.ordering.comment.model.CommentRowDO;
import com.hbue.ordering.comment.mapper.CommentWriteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MySQL元数据、评分和outbox在同一个本地事务内提交，不包含Mongo事务。 */
@Service
public class CommentMetadataWriterImpl implements CommentMetadataWriter {
    private final CommentWriteMapper mapper;
    private final ObjectMapper json;
    public CommentMetadataWriterImpl(CommentWriteMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }
    @Transactional(timeout = 15)
    public void commit(CommentRowDO row) {
        mapper.insertComment(row);
        mapper.updateRating(row.productId(), row.score());
        // 上面的写入已持有该菜品行锁；同菜品并发提交的版本和快照保持一致。
        var snapshot = mapper.lockRating(row.productId());
        if (snapshot == null) throw new IllegalStateException("评分统计不存在");
        ProductRatingChangedEvent event = new ProductRatingChangedEvent(1,row.commentId(),row.productId(),
                snapshot.revision(),snapshot.reviewCount(),snapshot.scoreTotal());
        try {
            mapper.insertOutbox(event.eventId(), event.productId(), event.revision(), json.writeValueAsString(event));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("评分事件编码失败", exception); }
    }
}
