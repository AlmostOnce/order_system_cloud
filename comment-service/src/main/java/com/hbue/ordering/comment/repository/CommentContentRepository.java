package com.hbue.ordering.comment.repository;

import com.hbue.ordering.comment.model.CommentContentDO;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论正文的 MongoDB 访问层，与 MySQL 元数据仓储保持分离。
 */
@Repository
public class CommentContentRepository {
    private final MongoTemplate mongo;

    public CommentContentRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void insert(CommentContentDO content) {
        mongo.insert(content);
    }

    public CommentContentDO byId(String id) {
        return mongo.findById(id, CommentContentDO.class);
    }

    public Map<String, CommentContentDO> byIds(Collection<String> ids) {
        Map<String, CommentContentDO> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        Query query = Query.query(Criteria.where("_id").in(ids));
        for (CommentContentDO content : mongo.find(query, CommentContentDO.class)) result.put(content.id(), content);
        return result;
    }

    public void markCommitted(String id) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), Update.update("committed", true), CommentContentDO.class);
    }

    /**
     * 覆盖已提交评价的正文、图片与标签。
     *
     * <p>条件中带 {@code committed = true}：仍在提交流程中（正文已写、元数据未提交）的文档不会被编辑，
     * 避免把一次尚未生效的评价改成新内容。评分不在此处维护，评分由 MySQL 元数据事务负责。</p>
     *
     * <p>返回匹配条数而不是修改条数：内容和原来完全一致时修改条数为 0，
     * 但文档确实存在，调用方需要据此区分"文档不存在"和"内容未变"。</p>
     *
     * @param id            评价标识
     * @param body          新正文
     * @param imageFileKeys 新图片文件标识
     * @param tags          新标签
     * @return 匹配到的文档数，正常为 1；文档不存在或尚未提交时为 0
     */
    public long updateContent(String id, String body, List<String> imageFileKeys, List<String> tags) {
        Query query = Query.query(Criteria.where("_id").is(id).and("committed").is(true));
        Update update = new Update()
                .set("body", body)
                .set("imageFileKeys", imageFileKeys)
                .set("tags", tags);
        return mongo.updateFirst(query, update, CommentContentDO.class).getMatchedCount();
    }

    public List<CommentContentDO> findUncommittedOlderThan(java.time.Instant cutoff, int limit) {
        Query query = Query.query(Criteria.where("committed").is(false).and("createdAt").lt(cutoff)).limit(limit);
        return mongo.find(query, CommentContentDO.class);
    }

    public void markCommittedIfPending(String id) {
        Query query = Query.query(Criteria.where("_id").is(id).and("committed").is(false));
        mongo.updateFirst(query, Update.update("committed", true), CommentContentDO.class);
    }

    public void removeIfPending(String id) {
        Query query = Query.query(Criteria.where("_id").is(id).and("committed").is(false));
        mongo.remove(query, CommentContentDO.class);
    }
}
