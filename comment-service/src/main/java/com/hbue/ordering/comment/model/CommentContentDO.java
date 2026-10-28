package com.hbue.ordering.comment.model;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
/** 正文聚合，只有对应 MySQL 元数据提交后才对外可见。 */
@Document("comments")
public record CommentContentDO(@Id String id, String body, List<String> imageFileKeys,
        List<String> tags, Instant createdAt, boolean committed) {}
