CREATE DATABASE IF NOT EXISTS product_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE product_db;

CREATE TABLE IF NOT EXISTS dining_windows (
    window_id BIGINT NOT NULL AUTO_INCREMENT,
    window_code VARCHAR(32) NOT NULL,
    window_name VARCHAR(64) NOT NULL,
    location VARCHAR(255) NULL,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (window_id),
    UNIQUE KEY uk_dining_windows_code (window_code),
    CONSTRAINT chk_dining_windows_status CHECK (status IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_categories (
    category_id BIGINT NOT NULL AUTO_INCREMENT,
    window_id BIGINT NOT NULL,
    category_name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_product_categories_window_name (window_id, category_name),
    UNIQUE KEY uk_product_categories_window_category (window_id, category_id),
    KEY idx_product_categories_window_status_sort (window_id, status, sort_order),
    CONSTRAINT fk_product_categories_window
        FOREIGN KEY (window_id) REFERENCES dining_windows (window_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_product_categories_status CHECK (status IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
    product_id BIGINT NOT NULL AUTO_INCREMENT,
    window_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    image_url VARCHAR(512) NULL,
    price DECIMAL(10, 2) NOT NULL,
    sale_status TINYINT UNSIGNED NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_products_window_code (window_id, product_code),
    KEY idx_products_window_sale_sort (window_id, sale_status, sort_order),
    KEY idx_products_window_category_sale_sort (window_id, category_id, sale_status, sort_order),
    CONSTRAINT fk_products_window
        FOREIGN KEY (window_id) REFERENCES dining_windows (window_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_products_window_category
        FOREIGN KEY (window_id, category_id)
        REFERENCES product_categories (window_id, category_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_products_price_nonnegative CHECK (price >= 0),
    CONSTRAINT chk_products_sale_status CHECK (sale_status IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
