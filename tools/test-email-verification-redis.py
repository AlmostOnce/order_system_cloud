"""Check email-code Lua scripts on isolated temporary Redis keys."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import os
import re
import socket
import uuid

root = Path(__file__).resolve().parents[1]
source = (root / 'user-service/src/main/java/com/hbue/ordering/user/service/impl/EmailVerificationService.java').read_text(encoding='utf-8')
def script(name):
    match = re.search(r'%s = new DefaultRedisScript<>\("""(.*?)""", Long.class\)' % name, source, re.S)
    if not match:
        raise RuntimeError('missing script ' + name)
    return match.group(1)
env = os.environ.copy()
for line in (root / '.env').read_text(encoding='utf-8').splitlines():
    if line.startswith('REDIS_PASSWORD='):
        env.setdefault('REDIS_PASSWORD', line.split('=', 1)[1].strip().strip('"').strip("'"))

def command(*args):
    with socket.create_connection(('127.0.0.1', 9096), timeout=5) as conn:
        f = conn.makefile('rb')
        def execute(*parts):
            values = [str(x).encode('utf-8') for x in parts]
            conn.sendall(b'*%d\r\n' % len(values) + b''.join(b'$%d\r\n' % len(v) + v + b'\r\n' for v in values))
            head = f.readline()
            if head.startswith(b'-'):
                raise RuntimeError(head.decode('utf-8'))
            if head.startswith(b':'):
                return int(head[1:])
            if head.startswith(b'$'):
                n = int(head[1:])
                return None if n < 0 else f.read(n + 2)[:-2].decode('utf-8')
            return head[1:-2].decode('utf-8')
        assert execute('AUTH', env.get('REDIS_PASSWORD', 'order_redis_dev_2026')) == 'OK'
        return execute(*args)

key = 'email:test:' + uuid.uuid4().hex
consume = script('CONSUME')
remove = script('REMOVE_IF_SAME')
correct = 'a' * 64
wrong = 'b' * 64
try:
    assert command('SET', key, correct + ':0', 'EX', 60) == 'OK'
    assert command('EVAL', consume, 1, key, wrong, '5') == 0
    assert command('GET', key) == correct + ':1'
    assert 0 < command('TTL', key) <= 60
    assert command('EVAL', remove, 1, key, wrong) == 0
    assert command('EVAL', consume, 1, key, correct, '5') == 1
    assert command('EVAL', consume, 1, key, correct, '5') == 0
    command('SET', key, correct + ':0', 'EX', 60)
    for _ in range(5):
        assert command('EVAL', consume, 1, key, wrong, '5') == 0
    assert command('GET', key) is None
    command('SET', key, correct + ':0', 'EX', 60)
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda _: command('EVAL', consume, 1, key, correct, '5'), range(16)))
    assert sum(results) == 1
    command('SET', key, correct + ':0', 'EX', 60)
    assert command('EVAL', remove, 1, key, correct) == 1
    print('Redis 邮箱验证码：失败计数、TTL、并发一次消费和条件删除验证通过')
finally:
    command('DEL', key)
