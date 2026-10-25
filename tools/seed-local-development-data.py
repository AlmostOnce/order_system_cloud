"""补齐本机开发目录、库存和管理账号；默认只预览，--apply 才写入。

运行前安装 Python bcrypt 包。新密码只写入 Git 之外的私有文件。
重复执行会复用已有账号、校验角色与范围，不重置密码或库存。
"""
import argparse
import collections
from datetime import datetime, timedelta, timezone
import gzip
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import urllib.request
import uuid

import bcrypt


ROOT = Path(__file__).resolve().parents[1]
DOCKER = (['wsl', '-d', 'Ubuntu-24.04', '--', 'docker']
          if sys.platform == 'win32' else ['docker'])
MYSQL = DOCKER + ['exec', '-i', 'order-system-cloud-mysql-1', 'sh', '-c',
                 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '
                 '--default-character-set=utf8mb4 --batch --raw --skip-column-names user_db']
SCHEMAS = ['user_db', 'order_db', 'product_db', 'stock_db', 'payment_db', 'file_db']


def query(sql):
    """通过容器内现有凭据执行 SQL，错误立即中止，不打印业务 SQL 或密码。"""
    result = subprocess.run(MYSQL, input=sql, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


def quote(value):
    """对生成的文本进行 MySQL 字面量转义。"""
    return "'" + str(value).replace('\\', '\\\\').replace("'", "''") + "'"


def rows(table):
    """完整快照用于比较原有用户、菜品和库存，不向终端输出敏感字段。"""
    assert re.fullmatch(r'[a-z_]+\.[a-z_]+', table)
    schema, name = table.split('.')
    columns = query('SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE '
                    'TABLE_SCHEMA=' + quote(schema) + ' AND TABLE_NAME=' + quote(name)
                    + ' ORDER BY ORDINAL_POSITION').splitlines()
    assert columns, '缺少业务表：' + table
    fields = ','.join(quote(column) + ',`' + column + '`' for column in columns)
    return json.loads(query('SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(' + fields
                            + ')),JSON_ARRAY()) FROM ' + table))


def keyed(table, key):
    return {row[key]: row for row in rows(table)}


def redis(*arguments):
    """只操作指定业务缓存；密码通过子进程环境传递，不写入命令参数。"""
    values = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip().strip('\"\'')
    result = subprocess.run(DOCKER + ['exec', '-e', 'REDISCLI_AUTH',
                            'order-system-cloud-redis-1', 'redis-cli', '--raw', *arguments],
                            env=dict(os.environ, REDISCLI_AUTH=values['REDIS_PASSWORD']),
                            capture_output=True, text=True, check=True)
    if result.stdout.startswith(('NOAUTH', 'WRONGPASS', 'ERR ')):
        raise RuntimeError('Redis 操作失败：' + result.stdout)
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--verify-auth', action='store_true', help='用新账号验证运行中的用户服务')
    args = parser.parse_args()
    os.umask(0o077)
    before_users = keyed('user_db.users', 'user_id')
    before_products = keyed('product_db.products', 'product_id')
    before_stock = keyed('stock_db.stock', 'stock_id')
    before_scopes = keyed('user_db.user_data_scopes', 'scope_id')
    untouched_tables = ['order_db.orders', 'order_db.order_items', 'order_db.sales_event_outbox',
                        'payment_db.payments', 'user_db.balance_records', 'stock_db.stock_flow']
    untouched = {table: rows(table) for table in untouched_tables}
    print('当前用户/商品/库存：', len(before_users), len(before_products), len(before_stock))
    if not args.apply:
        print('预览：补齐三食堂九窗口、77 道菜和库存；准备 13 个管理账号，保留原有账号。')
        return

    # 固定库存脚本来自早期五道演示菜，只在业务编码与原主键一致时使用。
    expected_codes = {1: 'DEMO-RICE-01', 2: 'DEMO-DRINK-01', 3: 'DEMO-UNCAT-01',
                      4: 'DEMO-HIDDEN-01', 5: 'DEMO-OFFSALE-01'}
    assert all(before_products.get(key, {}).get('product_code') == code
               and before_products[key]['window_id'] == 1 for key, code in expected_codes.items()), \
        '早期演示商品主键不匹配，必须按业务编码重新映射库存输入'
    stamp = datetime.now(timezone(timedelta(hours=8))).strftime('%Y%m%d_%H%M%S')
    output = Path.home() / '.codex/backups/order_system_cloud' / ('local_demo_' + stamp)
    output.mkdir(parents=True, mode=0o700, exist_ok=False)
    dump = subprocess.run(DOCKER + ['exec', 'order-system-cloud-mysql-1', 'sh', '-c',
                          'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot '
                          '--single-transaction --routines --events --triggers --hex-blob '
                          '--no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 '
                          '--databases ' + ' '.join(SCHEMAS)], capture_output=True, check=True)
    assert b'CREATE TABLE' in dump.stdout
    backup = output / 'business-databases-before.sql.gz'
    backup.write_bytes(gzip.compress(dump.stdout))
    assert gzip.decompress(backup.read_bytes()) == dump.stdout

    # 复用已审阅的目录脚本；仅给缺少库存的菜品创建初始基线。
    for name in ('campus_three_canteens.sql', 'stock_initialization_20261022.sql'):
        query((ROOT / 'docs/database/seeds' / name).read_text(encoding='utf8'))
    windows = rows('product_db.dining_windows')
    window_by_code = {row['window_code']: row for row in windows}
    first = window_by_code['W-DEMO-01']
    specs = [
        ('admin', 'SYSTEM_ADMIN', 'admin@example.test', []),
        ('general_manager', 'GENERAL_MANAGER', 'general_manager@example.test', [('PLATFORM', 0)]),
        ('rm01', 'RESTAURANT_MANAGER', 'rm01@example.test', [('RESTAURANT', first['restaurant_id'])]),
        ('stall01', 'STALL_MANAGER', 'stall01@example.test', [('WINDOW', first['window_id'])]),
        ('stalltest01', 'STALL_MANAGER', 'stalltest01@example.test', [('WINDOW', first['window_id'])]),
    ]
    for code in ('DEV-C1-W2', 'DEV-C1-W3', 'DEV-C2-W1', 'DEV-C2-W2', 'DEV-C2-W3',
                 'DEV-C3-W1', 'DEV-C3-W2', 'DEV-C3-W3'):
        window = window_by_code[code]
        specs.append((window['window_name'] + '店主', 'STALL_MANAGER',
                      'window-' + str(window['window_id']) + '@example.test',
                      [('WINDOW', window['window_id'])]))
    users_by_name = {row['username']: row for row in before_users.values()}
    numbers = {row['user_no'] for row in before_users.values()}
    emails = {row['email'] for row in before_users.values()}
    new_accounts = []
    for name, role, email, scopes in specs:
        if name in users_by_name:
            old = users_by_name[name]
            assert old['role_code'] == role and old['status'] == 1 and old['deleted_at'] is None, \
                '已有同名账号不符合目标角色或状态：' + name
            assert {(row['scope_type'], row['scope_ref']) for row in before_scopes.values()
                    if row['user_id'] == old['user_id']} == set(scopes), '已有账号范围不匹配：' + name
            continue
        assert email not in emails, '测试邮箱已占用：' + email
        number = str(1000000000 + secrets.randbelow(9000000000))
        while number in numbers:
            number = str(1000000000 + secrets.randbelow(9000000000))
        numbers.add(number)
        emails.add(email)
        password = secrets.token_urlsafe(15)
        hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=12)).decode()
        new_accounts.append(dict(username=name, role_code=role, email=email, user_no=number,
                                 user_uuid=str(uuid.uuid4()), password=password,
                                 password_hash=hashed, scopes=scopes))

    # 先保存私有凭据，再提交账号事务；失败时仍能找到已生成的登录信息。
    receipt = ['# 本机管理端测试账号', '', '登录使用用户号或邮箱，昵称不是登录账号。',
               'SYSTEM_ADMIN 管账号；GENERAL_MANAGER 管全平台业务；其他账号按范围授权。', '',
               '| 昵称 | 角色 | 用户号 | 邮箱 | 初始密码 | 范围 |', '|---|---|---|---|---|---|']
    for account in new_accounts:
        receipt.append('| ' + ' | '.join(str(account[key]) for key in
                       ('username', 'role_code', 'user_no', 'email', 'password', 'scopes')) + ' |')
    receipt.append('\n已存在的账号保持原密码，不会在此重新输出。邮箱仅用于开发登录，不接收邮件。\n')
    credentials = output / '管理端测试账号.md'
    credentials.write_text('\n'.join(receipt), encoding='utf8')
    statements = ['START TRANSACTION;']
    for account in new_accounts:
        fields = ('user_uuid', 'user_no', 'username', 'email', 'role_code', 'password_hash')
        statements.append('INSERT INTO users (' + ','.join(fields) + ',balance,status,version) VALUES ('
                          + ','.join(quote(account[key]) for key in fields) + ',0,1,0);')
        statements.append('SET @seed_user_id = LAST_INSERT_ID();')
        for scope_type, scope_ref in account['scopes']:
            statements.append('INSERT INTO user_data_scopes (user_id,scope_type,scope_ref) VALUES '
                              '(@seed_user_id,' + quote(scope_type) + ',' + str(scope_ref) + ');')
    query('\n'.join(statements + ['COMMIT;']))
    after_users = keyed('user_db.users', 'user_id')
    after_products = keyed('product_db.products', 'product_id')
    after_stock = keyed('stock_db.stock', 'stock_id')
    after_scopes = keyed('user_db.user_data_scopes', 'scope_id')
    for old, current in ((before_users, after_users), (before_products, after_products),
                         (before_stock, after_stock), (before_scopes, after_scopes)):
        assert all(current[key] == value for key, value in old.items()), '原有业务数据发生变化'
    assert all(rows(table) == original for table, original in untouched.items()), '订单或账务记录发生变化'
    for name, role, _, scopes in specs:
        user = next(row for row in after_users.values() if row['username'] == name)
        assert user['role_code'] == role and user['status'] == 1
        assert {(row['scope_type'], row['scope_ref']) for row in after_scopes.values()
                if row['user_id'] == user['user_id']} == set(scopes)
    for account in new_accounts:
        user = next(row for row in after_users.values() if row['user_no'] == account['user_no'])
        assert bcrypt.checkpw(account['password'].encode(), user['password_hash'].encode())
    assert len(rows('product_db.restaurants')) >= 3 and len(windows) >= 9
    assert len(after_products) >= 77 and len(after_stock) >= 77

    # 只失效商品读缓存、布隆位图及配置，不操作登录令牌、购物车或分布式锁。
    cache_keys = set()
    for pattern in ('product:windows:active*', 'product:menu:window:*', 'product:null:menu:window:*'):
        cache_keys.update(filter(None, redis('--scan', '--pattern', pattern).splitlines()))
    for name in ('product:bloom:window', 'product:bloom:product'):
        cache_keys.update((name, '{' + name + '}:config'))
    for row in after_stock.values():
        cache_keys.add('stock:available:' + str(row['window_id']) + ':' + str(row['product_id']))
    new_ids = [row['user_id'] for row in after_users.values() if row['user_id'] not in before_users]
    cache_keys.update('authz:user-scope:' + str(user_id) for user_id in new_ids)
    removed = int(redis('DEL', *sorted(cache_keys))) if cache_keys else 0

    verified = 0
    if args.verify_auth:
        values = {}
        for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                values[key.strip()] = value.strip().strip('\"\'')
        config = (ROOT / 'user-service/src/main/resources/application.yml').read_text()
        default_token = re.search(r'\$\{INTERNAL_SCOPE_TOKEN:([^}]*)\}', config).group(1)
        for account in new_accounts:
            for login in (account['user_no'], account['email']):
                payload = json.dumps({'account': login, 'password': account['password']}).encode()
                request = urllib.request.Request('http://127.0.0.1:9092/users/authenticate', data=payload,
                          headers={'Content-Type': 'application/json',
                                   'X-Internal-Token': values.get('INTERNAL_SCOPE_TOKEN', default_token)})
                with urllib.request.urlopen(request, timeout=10) as response:
                    result = json.load(response)
                assert result['code'] == 'COMMON_000' and result['data']['roleCode'] == account['role_code']
                verified += 1
    manifest = dict(new_accounts=len(new_accounts), users=len(after_users),
                    roles=dict(collections.Counter(row['role_code'] for row in after_users.values())),
                    restaurants=len(rows('product_db.restaurants')), windows=len(windows),
                    products=len(after_products), stock=len(after_stock), scopes=len(after_scopes),
                    auth_verifications=verified, existing_data_preserved=True, caches_removed=removed)
    (output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(manifest, ensure_ascii=False))
    print('Credentials:', credentials)
    print('Backup:', backup)


if __name__ == '__main__':
    main()
