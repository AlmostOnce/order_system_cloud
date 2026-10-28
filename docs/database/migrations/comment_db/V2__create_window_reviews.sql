USE comment_db;

-- 店铺评价按订单独立保存，不改变已有菜品评价。
CREATE TABLE IF NOT EXISTS window_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    review_id CHAR(36) NOT NULL COMMENT '公开评价标识',
    user_id BIGINT NOT NULL COMMENT '评价用户内部编号',
    author_name VARCHAR(64) NOT NULL COMMENT '评价时的用户昵称',
    order_id BIGINT NOT NULL COMMENT '已完成订单编号',
    window_id BIGINT NOT NULL COMMENT '所属窗口编号',
    score TINYINT NOT NULL COMMENT '店铺综合评分，1至5分',
    content VARCHAR(1000) NOT NULL COMMENT '评价正文',
    image_file_keys JSON NOT NULL COMMENT '评价图片文件标识',
    tags JSON NOT NULL COMMENT '评价标签',
    request_key CHAR(36) NOT NULL COMMENT '提交幂等标识',
    request_hash CHAR(64) NOT NULL COMMENT '提交内容摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_window_review_id (review_id),
    UNIQUE KEY uk_window_review_order (order_id),
    UNIQUE KEY uk_window_review_request (user_id, request_key),
    KEY idx_window_review_window (window_id, id),
    KEY idx_window_review_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单对应的店铺评价';
