CREATE TABLE IF NOT EXISTS orders (
    order_id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    window_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(255) NULL,
    pickup_code VARCHAR(32) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_created_at (user_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
