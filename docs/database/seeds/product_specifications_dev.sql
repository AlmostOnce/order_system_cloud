-- 开发库规格示例：仅为本项目已有 DEV-/DEMO- 测试菜品补充常见选择，不触碰真实编码菜品。
-- 基础售价不变；加价仅用于验证服务端加价规则，店主后续可在管理端按真实菜单调整。
-- 每道目标菜品只在当前没有启用规格时初始化；重复执行不会覆盖店主已保存的配置。
-- 执行前先应用 product_db V12；本脚本只插入规格组与选项，不改商品、订单或库存数据。
SET NAMES utf8mb4;
USE product_db;

CREATE
TEMPORARY TABLE seed_eligible_products (
    product_id BIGINT NOT NULL PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL
);

-- 仅处理明确属于本地演示目录的在售菜品；没有规格的其它测试/真实菜品仍保留直接加购。
INSERT INTO seed_eligible_products (product_id, product_code)
SELECT p.product_id, p.product_code
FROM products p
WHERE p.sale_status = 1
  AND (
    p.product_code LIKE 'DEV-C1-W1-P%'
        OR p.product_code LIKE 'DEV-C1-W2-P%'
        OR p.product_code LIKE 'DEV-C1-W3-P%'
        OR p.product_code LIKE 'DEV-C2-W1-P%'
        OR p.product_code LIKE 'DEV-C2-W2-P%'
        OR p.product_code LIKE 'DEV-C2-W3-P%'
        OR p.product_code LIKE 'DEV-C3-W1-P%'
        OR p.product_code LIKE 'DEV-C3-W2-P%'
        OR p.product_code LIKE 'DEV-C3-W3-P%'
        OR p.product_code IN ('DEMO-RICE-01', 'DEMO-DRINK-01')
    )
  AND NOT EXISTS (SELECT 1
                  FROM product_specification_groups existing_group
                  WHERE existing_group.product_id = p.product_id
                    AND existing_group.status = 1);

CREATE
TEMPORARY TABLE seed_group_rules (
    product_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    is_required TINYINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (product_id, name)
);

CREATE
TEMPORARY TABLE seed_group_templates (
    product_code_pattern VARCHAR(64) NOT NULL,
    name VARCHAR(50) NOT NULL,
    is_required TINYINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (product_code_pattern, name)
);

-- 每种窗口菜系套用对应模板；只读一次候选临时表，兼容 MySQL 禁止同一语句重复打开临时表的限制。
INSERT INTO seed_group_templates (product_code_pattern, name, is_required, sort_order)
VALUES ('DEV-C1-W2-P%', '辣度', 0, 10),
       ('DEV-C1-W3-P%', '面量', 1, 10),
       ('DEV-C1-W3-P%', '辣度', 0, 20),
       ('DEV-C2-W3-P%', '粉量', 1, 10),
       ('DEV-C2-W3-P%', '辣度', 0, 20),
       ('DEV-C2-W1-P%', '加饭', 0, 10),
       ('DEV-C2-W2-P%', '加饭', 0, 10),
       ('DEV-C3-W1-P%', '加饭', 0, 10),
       ('DEV-C3-W2-P%', '份量', 1, 10),
       ('DEV-C3-W2-P%', '蘸料', 0, 20),
       ('DEV-C3-W3-P%', '酱汁', 0, 10),
       ('DEV-C1-W1-P02', '辣度', 0, 10),
       ('DEV-C1-W1-P03', '辣度', 0, 10),
       ('DEV-C1-W1-P06', '辣度', 0, 10),
       ('DEMO-RICE-01', '份量', 1, 10),
       ('DEMO-DRINK-01', '温度', 1, 10),
       ('DEMO-DRINK-01', '甜度', 0, 20);

-- 香锅提供辣度；面、粉可加量，口味可选；必选份量组以标准份零加价作为基础价组合。
-- 蒸菜饭、铁板饭与盖饭可按需加饭；饺子、轻食和饮品按菜品属性展示份量、蘸料、酱汁或温度。
INSERT INTO seed_group_rules (product_id, name, is_required, sort_order)
SELECT eligible.product_id, template.name, template.is_required, template.sort_order
FROM seed_eligible_products eligible
         JOIN seed_group_templates template ON eligible.product_code LIKE template.product_code_pattern;

CREATE
TEMPORARY TABLE seed_option_rules (
    group_name VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    additional_price DECIMAL(10, 2) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (group_name, name)
);

INSERT INTO seed_option_rules (group_name, name, additional_price, sort_order)
VALUES ('辣度', '不辣', 0.00, 10),
       ('辣度', '微辣', 0.00, 20),
       ('辣度', '中辣', 0.00, 30),
       ('辣度', '特辣', 0.00, 40),
       ('面量', '标准份', 0.00, 10),
       ('面量', '加面', 3.00, 20),
       ('粉量', '标准份', 0.00, 10),
       ('粉量', '加粉', 3.00, 20),
       ('加饭', '不加饭', 0.00, 10),
       ('加饭', '加一份米饭', 2.00, 20),
       ('份量', '标准份', 0.00, 10),
       ('份量', '加量份', 5.00, 20),
       ('蘸料', '不加蘸料', 0.00, 10),
       ('蘸料', '香醋', 0.00, 20),
       ('蘸料', '红油', 0.00, 30),
       ('酱汁', '原味酱汁', 0.00, 10),
       ('酱汁', '酱汁另放', 0.00, 20),
       ('酱汁', '额外酱汁', 1.00, 30),
       ('温度', '热', 0.00, 10),
       ('温度', '常温', 0.00, 20),
       ('温度', '冰', 0.00, 30),
       ('甜度', '无糖', 0.00, 10),
       ('甜度', '少糖', 0.00, 20),
       ('甜度', '正常糖', 0.00, 30);

START TRANSACTION;

-- 目标菜品若已有任何启用组就完全跳过，避免示例脚本混入或覆盖人工配置。
INSERT INTO product_specification_groups (product_id, name, is_required, sort_order, status)
SELECT rules.product_id, rules.name, rules.is_required, rules.sort_order, 1
FROM seed_group_rules rules
         JOIN seed_eligible_products eligible ON eligible.product_id = rules.product_id;

-- 所有配置与选项在一个事务中写入；启用组关联到刚按规则创建的同菜同名组。
INSERT INTO product_specification_options (group_id, name, additional_price, sort_order, status)
SELECT spec_group.group_id, option_rule.name, option_rule.additional_price, option_rule.sort_order, 1
FROM seed_group_rules group_rule
         JOIN product_specification_groups spec_group
              ON spec_group.product_id = group_rule.product_id
                  AND spec_group.name = group_rule.name
                  AND spec_group.status = 1
         JOIN seed_option_rules option_rule ON option_rule.group_name = group_rule.name
         JOIN seed_eligible_products eligible ON eligible.product_id = group_rule.product_id;

COMMIT;

-- 输出初始化覆盖面供执行后核对；总数只包含本次开发示例菜品的启用规格。
SELECT COUNT(DISTINCT spec_group.product_id) AS initialized_product_count,
       COUNT(DISTINCT spec_group.group_id)   AS initialized_group_count,
       COUNT(spec_option.option_id)          AS initialized_option_count
FROM product_specification_groups spec_group
         JOIN products product ON product.product_id = spec_group.product_id
         LEFT JOIN product_specification_options spec_option
                   ON spec_option.group_id = spec_group.group_id AND spec_option.status = 1
WHERE spec_group.status = 1
  AND (product.product_code LIKE 'DEV-C1-W1-P%'
    OR product.product_code LIKE 'DEV-C1-W2-P%'
    OR product.product_code LIKE 'DEV-C1-W3-P%'
    OR product.product_code LIKE 'DEV-C2-W1-P%'
    OR product.product_code LIKE 'DEV-C2-W2-P%'
    OR product.product_code LIKE 'DEV-C2-W3-P%'
    OR product.product_code LIKE 'DEV-C3-W1-P%'
    OR product.product_code LIKE 'DEV-C3-W2-P%'
    OR product.product_code LIKE 'DEV-C3-W3-P%'
    OR product.product_code IN ('DEMO-RICE-01', 'DEMO-DRINK-01'));

DROP
TEMPORARY TABLE seed_option_rules;
DROP
TEMPORARY TABLE seed_group_templates;
DROP
TEMPORARY TABLE seed_group_rules;
DROP
TEMPORARY TABLE seed_eligible_products;
