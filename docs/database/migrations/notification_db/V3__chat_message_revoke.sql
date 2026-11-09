-- 目标notification_db；增加可空撤回标记，不删除历史正文，可重复执行。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='chat_messages' AND column_name='revoked_at')=0,
 'ALTER TABLE chat_messages ADD COLUMN revoked_at DATETIME(3) NULL COMMENT ''撤回时间，非空时对外屏蔽原文''', 'SELECT 1');
PREPARE chat_revoke_migration FROM @ddl;
EXECUTE chat_revoke_migration;
DEALLOCATE PREPARE chat_revoke_migration;
