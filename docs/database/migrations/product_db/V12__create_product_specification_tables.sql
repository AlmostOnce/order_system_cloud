-- 商品规格迁移：只新增表，不改写商品记录；执行前确认目标为开发环境 product_db。
-- IF NOT EXISTS 可避免重复建表，但不能修复同名异构表；重复执行前必须核验实际结构。
USE product_db;

CREATE TABLE IF NOT EXISTS product_specification_groups (
    group_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '规格组编号，完整替换保留已有编号',
    product_id BIGINT NOT NULL COMMENT '所属菜品编号，只能归属一个菜品',
    name VARCHAR(50) NOT NULL COMMENT '规格组名称，启用组在同菜品内唯一',
    is_required TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否必选：1必选、0可选；每组只选一个选项',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序，非负，越小越靠前',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '启用状态：1启用、0停用；移除只停用不删除',
    active_name VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN status = 1 THEN name ELSE NULL END) STORED COMMENT '仅启用记录参与名称唯一约束，停用历史可重名',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (group_id),
    UNIQUE KEY uk_specification_groups_active_name (product_id, active_name) COMMENT '同菜品启用组名唯一，停用记录不占用名称',
    KEY idx_specification_groups_product_status_sort (product_id, status, sort_order, group_id) COMMENT '按菜品批量读取启用组并稳定排序',
    -- 禁止删除仍有关联规格的菜品，保留规格编号的追溯能力。
    CONSTRAINT fk_specification_groups_product FOREIGN KEY (product_id) REFERENCES products (product_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_specification_groups_required CHECK (is_required IN (0, 1)),
    CONSTRAINT chk_specification_groups_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_specification_groups_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品单选规格组，移除时软停用';

CREATE TABLE IF NOT EXISTS product_specification_options (
    option_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '规格选项编号，学生核价只提交该编号',
    group_id BIGINT NOT NULL COMMENT '所属规格组，禁止跨组移动选项',
    name VARCHAR(50) NOT NULL COMMENT '选项名称，启用项在同组内唯一',
    additional_price DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '在菜品基础售价上累加的非负金额，最多两位小数',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序，非负，越小越靠前',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '启用状态：1启用、0停用；移除只停用不删除',
    active_name VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN status = 1 THEN name ELSE NULL END) STORED COMMENT '仅启用记录参与名称唯一约束',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (option_id),
    UNIQUE KEY uk_specification_options_active_name (group_id, active_name) COMMENT '同组启用项名称唯一，允许保留同名停用历史',
    KEY idx_specification_options_group_status_sort (group_id, status, sort_order, option_id) COMMENT '按组批量读取启用选项并稳定排序',
    -- 关联组不允许物理删除；规格管理先按商品归属限定组，再校验选项归属。
    CONSTRAINT fk_specification_options_group FOREIGN KEY (group_id) REFERENCES product_specification_groups (group_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_specification_options_price CHECK (additional_price >= 0),
    CONSTRAINT chk_specification_options_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_specification_options_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品规格选项，保存非负加价并保留停用历史';
