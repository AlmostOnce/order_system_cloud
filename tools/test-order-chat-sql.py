"""在连接内临时表执行真实聊天Mapper SQL，验证顺序、幂等、已读及事务原子性，不生成业务会话。"""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'notification-service/src/main/java/com/hbue/ordering/notification/mapper'
sql = ['SET NAMES utf8mb4 COLLATE utf8mb4_bin;']
checks = {}
names = ['chat_conversations', 'chat_messages', 'chat_read_state', 'chat_event_outbox']
for name in names:
    sql.append(f'CREATE TEMPORARY TABLE check_{name} LIKE {name};')


def literal(value):
    """固定测试参数按类型转义，不读取用户输入或凭据。"""
    return str(value) if isinstance(value, int) else "'" + value.replace("'", "''") + "'"


def render(node, values):
    """解释真实Mapper中的条件及窗口范围列表，避免测试另写一份业务SQL。"""
    if node.tag in ('if', 'when'):
        condition = node.attrib['test']
        name = condition.split()[0]
        if ('!= null' in condition and values[name] is None) or ('!= null' not in condition and not values[name]):
            return ''
    if node.tag == 'choose':
        chosen = None
        for child in node:
            if child.tag == 'when':
                condition = child.attrib['test']
                value = values[condition.split()[0]]
                if (value is not None if '!= null' in condition else bool(value)):
                    chosen = child
                    break
        if chosen is None:
            chosen = next(child for child in node if child.tag == 'otherwise')
        return render(chosen, values)
    if node.tag == 'foreach':
        return node.attrib['open'] + node.attrib['separator'].join(literal(value) for value in values[node.attrib['collection']]) + node.attrib['close']
    output = node.text or ''
    for child in node:
        output += render(child, values) + (child.tail or '')
    return re.sub(r'#\{(\w+)\}', lambda match: literal(values[match[1]]), output)


def query(mapper, method, **values):
    """提取指定注解语句，只替换物理表名为本连接临时表。"""
    text = (BASE / (mapper + '.java')).read_text(encoding='utf-8')
    end = re.search(r'\b' + method + r'\(', text).start()
    start = max(text.rfind('@Select', 0, end), text.rfind('@Insert', 0, end), text.rfind('@Update', 0, end))
    annotation = text[start:end]
    block = re.search(r'"""(.*?)"""', annotation, re.S)
    statement = block[1] if block else ''.join(re.findall(r'"([^"\n]*)"', annotation))
    statement = statement.strip()
    if statement.startswith('<script>'):
        statement = render(ET.fromstring(statement), values)
    else:
        statement = re.sub(r'#\{(\w+)\}', lambda match: literal(values[match[1]]), statement)
    for name in names:
        statement = re.sub(r'\b' + name + r'\b', 'check_' + name, statement)
    return statement.strip()


def check(name, statement, expected):
    sql.extend(["SELECT 'CASE:" + name + "';", statement.rstrip(';') + ';'])
    checks[name] = expected


def uuid(number):
    return f'00000000-0000-4000-8000-{number:012d}'


for key, customer, window, minute in [(1, 7, 3, 4), (2, 7, 4, 1), (3, 8, 3, 2)]:
    sql.append(f"INSERT INTO check_chat_conversations(id,conversation_uuid,order_id,order_no,window_id,window_name,customer_id,last_seq,last_message_at,created_at) VALUES({key},'{uuid(key)}',{key},'O{key}',{window},'店铺',{customer},{4 if key == 1 else 1 if key == 2 else 0},'2026-11-09 09:0{minute}:00','2026-11-09 09:00:00');")
# 游标子查询不能重复打开MySQL同一临时表，因此为它准备完全相同的只读副本。
sql.extend(['CREATE TEMPORARY TABLE check_chat_cursor LIKE chat_conversations;', 'INSERT INTO check_chat_cursor SELECT * FROM check_chat_conversations;'])


def conversations(customer, uid, windows, cursor=None):
    statement = query('ChatConversationMapper', 'page', userId=uid, customer=customer, windows=windows, cursor=cursor, size=21)
    statement = re.sub(r'^SELECT \*', 'SELECT id', statement)
    return statement.replace('FROM check_chat_conversations WHERE conversation_uuid=', 'FROM check_chat_cursor WHERE conversation_uuid=')


check('customer_scope', conversations(True, 7, []), ['1', '2'])
check('other_customer_scope', conversations(True, 9, []), [])
check('merchant_direct_window_scope', conversations(False, 9, [3]), ['1', '3'])
check('conversation_cursor', conversations(True, 7, [], uuid(1)), ['2'])
check('merchant_cursor', conversations(False, 9, [3], uuid(1)), ['3'])
# 执行真实创建SQL，以订单唯一键检验并发兜底不覆盖已有会话身份。
sql.append(query('ChatConversationMapper', 'create', conversationUuid=uuid(99), orderId=1, orderNo='其他', windowId=99, windowName='其他', customerId=99) + ';')
check('order_unique_keeps_identity', 'SELECT conversation_uuid,customer_id,window_id FROM check_chat_conversations WHERE order_id=1', [uuid(1) + '\t7\t3'])


def insert_message(seq, sender, identifier=None):
    return f"INSERT INTO check_chat_messages(message_uuid,conversation_id,seq,sender_id,sender_uuid,sender_name,sender_side,client_message_id,content) VALUES('{uuid(100+seq)}',1,{seq},{sender},'{uuid(sender)}','发送人','{'CUSTOMER' if sender == 7 else 'MERCHANT'}','{uuid(identifier or 200+seq)}','消息{seq}')"


for seq, sender in [(1, 7), (2, 9), (3, 7), (4, 9)]:
    sql.append(insert_message(seq, sender) + ';')


def messages(before=None, after=None, size=3):
    return query('ChatMessageMapper', 'page', cid=1, before=before, after=after, size=size).replace('SELECT *', 'SELECT seq', 1)


check('latest_message_page', messages(), ['4', '3', '2'])
check('older_message_page', messages(before=3), ['2', '1'])
check('incremental_message_page', messages(after=1), ['2', '3', '4'])
check('message_page_bound', messages(after=0, size=2), ['1', '2'])
check('customer_unread_excludes_self', query('ChatMessageMapper', 'unread', cid=1, uid=7), ['2'])
sql.append(query('ChatMessageMapper', 'read', cid=1, uid=7, seq=2) + ';')
check('read_watermark', query('ChatMessageMapper', 'unread', cid=1, uid=7), ['1'])
sql.append(query('ChatMessageMapper', 'read', cid=1, uid=7, seq=1) + ';')
check('late_read_cannot_regress', 'SELECT last_read_seq FROM check_chat_read_state WHERE conversation_id=1 AND user_id=7', ['2'])
check('merchant_read_is_independent', query('ChatMessageMapper', 'unread', cid=1, uid=9), ['2'])
# 回执按对方一侧查询，不把本人阅读水位误认为对方已读；其他会话水位不能串入。
check('customer_peer_initial', query('ChatMessageMapper', 'peerReadSeq', cid=1, customerId=7, customer=True), ['0'])
check('merchant_sees_customer_read', query('ChatMessageMapper', 'peerReadSeq', cid=1, customerId=7, customer=False), ['2'])
sql.append(query('ChatMessageMapper', 'read', cid=1, uid=9, seq=3) + ';')
check('customer_sees_merchant_read', query('ChatMessageMapper', 'peerReadSeq', cid=1, customerId=7, customer=True), ['3'])
sql.append(query('ChatMessageMapper', 'read', cid=1, uid=10, seq=4) + ';')
check('any_merchant_read', query('ChatMessageMapper', 'peerReadSeq', cid=1, customerId=7, customer=True), ['4'])
check('peer_conversation_isolation', query('ChatMessageMapper', 'peerReadSeq', cid=2, customerId=7, customer=True), ['0'])
check('unread_total', query('ChatConversationMapper', 'unreadTotal', userId=7, customer=True, windows=[]), ['1'])
# 与正式服务相同的消息唯一键：相同幂等键的新seq无法再次插入。
sql.append(insert_message(5, 7, 201).replace('INSERT INTO', 'INSERT IGNORE INTO') + ';')
check('idempotency_key_unique', 'SELECT COUNT(*) FROM check_chat_messages', ['4'])
sql.append('START TRANSACTION;')
sql.append(insert_message(5, 7) + ';')
sql.append(query('ChatConversationMapper', 'advance', id=1, seq=5, content='消息5') + ';')
sql.append(f"INSERT INTO check_chat_event_outbox(event_uuid,conversation_uuid) VALUES('{uuid(300)}','{uuid(1)}');")
sql.append('ROLLBACK;')
check('rollback_removes_message', 'SELECT COUNT(*) FROM check_chat_messages', ['4'])
check('rollback_restores_sequence', 'SELECT last_seq FROM check_chat_conversations WHERE id=1', ['4'])
check('rollback_removes_outbox', 'SELECT COUNT(*) FROM check_chat_event_outbox', ['0'])
sql.append('START TRANSACTION;')
sql.append(insert_message(5, 7) + ';')
sql.append(query('ChatConversationMapper', 'advance', id=1, seq=5, content='消息5') + ';')
sql.append(f"INSERT INTO check_chat_event_outbox(event_uuid,conversation_uuid) VALUES('{uuid(300)}','{uuid(1)}');")
# AUTO_INCREMENT不会因回滚回收，保存实际分配的Outbox编号，不能假定仍为1。
sql.append('SET @chat_event_id=LAST_INSERT_ID();')
sql.append('COMMIT;')
check('commit_sequence', 'SELECT last_seq FROM check_chat_conversations WHERE id=1', ['5'])
check('commit_outbox', 'SELECT COUNT(*) FROM check_chat_event_outbox', ['1'])
sql.append(query('ChatOutboxMapper', 'retry', id=0).replace('WHERE id=0','WHERE id=@chat_event_id') + ';')
check('retry_retains_event', 'SELECT attempts,delivered_at IS NULL,next_attempt_at>CURRENT_TIMESTAMP(3) FROM check_chat_event_outbox', ['1\t1\t1'])
sql.append(query('ChatOutboxMapper', 'delivered', id=0).replace('WHERE id=0','WHERE id=@chat_event_id') + ';')
check('delivered_is_not_message_read', query('ChatMessageMapper', 'unread', cid=1, uid=7), ['1'])
check('delivered_event_not_pending', query('ChatOutboxMapper', 'pending').replace('SELECT id,event_uuid,conversation_uuid,attempts', 'SELECT id'), [])

# 固定时间边界：十分钟内本人可撤回，超时/他人不能修改；不写真实消息。
sql.append("UPDATE check_chat_messages SET created_at='2026-11-09 10:00:00' WHERE seq IN (1,2,3);")
sql.append(query('ChatMessageMapper','revoke',id=1,uid=9,now='2026-11-09 10:05:00')+';')
check('revoke_other_sender_denied', 'SELECT revoked_at IS NULL FROM check_chat_messages WHERE id=1', ['1'])
sql.append(query('ChatMessageMapper','revoke',id=1,uid=7,now='2026-11-09 10:10:00.001')+';')
check('revoke_over_ten_minutes_denied', 'SELECT revoked_at IS NULL FROM check_chat_messages WHERE id=1', ['1'])
sql.append(query('ChatMessageMapper','revoke',id=1,uid=7,now='2026-11-09 10:10:00')+';')
check('revoke_at_boundary_allowed', 'SELECT revoked_at IS NOT NULL FROM check_chat_messages WHERE id=1', ['1'])
sql.append(query('ChatMessageMapper','revoke',id=1,uid=7,now='2026-11-09 10:09:00')+';')
check('revoke_cannot_change_original_time', "SELECT revoked_at='2026-11-09 10:10:00' FROM check_chat_messages WHERE id=1", ['1'])
sql.append(query('ChatConversationMapper','revokePreview',id=1,seq=1)+';')
check('old_revoke_preserves_newest_preview', "SELECT last_content='消息5' FROM check_chat_conversations WHERE id=1", ['1'])
check('revoked_batch_only_requested', query('ChatMessageMapper','revoked',cid=1,ids=[uuid(101),uuid(102)]).replace('SELECT *','SELECT message_uuid'), [uuid(101)])
check('revoked_batch_scope', query('ChatMessageMapper','revoked',cid=2,ids=[uuid(101)]).replace('SELECT *','SELECT message_uuid'), [])
command = ['wsl.exe', '--exec', 'docker', 'exec', '-i', 'order-system-cloud-mysql-1', 'sh', '-c',
           'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 notification_db']
run = subprocess.run(command, input='\n'.join(sql).encode(), capture_output=True, timeout=30)
if run.returncode:
    raise RuntimeError(run.stderr.decode(errors='replace'))
actual = {}
current = None
for line in run.stdout.decode().splitlines():
    if line.startswith('CASE:'):
        current = line[5:]
        actual[current] = []
    elif current is not None:
        actual[current].append(line)
for name, expected in checks.items():
    assert actual[name] == expected, (name, actual[name], expected)
print(f'聊天SQL检查通过：{len(checks)}项，覆盖范围、分页、订单唯一性、消息幂等、独立已读、事务回滚及Outbox重试；未创建业务会话。')
