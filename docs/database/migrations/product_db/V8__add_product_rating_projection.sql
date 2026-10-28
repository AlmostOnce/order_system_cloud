USE product_db;
CREATE TABLE IF NOT EXISTS product_rating_stats (
    product_id BIGINT UNSIGNED NOT NULL COMMENT '菜品ID',
    review_count BIGINT NOT NULL COMMENT '评论服务提供的已发布评价总数',
    score_total BIGINT NOT NULL COMMENT '评论服务提供的评分总和',
    revision BIGINT NOT NULL COMMENT '已应用的源评分版本，用于重复和乱序消费保护',
    PRIMARY KEY(product_id),
    CONSTRAINT ck_product_rating CHECK(review_count>0 AND score_total>=review_count AND score_total<=review_count*5 AND revision>0)
) ENGINE=InnoDB COMMENT='菜品评分异步读模型，事实来源为评论服务';
