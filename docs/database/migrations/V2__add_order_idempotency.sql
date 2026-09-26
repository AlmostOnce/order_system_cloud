ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD COLUMN request_hash CHAR(64) NULL,
    ADD UNIQUE KEY uk_orders_user_idempotency_key (user_id, idempotency_key);
