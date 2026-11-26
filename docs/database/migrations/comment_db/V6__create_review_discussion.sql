USE comment_db;

-- 评价自由评论区：一条评价对应任意多条评论，不再限制"每条评价仅一条商家回复"。
-- 仅新增独立表，不修改 comments、window_reviews，也不影响评分汇总与评分 outbox。
CREATE TABLE IF NOT EXISTS review_discussion
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '内部顺序主键，不对外暴露，用于游标分页',
    discussion_id CHAR(36)     NOT NULL COMMENT '公开评论标识',
    review_type   VARCHAR(16)  NOT NULL COMMENT '所属评价类型：COMMENT 菜品评价，WINDOW_REVIEW 店铺评价',
    review_id     CHAR(36)     NOT NULL COMMENT '所属评价公开标识，对应 comments.comment_id 或 window_reviews.review_id',
    window_id     BIGINT       NOT NULL COMMENT '评价所属档口编号，冗余用于数据范围判定与按档口检索，不建跨服务外键',
    root_id       CHAR(36)     NOT NULL COMMENT '所属楼层根评论标识；顶层评论的 root_id 等于自身 discussion_id',
    parent_id     CHAR(36)     NULL COMMENT '被直接回复的评论标识；顶层评论为 NULL',
    depth         TINYINT      NOT NULL COMMENT '楼层深度：1 顶层评论，2 楼中楼回复；不开放更深层级',
    author_id     BIGINT       NOT NULL COMMENT '评论人内部用户编号，不对外暴露',
    author_role   VARCHAR(24)  NOT NULL COMMENT '评论时的角色快照：CUSTOMER/STALL_MANAGER/RESTAURANT_MANAGER/GENERAL_MANAGER/SYSTEM_ADMIN',
    author_name   VARCHAR(64)  NOT NULL COMMENT '评论时的展示名快照：学生昵称或商家档口名',
    content       VARCHAR(500) NOT NULL COMMENT '评论正文，纯文本，前端按文本渲染不解释 HTML',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 已发布，2 作者撤回，3 管理员屏蔽',
    version       INT          NOT NULL DEFAULT 1 COMMENT '内容版本，每次编辑或状态变化递增，用于乐观并发与通知幂等',
    request_key   CHAR(36)     NOT NULL COMMENT '客户端幂等请求 UUID',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次发布时间',
    updated_at    DATETIME(3)  NULL COMMENT '最近编辑、撤回或屏蔽时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_discussion_public (discussion_id),
    -- 同一作者同一请求键只落一条，吸收网络重试与客户端重复提交。
    UNIQUE KEY uk_review_discussion_request (author_id, request_key),
    -- 评价下的楼层列表：按评价过滤后取 depth=1，再按 id 倒序游标分页。
    KEY idx_review_discussion_review (review_type, review_id, depth, status, id),
    -- 楼层内回复列表：固定 root_id 后按 id 正序游标分页。
    KEY idx_review_discussion_root (root_id, status, id),
    -- 按档口做运营检索与批量计数。
    KEY idx_review_discussion_window (window_id, status, id),
    CONSTRAINT ck_review_discussion_status CHECK (status IN (1, 2, 3)),
    CONSTRAINT ck_review_discussion_depth CHECK (depth IN (1, 2)),
    CONSTRAINT ck_review_discussion_version CHECK (version > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='评价的自由评论区，一条评价对应多条评论';
