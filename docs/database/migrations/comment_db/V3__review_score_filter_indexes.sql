-- 仅新增索引，不改写或删除评价；通过元数据判断，允许在开发环境重复执行。
USE comment_db;

-- comments按对象/本人、分数和游标过滤，保留既有未筛选索引。
SET @review_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='comments' AND index_name='idx_comment_product_score'),
    'SELECT 1', 'ALTER TABLE comments ADD INDEX idx_comment_product_score (product_id,status,score,id)');
PREPARE review_index_stmt FROM @review_index_sql;
EXECUTE review_index_stmt;
DEALLOCATE PREPARE review_index_stmt;

-- comments按对象/本人、分数和游标过滤，保留既有未筛选索引。
SET @review_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='comments' AND index_name='idx_comment_user_score'),
    'SELECT 1', 'ALTER TABLE comments ADD INDEX idx_comment_user_score (user_id,status,score,id)');
PREPARE review_index_stmt FROM @review_index_sql;
EXECUTE review_index_stmt;
DEALLOCATE PREPARE review_index_stmt;

-- window_reviews按对象/本人、分数和游标过滤，保留既有未筛选索引。
SET @review_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='window_reviews' AND index_name='idx_window_review_window_score'),
    'SELECT 1', 'ALTER TABLE window_reviews ADD INDEX idx_window_review_window_score (window_id,score,id)');
PREPARE review_index_stmt FROM @review_index_sql;
EXECUTE review_index_stmt;
DEALLOCATE PREPARE review_index_stmt;

-- window_reviews按对象/本人、分数和游标过滤，保留既有未筛选索引。
SET @review_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='window_reviews' AND index_name='idx_window_review_user_score'),
    'SELECT 1', 'ALTER TABLE window_reviews ADD INDEX idx_window_review_user_score (user_id,score,id)');
PREPARE review_index_stmt FROM @review_index_sql;
EXECUTE review_index_stmt;
DEALLOCATE PREPARE review_index_stmt;
