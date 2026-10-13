USE product_db;

-- 餐厅层级：restaurant 1 → N dining_window。
-- 新增该表后，RESTAURANT_MANAGER / GENERAL_MANAGER 才有可管辖的餐厅维度。
CREATE TABLE IF NOT EXISTS restaurants (
    restaurant_id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_code VARCHAR(32) NOT NULL,
    restaurant_name VARCHAR(64) NOT NULL,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (restaurant_id),
    UNIQUE KEY uk_restaurants_code (restaurant_code),
    CONSTRAINT chk_restaurants_status CHECK (status IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 先插入默认餐厅，保证存量档口有归属，再给餐厅下的档口补 restaurant_id。
INSERT INTO restaurants (restaurant_code, restaurant_name)
SELECT 'R0001', '默认餐厅'
WHERE NOT EXISTS (
    SELECT 1 FROM restaurants WHERE restaurant_code = 'R0001'
);

ALTER TABLE dining_windows
    ADD COLUMN restaurant_id BIGINT NOT NULL DEFAULT 1 AFTER window_id,
    ADD KEY idx_dining_windows_restaurant (restaurant_id),
    ADD CONSTRAINT fk_dining_windows_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (restaurant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
