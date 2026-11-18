-- 首页推荐独立读模型。只保存推荐服务自己的投影，不跨库读写订单、用户或商品事实表。
CREATE
DATABASE IF NOT EXISTS recommendation_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE recommendation_db;

CREATE TABLE IF NOT EXISTS recommendation_order_snapshots
(
    order_id
    BIGINT
    NOT
    NULL
    COMMENT
    '订单服务源订单主键',
    user_id
    BIGINT
    NOT
    NULL
    COMMENT
    '验签后解析出的内部用户主键',
    window_id
    BIGINT
    NOT
    NULL
    COMMENT
    '下单窗口',
    order_version
    INT
    NOT
    NULL
    COMMENT
    '源订单单调版本，用于拒绝重复及乱序快照',
    paid_at
    DATETIME
    NOT
    NULL
    COMMENT
    '首次支付成功时间，退款不改变该时间',
    updated_at
    DATETIME
    NOT
    NULL
    DEFAULT
    CURRENT_TIMESTAMP
    ON
    UPDATE
    CURRENT_TIMESTAMP
    COMMENT
    '投影更新时间',
    PRIMARY
    KEY
(
    order_id
),
    KEY idx_recommendation_order_user_paid
(
    user_id,
    paid_at
    DESC,
    order_id
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_0900_ai_ci COMMENT='当前用户订单推荐快照';

CREATE TABLE IF NOT EXISTS recommendation_order_items
(
    item_id
    BIGINT
    NOT
    NULL
    COMMENT
    '源订单明细主键，部分退款定位到该行',
    order_id
    BIGINT
    NOT
    NULL
    COMMENT
    '所属源订单主键',
    product_id
    BIGINT
    NOT
    NULL
    COMMENT
    '推荐粒度为菜品，不按规格拆分',
    selection_key
    VARCHAR
(
    256
) NOT NULL DEFAULT 'base' COMMENT '下单时规格选项组合键',
    specification_text_snapshot VARCHAR
(
    1024
) NOT NULL DEFAULT '' COMMENT '下单时规格显示文字',
    specification_selection_snapshot JSON NULL COMMENT '下单时服务端确认的结构化组/选项文字快照',
    purchased_quantity INT NOT NULL COMMENT '支付订单该明细原购买份数',
    refunded_quantity INT NOT NULL DEFAULT 0 COMMENT '资金确认成功且已撤销的该行份数',
    PRIMARY KEY
(
    item_id
),
    KEY idx_recommendation_items_order
(
    order_id,
    product_id
),
    KEY idx_recommendation_items_product
(
    product_id
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_0900_ai_ci COMMENT='订单菜品及规格级推荐事实投影';

CREATE TABLE IF NOT EXISTS recommendation_favorite_states
(
    user_id
    BIGINT
    NOT
    NULL
    COMMENT
    '内部用户主键',
    target_type
    VARCHAR
(
    16
) NOT NULL COMMENT 'PRODUCT或WINDOW',
    target_id BIGINT NOT NULL COMMENT '菜品或窗口主键',
    source_version BIGINT NOT NULL COMMENT '收藏Outbox自增ID数值版本，不能按字符串字典序比较',
    active TINYINT
(
    1
) NOT NULL COMMENT '当前是否收藏；取消墓碑也保留以压过迟到旧事件',
    changed_at DATETIME NOT NULL COMMENT '源收藏状态变更时间',
    PRIMARY KEY
(
    user_id,
    target_type,
    target_id
),
    KEY idx_recommendation_favorite_user
(
    user_id,
    active,
    target_type
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_0900_ai_ci COMMENT='当前收藏状态投影';

CREATE TABLE IF NOT EXISTS recommendation_search_intents
(
    intent_id
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
    '搜索意图本地编号',
    user_id
    BIGINT
    NOT
    NULL
    COMMENT
    '由搜索服务端认证上下文解析的内部用户主键',
    query_id
    VARCHAR
(
    64
) NOT NULL COMMENT '一次主动首屏搜索的幂等标识',
    keyword VARCHAR
(
    80
) NOT NULL COMMENT '短期保留的搜索关键词，不得写入普通日志',
    restaurant_id BIGINT NULL COMMENT '搜索时指定的餐厅范围',
    window_id BIGINT NULL COMMENT '搜索时指定的窗口范围',
    category_id BIGINT NULL COMMENT '搜索时指定的分类范围',
    ranked_product_ids JSON NOT NULL COMMENT '成功首屏结果菜品ID按检索顺序排列，最多20项',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '搜索成功时间，用于14天保留和时间衰减',
    PRIMARY KEY
(
    intent_id
),
    UNIQUE KEY uk_recommendation_search_query
(
    user_id,
    query_id
),
    KEY idx_recommendation_search_user_time
(
    user_id,
    created_at
    DESC,
    intent_id
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_0900_ai_ci COMMENT='短期主动搜索意图';

CREATE TABLE IF NOT EXISTS recommendation_product_features
(
    product_id
    BIGINT
    NOT
    NULL
    COMMENT
    '商品服务菜品主键',
    category_id
    BIGINT
    NULL
    COMMENT
    '商品分类主键',
    window_id
    BIGINT
    NOT
    NULL
    COMMENT
    '所属窗口主键',
    restaurant_id
    BIGINT
    NOT
    NULL
    COMMENT
    '所属餐厅主键',
    window_name
    VARCHAR
(
    100
) NOT NULL DEFAULT '' COMMENT '窗口展示名称快照',
    category_name VARCHAR
(
    100
) NULL COMMENT '分类展示名称快照',
    product_name VARCHAR
(
    100
) NOT NULL COMMENT '菜名文本特征',
    description VARCHAR
(
    1000
) NULL COMMENT '菜品描述文本特征',
    image_url VARCHAR
(
    512
) NULL COMMENT '当前展示图片地址',
    base_price DECIMAL
(
    10,
    2
) NOT NULL COMMENT '当前商品基础售价，不代表已选规格的最终报价',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '目录稳定顺序，用于同分时确定性排序',
    has_specifications TINYINT
(
    1
) NOT NULL DEFAULT 0 COMMENT '当前是否有启用规格组',
    specification_groups JSON NULL COMMENT '当前可选规格，仅作明确口味匹配的候选能力',
    available TINYINT
(
    1
) NOT NULL DEFAULT 1 COMMENT '商品域当前目录状态；下架快照保留作历史画像特征',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '目录快照更新时间',
    PRIMARY KEY
(
    product_id
),
    KEY idx_recommendation_product_window
(
    window_id,
    available,
    sort_order,
    product_id
),
    KEY idx_recommendation_product_restaurant
(
    restaurant_id,
    available,
    sort_order,
    product_id
),
    KEY idx_recommendation_product_category
(
    window_id,
    category_id,
    available
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_0900_ai_ci COMMENT='推荐服务商品内容与当前展示读模型';

CREATE TABLE IF NOT EXISTS recommendation_hot_products
(
    product_id
    BIGINT
    NOT
    NULL
    COMMENT
    '统计服务榜单菜品主键',
    window_id
    BIGINT
    NOT
    NULL
    COMMENT
    '榜单窗口主键',
    sales
    BIGINT
    NOT
    NULL
    COMMENT
    '统计服务定义的累计已支付份数',
    rank_no
    INT
    NOT
    NULL
    COMMENT
    '统计服务当前排名',
    updated_at
    DATETIME
    NOT
    NULL
    DEFAULT
    CURRENT_TIMESTAMP
    COMMENT
    '最近一次成功读取榜单时间',
    PRIMARY
    KEY
(
    product_id
),
    KEY idx_recommendation_hot_rank
(
    rank_no,
    product_id
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_0900_ai_ci COMMENT='统计服务热门菜品短期投影';

-- 推荐运行账号只读写本服务数据库，不授予其他业务库或全局管理权限。
CREATE
USER IF NOT EXISTS 'recommendation_service'@'%' IDENTIFIED BY 'recommendation_password_dev_2026';
GRANT
SELECT,
INSERT
,
UPDATE,
DELETE
ON recommendation_db.* TO 'recommendation_service'@'%';
