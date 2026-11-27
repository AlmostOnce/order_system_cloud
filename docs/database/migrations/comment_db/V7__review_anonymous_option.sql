-- 匿名评价开关。
-- 设计要点：
--   1. 匿名字段与真实昵称快照分开保存：author_name 始终记录发布时的真实昵称，
--      anonymous 只决定对外展示；这样作者本人回溯、管理端核查和申诉取证都不丢信息。
--   2. 默认值为 0，历史评价与未升级客户端继续按实名展示，不改变既有数据语义。
--   3. 对外脱敏在读取时完成，不在写入时改写昵称，避免把“匿名”固化成不可逆的数据丢失。
-- 全部语句按 information_schema 判断后再执行，重复执行不会报错。
USE comment_db;

-- 菜品评价匿名标记。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'comments' AND column_name = 'anonymous'),
              'SELECT 1',
              'ALTER TABLE comments ADD COLUMN anonymous TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否匿名展示：1 匿名，0 实名''');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;

-- 店铺评价匿名标记。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'window_reviews' AND column_name = 'anonymous'),
              'SELECT 1',
              'ALTER TABLE window_reviews ADD COLUMN anonymous TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否匿名展示：1 匿名，0 实名''');
PREPARE comment_ddl FROM @ddl;
EXECUTE comment_ddl;
DEALLOCATE PREPARE comment_ddl;
