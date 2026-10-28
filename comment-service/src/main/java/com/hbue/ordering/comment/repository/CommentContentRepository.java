package com.hbue.ordering.comment.repository;

import com.hbue.ordering.comment.model.CommentContentDO;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** 评论正文的 MongoDB 访问层，与 MySQL 元数据仓储保持分离。 */
@Repository
public class CommentContentRepository {
    private final MongoTemplate mongo;

    public CommentContentRepository(MongoTemplate mongo) { this.mongo = mongo; }
    public void insert(CommentContentDO content) { mongo.insert(content); }
    public CommentContentDO byId(String id) { return mongo.findById(id, CommentContentDO.class); }
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
