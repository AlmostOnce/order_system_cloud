USE product_db;

-- 仅用于本机开发和接口验证；唯一编码使脚本可以重复执行。
INSERT IGNORE INTO dining_windows (
    window_code,
    window_name,
    location,
    status
) VALUES (
    'W-DEMO-01',
    '一楼测试窗口',
    '一楼东侧',
    1
);

SET @demo_window_id = (
    SELECT window_id
    FROM dining_windows
    WHERE window_code = 'W-DEMO-01'
);

INSERT INTO product_categories (
    window_id,
    category_name,
    sort_order,
    status
) VALUES
    (@demo_window_id, '主食', 10, 1),
    (@demo_window_id, '饮品', 20, 1),
    (@demo_window_id, '停用分类示例', 30, 0)
ON DUPLICATE KEY UPDATE
    sort_order = VALUES(sort_order),
    status = VALUES(status);

SET @main_category_id = (
    SELECT category_id
    FROM product_categories
    WHERE window_id = @demo_window_id
      AND category_name = '主食'
);
SET @drink_category_id = (
    SELECT category_id
    FROM product_categories
    WHERE window_id = @demo_window_id
      AND category_name = '饮品'
);
SET @disabled_category_id = (
    SELECT category_id
    FROM product_categories
    WHERE window_id = @demo_window_id
      AND category_name = '停用分类示例'
);

INSERT INTO products (
    window_id,
    category_id,
    product_code,
    product_name,
    description,
    price,
    sale_status,
    sort_order
) VALUES
    (
        @demo_window_id,
        @main_category_id,
        'DEMO-RICE-01',
        '鸡肉饭',
        '开发环境示例菜品',
        18.50,
        1,
        10
    ),
    (
        @demo_window_id,
        @drink_category_id,
        'DEMO-DRINK-01',
        '豆浆',
        '开发环境示例饮品',
        3.00,
        1,
        10
    ),
    (
        @demo_window_id,
        NULL,
        'DEMO-UNCAT-01',
        '瓶装水',
        '未关联分类的示例商品',
        2.00,
        1,
        20
    ),
    (
        @demo_window_id,
        @disabled_category_id,
        'DEMO-HIDDEN-01',
        '隐藏分类菜品',
        '停用分类下的示例商品',
        9.00,
        1,
        30
    ),
    (
        @demo_window_id,
        @main_category_id,
        'DEMO-OFFSALE-01',
        '已下架示例菜品',
        '不应出现在顾客菜单中',
        12.00,
        0,
        40
    )
ON DUPLICATE KEY UPDATE
    category_id = VALUES(category_id),
    product_name = VALUES(product_name),
    description = VALUES(description),
    price = VALUES(price),
    sale_status = VALUES(sale_status),
    sort_order = VALUES(sort_order);
