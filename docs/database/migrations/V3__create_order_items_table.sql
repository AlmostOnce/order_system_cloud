CREATE TABLE IF NOT EXISTS order_items (
    item_id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT NOT NULL,
    product_code_snapshot VARCHAR(64) NOT NULL,
    product_name_snapshot VARCHAR(128) NOT NULL,
    unit_price_snapshot DECIMAL(10, 2) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    line_amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id),
    KEY idx_order_items_order_id (order_id),
    KEY idx_order_items_product_id (product_id),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (order_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_unit_price_nonnegative
        CHECK (unit_price_snapshot >= 0),
    CONSTRAINT chk_order_items_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT chk_order_items_line_amount_nonnegative
        CHECK (line_amount >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
