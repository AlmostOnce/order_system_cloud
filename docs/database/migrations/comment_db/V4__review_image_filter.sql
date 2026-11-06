-- 图片筛选投影：Mongo正文仍是图片引用来源，MySQL标记用于先筛选再分页。
-- comments的NULL表示尚未回填，不能冒充无图；须配合tools/migrate-review-image-filter.py执行历史回填。
USE comment_db;
SET @review_image_sql=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='comments' AND column_name='has_images'),
    'SELECT 1', 'ALTER TABLE comments ADD COLUMN has_images TINYINT NULL DEFAULT NULL COMMENT ''是否带图，NULL待回填，0无图，1有图；与元数据事务写入''');
PREPARE review_image_stmt FROM @review_image_sql;
EXECUTE review_image_stmt;
DEALLOCATE PREPARE review_image_stmt;
-- 店铺图片本来就在MySQL，生成列保证新旧代码写入都自动保持一致。
SET @review_image_sql=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='window_reviews' AND column_name='has_images'),
    'SELECT 1', 'ALTER TABLE window_reviews ADD COLUMN has_images TINYINT GENERATED ALWAYS AS (JSON_LENGTH(image_file_keys)>0) STORED COMMENT ''是否带图，依据图片数组自动生成''');
PREPARE review_image_stmt FROM @review_image_sql;
EXECUTE review_image_stmt;
DEALLOCATE PREPARE review_image_stmt;

SET @review_image_sql=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='comments' AND index_name='idx_comment_product_images'),
    'SELECT 1', 'ALTER TABLE comments ADD INDEX idx_comment_product_images (product_id,status,has_images,id)');
PREPARE review_image_stmt FROM @review_image_sql;
EXECUTE review_image_stmt;
DEALLOCATE PREPARE review_image_stmt;

SET @review_image_sql=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='comments' AND index_name='idx_comment_user_images'),
    'SELECT 1', 'ALTER TABLE comments ADD INDEX idx_comment_user_images (user_id,status,has_images,id)');
PREPARE review_image_stmt FROM @review_image_sql;
EXECUTE review_image_stmt;
DEALLOCATE PREPARE review_image_stmt;

SET @review_image_sql=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='window_reviews' AND index_name='idx_window_review_window_images'),
    'SELECT 1', 'ALTER TABLE window_reviews ADD INDEX idx_window_review_window_images (window_id,has_images,id)');
PREPARE review_image_stmt FROM @review_image_sql;
EXECUTE review_image_stmt;
DEALLOCATE PREPARE review_image_stmt;

SET @review_image_sql=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='window_reviews' AND index_name='idx_window_review_user_images'),
    'SELECT 1', 'ALTER TABLE window_reviews ADD INDEX idx_window_review_user_images (user_id,has_images,id)');
PREPARE review_image_stmt FROM @review_image_sql;
EXECUTE review_image_stmt;
DEALLOCATE PREPARE review_image_stmt;
