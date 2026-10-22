"""Validate the production aggregation SQL and Lua using isolated temporary data."""
from pathlib import Path
import json, re, subprocess, uuid
ROOT = Path(__file__).resolve().parents[1]
values = {}
for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip().strip('\"\'')
def mysql(sql):
    result = subprocess.run(['wsl','-d','Ubuntu-24.04','--','docker','exec','-i','-e',
        'MYSQL_PWD=' + values['MYSQL_ROOT_PASSWORD'], 'order-system-cloud-mysql-1',
        'mysql','-uroot','--batch','--skip-column-names','--default-character-set=utf8mb4','order_db'],
        input=sql, encoding='utf-8', capture_output=True)
    if result.returncode: raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()
source = (ROOT / 'order-service/src/main/java/com/hbue/ordering/order/mapper/SalesRankingMapper.java').read_text(encoding='utf-8')
sql = re.search(r'@Select\("""([\s\S]*?)"""\)', source).group(1).strip()
# Actual schema query is read-only; fixtures shadow these names only within one MySQL session.
actual = mysql(sql + ';')
fixtures = """
CREATE TEMPORARY TABLE orders (order_id BIGINT PRIMARY KEY, window_id BIGINT, status VARCHAR(20));
CREATE TEMPORARY TABLE order_items (item_id BIGINT PRIMARY KEY, order_id BIGINT, product_id BIGINT, quantity INT, product_name_snapshot VARCHAR(80));
INSERT INTO orders VALUES (1,1,'待支付'),(2,1,'已取消'),(3,1,'已支付'),(4,1,'制作中'),(5,1,'待取货'),(6,1,'已完成');
INSERT INTO order_items VALUES (1,1,7,80,'unpaid'),(2,2,7,90,'cancelled'),(3,3,7,2,'old'),(4,4,7,3,'old'),(5,5,7,4,'old'),(6,6,7,5,'latest-paid'),(7,2,7,100,'cancelled-latest'),(8,6,9,14,'tie'),(9,3,11,1,'third');
"""
rows = mysql(fixtures + sql + ';').splitlines()
assert len(rows) == 6, rows
quantities = {}
for row in rows:
    parts = row.split('\t')
    assert int(parts[0]) not in [1, 2], parts
    quantities[int(parts[2])] = quantities.get(int(parts[2]), 0) + int(parts[5])
assert quantities == {7:14, 9:14, 11:1}, quantities
# Exercise outbox uniqueness, retry and rollback using a temporary copy of the real schema.
outbox_sql = """
CREATE TEMPORARY TABLE test_sales_event_outbox LIKE sales_event_outbox;
START TRANSACTION;
INSERT INTO test_sales_event_outbox(event_id,order_id,payload)
VALUES ('test-event',1,'{"version":1,"eventId":"test-event","orderId":1}');
INSERT IGNORE INTO test_sales_event_outbox(event_id,order_id,payload)
VALUES ('duplicate-order',1,'{}');
SELECT COUNT(*) FROM test_sales_event_outbox;
UPDATE test_sales_event_outbox
SET next_attempt_at = TIMESTAMPADD(SECOND, LEAST(300, POW(2, LEAST(attempts,8))), CURRENT_TIMESTAMP),
    attempts=attempts+1, last_error='TestFailure'
WHERE event_id='test-event' AND published_at IS NULL;
SELECT attempts, next_attempt_at > CURRENT_TIMESTAMP, published_at IS NULL FROM test_sales_event_outbox;
ROLLBACK;
SELECT COUNT(*) FROM test_sales_event_outbox;
"""
assert mysql(outbox_sql).splitlines() == ['1', '1\t1\t1', '0']
prefix = 'test:ranking:{sales-' + uuid.uuid4().hex + '}:'
keys = [prefix + name for name in ['data', 'meta', 'seen', 'ready', 'generation']]

def redis(*args):
    # Send RESP through a socket; WSL shell brace expansion must not alter JSON members.
    import base64
    payload = base64.b64encode(json.dumps([values['REDIS_PASSWORD'], list(map(str,args))]).encode()).decode()
    program = r"""
import base64,json,socket
password,args=json.loads(base64.b64decode(PAYLOAD))
s=socket.create_connection(('127.0.0.1',9096),timeout=5)
f=s.makefile('rb')
def read():
    line=f.readline(); kind=line[:1]; value=line[1:-2]
    if kind==b'+': return value.decode()
    if kind==b'-': raise RuntimeError(value.decode())
    if kind==b':': return int(value)
    if kind==b'$':
        n=int(value)
        if n<0: return None
        data=f.read(n);f.read(2);return data.decode()
    if kind==b'*': return [read() for _ in range(int(value))]
    raise RuntimeError('Invalid Redis response')
def send(args):
    chunks=[b'*'+str(len(args)).encode()+b'\r\n']
    for arg in args:
        raw=arg.encode();chunks.extend([b'$'+str(len(raw)).encode()+b'\r\n',raw,b'\r\n'])
    s.sendall(b''.join(chunks));return read()
send(['AUTH',password])
print(json.dumps(send(args)))
s.close()
""".replace('PAYLOAD',repr(payload))
    result = subprocess.run(['wsl','-d','Ubuntu-24.04','--','python3','-'],input=program,encoding='utf-8',capture_output=True)
    if result.returncode: raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout)

def lua(name):
    return (ROOT / ('order-service/src/main/resources/rankings/' + name)).read_text(encoding='utf-8')
rebuild, apply, read = map(lua, ['rebuild-sales-v2.lua','apply-paid-sales-v2.lua','read-sales-v2.lua'])
def member(product): return f'{product:020d}:{1:020d}'
def meta(product, item_id, name='dish'):
    return f'{item_id:020d}:' + json.dumps({'rank':0,'productId':product,'windowId':1,'productName':name,'sales':0},separators=(',',':'))
def snapshot(generation='1', quantity=14, orders=(3,4,5,6)):
    return [generation,'snapshot',str(len(orders)),*map(str, orders),member(7),str(quantity),meta(7,6,'latest'),member(9),'14',meta(9,8),member(11),'1',meta(11,9)]
def consume(order_id, generation, product=7, quantity=2, item_id=20, name='new'):
    return redis('EVAL',apply,5,*keys,str(generation),str(order_id),'event',member(product),str(quantity),meta(product,item_id,name))
try:
    redis('SET', keys[4], '1')
    assert redis('EVAL',rebuild,5,*keys,*snapshot()) == 1
    result = redis('EVAL',read,5,*keys,2)
    assert len(result) == 5 and json.loads(result[1][21:])['productId'] == 9 and result[2] == '14', result
    # Historical orders included in snapshot: never increment them again.
    assert consume(3,1) == 0
    assert redis('ZSCORE',keys[0],member(7)) == '14'
    # New message increments once; replay cannot increase score.
    redis('SET',keys[4],'2')
    assert consume(10,2) == 1
    assert consume(10,2) == 0
    assert redis('ZSCORE',keys[0],member(7)) == '16'
    # An expired lock holder must not overwrite a newer event with an old snapshot.
    assert redis('EVAL',rebuild,5,*keys,*snapshot('1')) == 0
    assert redis('ZSCORE',keys[0],member(7)) == '16'
    # Old event metadata cannot replace the latest name.
    redis('SET',keys[4],'3')
    assert consume(11,3,quantity=1,item_id=5,name='old') == 1
    assert json.loads(redis('HGET',keys[1],member(7))[21:])['productName'] == 'new'
    assert redis('TTL',keys[2]) == -1, 'dedupe must persist as long as the projection'
    # Full rebuild contains orders seen before rebuilding; replay stays a no-op.
    redis('SET',keys[4],'4')
    assert redis('EVAL',rebuild,5,*keys,*snapshot('4',17,(3,4,5,6,10,11))) == 1
    assert consume(10,4) == 0
    assert redis('ZSCORE',keys[0],member(7)) == '17'
    # Validate every line before changing any score; overflow must not partly apply.
    redis('ZADD',keys[0],9007199254740991,member(99))
    args=['4','overflow','event',member(7),'2',meta(7,21),member(99),'1',meta(99,22)]
    assert redis('EVAL',apply,5,*keys,*args) == -3
    assert redis('ZSCORE',keys[0],member(7)) == '17'
    assert redis('SISMEMBER',keys[2],'overflow') == 0
    # Redis loss: no unseen message may apply before an authoritative rebuild.
    redis('DEL',*keys[:4])
    assert consume(10,4) == -1
    assert redis('EVAL',read,5,*keys,50) == []
    assert redis('EVAL',rebuild,5,*keys,*snapshot('4',17,(3,4,5,6,10,11))) == 1
    assert consume(10,4) == 0
    # Real empty projection is cached and readable, no invented sales.
    assert redis('EVAL',rebuild,5,*keys,'4','empty','0') == 1
    assert redis('EVAL',read,5,*keys,50) == ['empty']
finally:
    redis('DEL',*keys)
print('Outbox schema/rollback/retry and MQ projection SQL/Lua integration passed: paid-only facts, ZSet increment/order/limit, replay dedupe, historical-rebuild dedupe, fencing, out-of-order metadata, atomic overflow guard, Redis-loss recovery and real empty projection. Isolated keys cleaned.')
