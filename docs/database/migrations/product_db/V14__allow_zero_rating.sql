-- 菜品评分投影允许"零评价"状态。
-- 评论服务删除某菜品的最后一条评价时，会推送 review_count=0、score_total=0 的全量快照，
-- 原有 ck_product_rating 要求 review_count>0，会把合法的清零事件拒掉，
-- 因此放宽为 >=0，同时保留"总分不超过最多 5 分/条"的区间约束。
USE product_db;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
                     WHERE table_schema = DATABASE() AND table_name = 'product_rating_stats' AND constraint_name = 'ck_product_rating'),
              'ALTER TABLE product_rating_stats DROP CHECK ck_product_rating',
              'SELECT 1');
PREPARE product_ddl FROM @ddl;
EXECUTE product_ddl;
DEALLOCATE PREPARE product_ddl;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
                     WHERE table_schema = DATABASE() AND table_name = 'product_rating_stats' AND constraint_name = 'ck_product_rating'),
              'SELECT 1',
              'ALTER TABLE product_rating_stats ADD CONSTRAINT ck_product_rating CHECK (review_count >= 0 AND score_total >= 0 AND score_total <= review_count * 5 AND (review_count > 0 OR score_total = 0) AND revision > 0)');
PREPARE product_ddl FROM @ddl;
EXECUTE product_ddl;
DEALLOCATE PREPARE product_ddl;
