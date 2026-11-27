-- 首页公告：平台级内容，与学生端首页的餐厅筛选无关。
-- 与 restaurants.notice（餐厅简介）相互独立：本表有发布状态和生效时间窗口，
-- 只由总经理/系统管理员在 Vue 管理端维护，学生端只读。
USE product_db;

CREATE TABLE IF NOT EXISTS home_announcements
(
    announcement_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公告内部编号',
    title           VARCHAR(60)  NOT NULL COMMENT '公告标题，首页公告条与弹层展示',
    content         VARCHAR(500) NOT NULL COMMENT '公告正文，纯文本，不支持富文本',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 草稿、1 已发布、2 已下线',
    start_at        DATETIME     NOT NULL COMMENT '生效开始时间，按服务端本地时间解释',
    end_at          DATETIME     NULL COMMENT '生效结束时间，NULL 表示长期有效直到手动下线',
    published_at    DATETIME     NULL COMMENT '最近一次发布时间；多条同时生效时取该值最新的一条',
    created_by      BIGINT       NOT NULL COMMENT '创建人用户编号，仅审计使用',
    updated_by      BIGINT       NOT NULL COMMENT '最后修改人用户编号，仅审计使用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (announcement_id),
    KEY idx_home_announcements_active (status, published_at),
    KEY idx_home_announcements_window (status, start_at, end_at),
    CONSTRAINT chk_home_announcements_status CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_home_announcements_window CHECK (end_at IS NULL OR end_at > start_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '学生端首页公告';
