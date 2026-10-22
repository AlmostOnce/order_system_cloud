-- 必须在部署新的订单持久化代码前执行；没有此表时支付状态更新会回滚。
CREATE TABLE IF NOT EXISTS sales_event_outbox (
    event_id VARCHAR(80) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    payload JSON NOT NULL,
    attempts BIGINT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME NULL,
    last_error VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_sales_event_order (order_id),
    KEY idx_sales_event_pending (published_at, next_attempt_at),
    CONSTRAINT fk_sales_event_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
