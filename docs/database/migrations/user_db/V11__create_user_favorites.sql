-- 学生收藏关系只保存窗口/菜品引用，不复制商品文案或图片。
-- 跨服务目标不建外键；窗口停用或菜品下架时仍保留关系，供用户查看并移除。
USE user_db;

CREATE TABLE IF NOT EXISTS user_favorites (
    favorite_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏关系内部编号，同时作为稳定分页游标',
    user_id BIGINT NOT NULL COMMENT '收藏所属用户的内部编号',
    target_type VARCHAR(16) NOT NULL COMMENT '收藏目标类型：WINDOW窗口店铺、PRODUCT菜品',
    target_id BIGINT NOT NULL COMMENT '商品服务中的窗口ID或菜品ID，不建立跨服务外键',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏创建时间',
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uk_user_favorite_target (user_id, target_type, target_id)
        COMMENT '同一用户对同一类型目标只保留一条收藏关系',
    KEY idx_user_favorite_page (user_id, target_type, favorite_id)
        COMMENT '支持按用户和类型使用收藏编号倒序游标分页'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='学生收藏的窗口店铺与菜品关系';
