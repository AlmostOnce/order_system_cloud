-- 菜品目录事务 outbox：与商品表处于同一 MySQL 事务，确保提交后可重试发布索引刷新事件。
USE product_db;

CREATE TABLE IF NOT EXISTS product_search_outbox (
    event_id VARCHAR(36) NOT NULL COMMENT '搜索刷新事件 UUID',
    window_id BIGINT NOT NULL COMMENT '需要重新生成索引的窗口 ID',
    attempts INT NOT NULL DEFAULT 0 COMMENT '发布失败重试次数',
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次允许发布时间',
    published_at TIMESTAMP NULL DEFAULT NULL COMMENT 'Broker 确认接收时间',
    last_error VARCHAR(128) NULL DEFAULT NULL COMMENT '最近一次发布错误类型',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件创建时间',
    PRIMARY KEY (event_id),
    KEY idx_product_search_outbox_pending (published_at, next_attempt_at, created_at),
    KEY idx_product_search_outbox_window (window_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品搜索索引刷新事件发件箱';
