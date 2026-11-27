"""在单个MySQL会话临时表上验证真实Mapper分页SQL，不迁移或写入项目orders表。"""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TABLE = "codex_order_page_check"
source = (ROOT / "order-service/src/main/java/com/hbue/ordering/order/mapper/OrderMapper.java").read_text(encoding="utf-8")
script = next(value for value in re.findall(r'@Select\("""(.*?)"""\)', source, re.S) if '<script>' in value and 'statuses' in value)
exists = re.search(r'@Select\("(SELECT EXISTS[^"\n]+)"\)\s*boolean existsForUserOrder', source).group(1)


def literal(value):
    """只绑定本文件固定测试值，不接收外部用户输入或拼接认证信息。"""
    return str(value) if isinstance(value, int) else "'" + value.replace("'", "''") + "'"


def render(node, values):
    """解释当前Mapper实际使用的if/foreach，测试所有筛选分支而非另写一套SQL。"""
    if node.tag == 'if' and values[node.attrib['test'].split()[0]] is None:
        return ''
    if node.tag == 'foreach':
        return node.attrib['open'] + node.attrib['separator'].join(
            re.sub(r'#\{(\w+)\}', lambda m: literal({**values, node.attrib['item']: item}[m[1]]), node.text or '')
            for item in values[node.attrib['collection']]) + node.attrib['close']
    text = node.text or ''
    for child in node:
        text += render(child, values) + (child.tail or '')
    return re.sub(r'#\{(\w+)\}', lambda m: literal(values[m[1]]), text)


def page(user=7, statuses=None, cursor=None, size=21):
    """使用Mapper原文生成有界查询；只把表名和返回列改为本会话临时表、编号。"""
    return render(ET.fromstring(script.strip()), dict(userId=user, statuses=statuses, beforeId=cursor, size=size)).replace('FROM orders', 'FROM ' + TABLE).replace('SELECT *', 'SELECT order_id').strip()


statements = ['SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;', 'CREATE TEMPORARY TABLE ' + TABLE + ' LIKE orders;']
migration = (ROOT / 'docs/database/migrations/order_db/V6__order_list_pagination_indexes.sql').read_text(encoding='utf-8')
# 先填充临时样本再建索引，模拟业务库给既有订单加索引的真实顺序。
statuses = ['待支付', '已支付', '制作中', '待取货', '已完成', '已取消']
fixtures = [(i, 8 if i % 5 == 0 else 7, statuses[i % 6]) for i in range(1, 1201)]
rows = [f"({i},'PAGE{i}',{user},1,10,{literal(status)},'2026-11-06 10:00:00','2026-11-06 10:00:00')" for i, user, status in fixtures]
statements.append('INSERT INTO ' + TABLE + '(order_id,order_no,user_id,window_id,total_amount,status,created_at,updated_at) VALUES ' + ','.join(rows) + ';')
statements.append(migration.replace('ALTER TABLE orders', 'ALTER TABLE ' + TABLE))
checks = {}


def check(name, query, expected=None):
    """用唯一标记拆分同一连接的多个查询结果，临时表只在这个连接内存在。"""
    statements.extend(["SELECT 'CASE:" + name + "';", query + ';'])
    if expected is not None:
        checks[name] = [str(value) for value in expected]


filters = [None, ['待支付'], ['已支付', '制作中', '待取货'], ['已完成'], ['已取消']]
for index, selected in enumerate(filters):
    for cursor in [None, 640]:
        matching = sorted((i for i, user, status in fixtures if user == 7 and (selected is None or status in selected) and (cursor is None or i < cursor)), reverse=True)[:21]
        check(f'filter{index}_{cursor}', page(statuses=selected, cursor=cursor), matching)
check('other_user', page(user=8), sorted((i for i, user, _ in fixtures if user == 8), reverse=True)[:21])
check('max_page', page(size=51), sorted((i for i, user, _ in fixtures if user == 7), reverse=True)[:51])
for name, user, identifier, expected in [('owned',7,639,1),('foreign',7,640,0),('missing',7,9000,0)]:
    query = re.sub(r'#\{(\w+)\}', lambda m: str(dict(userId=user, orderId=identifier)[m[1]]), exists).replace('FROM orders','FROM '+TABLE)
    check(name,query,[expected])
check('before_insert',page(cursor=640))
statements.append("INSERT INTO " + TABLE + "(order_id,order_no,user_id,window_id,total_amount,status,created_at,updated_at) VALUES (5000,'NEW-TOP',7,1,10,'已支付',NOW(),NOW());")
check('after_insert',page(cursor=640))
check('explain_all','EXPLAIN '+page().replace('SELECT order_id', 'SELECT *'))
check('explain_pending','EXPLAIN '+page(statuses=['待支付']).replace('SELECT order_id', 'SELECT *'))
# 优化器会按统计信息选择计划；另外验证新索引本身能承接排序，不把特定自然计划写成不稳定断言。
check('index_all', 'EXPLAIN ' + page().replace('SELECT order_id', 'SELECT *').replace('FROM ' + TABLE, 'FROM ' + TABLE + ' FORCE INDEX (idx_orders_user_order)'))
check('index_pending', 'EXPLAIN ' + page(statuses=['待支付']).replace('SELECT order_id', 'SELECT *').replace('FROM ' + TABLE, 'FROM ' + TABLE + ' FORCE INDEX (idx_orders_user_status_order)'))
# 认证从容器已有环境读取，不把密码复制到命令参数、源码或输出；stdin携带固定测试SQL。
command = ['wsl.exe', '--exec', 'docker', 'exec', '-i', 'order-system-cloud-mysql-1', 'sh', '-c',
           'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 order_db']
run = subprocess.run(command, input='\n'.join(statements).encode('utf-8'), capture_output=True, timeout=30)
if run.returncode:
    raise RuntimeError('临时表SQL验证失败：' + run.stderr.decode('utf-8', errors='replace'))
results = {}; current = None
for line in run.stdout.decode('utf-8').splitlines():
    if line.startswith('CASE:'):
        current = line[5:]; results[current] = []
    elif current is not None:
        results[current].append(line)
for name, expected in checks.items():
    assert results[name] == expected, (name, results[name], expected)
assert results['before_insert'] == results['after_insert'], '顶部新增订单不能改变旧游标后的结果'
for name in ['explain_all','explain_pending']:
    print(name + ': ' + results[name][0])
for name in ['index_all','index_pending']:
    plan = results[name][0].split('\t')
    assert plan[6] in ['idx_orders_user_order','idx_orders_user_status_order'], (name,plan)
    assert 'filesort' not in plan[-1].lower(), (name,plan)
    print(name + ': ' + plan[6] + ', ' + plan[-1])
print('SQL检查通过：1200条临时样本、五状态/多用户隔离、游标边界、新订单插入、21/51条上限和指定索引排序能力。自然选择计划已单独输出，未迁移或修改业务表。')
