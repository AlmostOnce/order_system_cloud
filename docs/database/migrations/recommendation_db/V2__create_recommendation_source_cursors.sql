-- 推荐服务历史订单和收藏分批回填断点；仅保存本服务的源读取位置。
USE recommendation_db;

CREATE TABLE IF NOT EXISTS recommendation_source_cursors (
    source_name VARCHAR(32) NOT NULL COMMENT '受信任的固定源名称：purchase-orders或favorite-relations',
    cursor_value BIGINT NOT NULL DEFAULT 0 COMMENT '最后完整投影的源主键，按数值递增且不回退',
    upper_bound BIGINT NULL DEFAULT NULL COMMENT '收藏首屏捕获的favorite_id上界；订单源为空',
    completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '首轮历史扫描是否到达末页；之后变化由可靠实时事件补齐',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次成功推进断点时间',
    PRIMARY KEY (source_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐历史数据源回填断点';

INSERT IGNORE INTO recommendation_source_cursors(source_name, cursor_value, upper_bound, completed)
VALUES ('purchase-orders', 0, NULL, 0), ('favorite-relations', 0, NULL, 0);
