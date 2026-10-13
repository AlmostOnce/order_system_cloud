USE user_db;

-- 用户数据范围授权表。
-- 角色（role_code）表达"能执行哪类操作"，本表表达"能操作哪些数据"，
-- 二者必须同时校验；该表是数据范围的唯一事实来源，不放入 JWT。
-- scope_type 取值：WINDOW（scope_ref=window_id）、
--                  RESTAURANT（scope_ref=restaurant_id）、
--                  PLATFORM（scope_ref=0，全平台）。
-- scope_ref 指向的档口/餐厅位于 product_db，MySQL 无法跨库建外键，
-- 其有效性由分配范围时调用 product-service 内部接口校验保证。
CREATE TABLE IF NOT EXISTS user_data_scopes (
    scope_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_ref BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (scope_id),
    UNIQUE KEY uk_user_data_scopes (user_id, scope_type, scope_ref),
    KEY idx_user_data_scopes_user (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
