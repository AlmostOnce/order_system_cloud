-- 评价修改与删除所需的结构调整。
-- 设计要点：
--   1. 删除采用软删除：comments 用 status 标记，window_reviews 新增 status 列；
--      评价正文与图片保留，便于追责与申诉，不做物理删除。
--   2. 评分汇总量在删掉最后一条评价时会降到 0，因此把 review_count>0 的约束放宽到 >=0，
--      同时保留 score_total 与条数的区间关系，避免出现"条数为零却有总分"的脏数据。
--   3. updated_at 只记录最后一次修改时间，未修改过保持 NULL，不用创建时间兜底，
--      这样客户端与服务端都能区分"从未编辑"和"改回原内容"。
-- 全部语句按 information_schema 判断后再执行，重复执行不会报错。
USE comment_db;

-- comments：新增最后修改时间。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'comments' AND column_name = 'updated_at'),
              'SELECT 1',
              'ALTER TABLE comments ADD COLUMN updated_at DATETIME(6) NULL COMMENT ''最后修改时间；未修改过为 NULL''');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

-- comments：放开状态约束。1=已发布，2=已删除（软删除，正文保留）。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
                     WHERE table_schema = DATABASE() AND table_name = 'comments' AND constraint_name = 'ck_comment_status'),
              'ALTER TABLE comments DROP CHECK ck_comment_status',
              'SELECT 1');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
                     WHERE table_schema = DATABASE() AND table_name = 'comments' AND constraint_name = 'ck_comment_status'),
              'SELECT 1',
              'ALTER TABLE comments ADD CONSTRAINT ck_comment_status CHECK (status IN (1,2))');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

-- 菜品评分汇总：允许评价被全部删除后的零值状态。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
                     WHERE table_schema = DATABASE() AND table_name = 'comment_rating_stats' AND constraint_name = 'ck_comment_rating'),
              'ALTER TABLE comment_rating_stats DROP CHECK ck_comment_rating',
              'SELECT 1');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
                     WHERE table_schema = DATABASE() AND table_name = 'comment_rating_stats' AND constraint_name = 'ck_comment_rating'),
              'SELECT 1',
              'ALTER TABLE comment_rating_stats ADD CONSTRAINT ck_comment_rating CHECK (review_count >= 0 AND score_total >= 0 AND score_total <= review_count * 5 AND (review_count > 0 OR score_total = 0) AND revision > 0)');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

-- window_reviews：新增软删除状态与修改时间。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'window_reviews' AND column_name = 'status'),
              'SELECT 1',
              'ALTER TABLE window_reviews ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''可见状态：1 已发布，2 已删除''');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'window_reviews' AND column_name = 'updated_at'),
              'SELECT 1',
              'ALTER TABLE window_reviews ADD COLUMN updated_at DATETIME(6) NULL COMMENT ''最后修改时间；未修改过为 NULL''');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
                     WHERE table_schema = DATABASE() AND table_name = 'window_reviews' AND constraint_name = 'ck_window_review_status'),
              'SELECT 1',
              'ALTER TABLE window_reviews ADD CONSTRAINT ck_window_review_status CHECK (status IN (1,2))');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

-- 列表与统计都带 status 条件，索引前补 status 避免回表后再过滤。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics
                     WHERE table_schema = DATABASE() AND table_name = 'window_reviews' AND index_name = 'idx_window_review_window_status'),
              'SELECT 1',
              'ALTER TABLE window_reviews ADD INDEX idx_window_review_window_status (window_id, status, id)');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics
                     WHERE table_schema = DATABASE() AND table_name = 'window_reviews' AND index_name = 'idx_window_review_user_status'),
              'SELECT 1',
              'ALTER TABLE window_reviews ADD INDEX idx_window_review_user_status (user_id, status, id)');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;
