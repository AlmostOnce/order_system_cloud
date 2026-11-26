USE notification_db;
SET NAMES utf8mb4;

-- 回填历史系统会话：升级前消息页的订单中心由通知表临时拼装，没有 system_conversations 实体行。
-- 每个用户取通知内部主键最大的一条作为会话摘要，保证升级后首次打开就能看到最新内容。
-- 只填充尚无摘要的会话，已有摘要（通知写入链路已推进）不被历史数据覆盖。
-- 文件名与内容中的中文均为 UTF-8，执行时必须使用 --default-character-set=utf8mb4 或本文件的 SET NAMES。
INSERT INTO system_conversations(customer_id, conversation_type, title, preview, latest_at)
SELECT n.recipient_id, 'ORDER', '订单中心', n.content, n.created_at
FROM notifications n
INNER JOIN (
    SELECT recipient_id, MAX(id) AS latest_id
    FROM notifications
    GROUP BY recipient_id
) latest ON latest.latest_id = n.id
ON DUPLICATE KEY UPDATE
    preview = IF(system_conversations.latest_at IS NULL, VALUES(preview), system_conversations.preview),
    latest_at = IF(system_conversations.latest_at IS NULL, VALUES(latest_at), system_conversations.latest_at);
