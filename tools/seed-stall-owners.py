"""Create development owners for the eight seeded windows; keep credentials outside Git."""
from pathlib import Path
from datetime import datetime, timezone, timedelta
import base64
import hashlib
import json
import secrets
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
values = {}
for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip().strip('\"\'')
prefix = ['wsl', '-d', 'Ubuntu-24.04', '--', 'docker', 'exec', '-i', '-e',
          'MYSQL_PWD=' + values['MYSQL_ROOT_PASSWORD'], 'order-system-cloud-mysql-1']


def query(sql):
    result = subprocess.run(prefix + ['mysql', '-uroot', '--batch', '--skip-column-names',
                            '--default-character-set=utf8mb4', 'user_db'],
                            input=sql.encode(), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode())
    return result.stdout.decode().strip()


def snapshot(table, key):
    columns = query("SELECT column_name FROM information_schema.columns "
                    "WHERE table_schema='user_db' AND table_name='" + table +
                    "' ORDER BY ordinal_position;").splitlines()
    raw = query('SELECT JSON_ARRAYAGG(JSON_OBJECT(' +
                ','.join("'" + c + "',`" + c + '`' for c in columns) + ')) FROM ' + table + ';')
    return {row[key]: row for row in (json.loads(raw) or [])}


windows = json.loads(query("""
SELECT JSON_ARRAYAGG(JSON_OBJECT('window_id',w.window_id,'window_name',w.window_name,
       'restaurant_name',r.restaurant_name))
FROM product_db.dining_windows w JOIN product_db.restaurants r ON r.restaurant_id=w.restaurant_id
WHERE w.window_code IN ('DEV-C1-W2','DEV-C1-W3','DEV-C2-W1','DEV-C2-W2',
                       'DEV-C2-W3','DEV-C3-W1','DEV-C3-W2','DEV-C3-W3');
"""))
assert len(windows) == 8, 'Expected all eight seed windows'
windows.sort(key=lambda row: row['window_id'])
original_users = snapshot('users', 'user_id')
original_scopes = snapshot('user_data_scopes', 'scope_id')
target_ids = {w['window_id'] for w in windows}
assert not any(s['scope_type'] == 'WINDOW' and s['scope_ref'] in target_ids
               for s in original_scopes.values()), 'Target already assigned; do not duplicate or replace owners'
used_numbers = {u['user_no'] for u in original_users.values()}
used_names = {u['username'] for u in original_users.values()}
used_emails = {u['email'] for u in original_users.values()}
accounts = []
for window in windows:
    number = str(1_000_000_000 + secrets.randbelow(9_000_000_000))
    while number in used_numbers:
        number = str(1_000_000_000 + secrets.randbelow(9_000_000_000))
    used_numbers.add(number)
    name = window['window_name'] + '店主'
    email = 'window-' + str(window['window_id']) + '@example.test'
    assert name not in used_names and email not in used_emails, 'Account already exists'
    accounts.append(dict(window, user_no=number, user_uuid=str(uuid.uuid4()), username=name,
                         email=email, password=secrets.token_urlsafe(12)))

# Use stdin rather than shell interpolation for the BCrypt program and generated passwords.
payload = base64.b64encode(json.dumps([a['password'] for a in accounts]).encode()).decode()
program = """
import crypt, json, base64
passwords = json.loads(base64.b64decode(PAYLOAD))
hashes = [crypt.crypt(p, crypt.mksalt(crypt.METHOD_BLOWFISH, rounds=4096)) for p in passwords]
assert all(h.startswith(('$2b$', '$2a$', '$2y$')) and len(h) == 60
           and crypt.crypt(p, h) == h and crypt.crypt('incorrect-' + p, h) != h
           for p, h in zip(passwords, hashes))
print(json.dumps(hashes))
""".replace('PAYLOAD', repr(payload))
result = subprocess.run(['wsl', '-d', 'Ubuntu-24.04', '--', 'python3', '-'],
                        input=program.encode(), capture_output=True)
if result.returncode:
    raise RuntimeError('BCrypt generation/verification failed before database mutation')
hashes = json.loads(result.stdout)

stamp = datetime.now(timezone(timedelta(hours=8))).strftime('%Y%m%d_%H%M%S')
output = Path.home() / '.codex/backups/order_system_cloud' / ('stall_accounts_' + stamp)
output.mkdir(parents=True, exist_ok=False)
dump = subprocess.run(prefix + ['mysqldump', '-uroot', '--single-transaction', '--skip-lock-tables',
                      '--no-tablespaces', '--set-gtid-purged=OFF', '--default-character-set=utf8mb4',
                      'user_db'], capture_output=True)
if dump.returncode:
    raise RuntimeError(dump.stderr.decode())
assert b'CREATE TABLE' in dump.stdout
(output / 'user_db_before_stall_accounts.sql').write_bytes(dump.stdout)
receipt = ('# 店主测试账号\n\n角色均为 STALL_MANAGER，各自只管理所列窗口。'
           '可用用户号或邮箱配合初始密码登录。邮箱为保留测试域名，不接收邮件。\n\n'
           '| 食堂 | 窗口 | 昵称 | 用户号 | 邮箱 | 初始密码 |\n|---|---|---|---|---|---|\n')
for account in accounts:
    receipt += '| ' + ' | '.join(account[k] for k in
                               ['restaurant_name', 'window_name', 'username', 'user_no', 'email', 'password']) + ' |\n'
receipt += '\n明文初始密码只保存于此本地文件，未写入仓库；数据库保存 BCrypt 哈希。\n'
pending = output / '店主测试账号_待完成.md'
pending.write_text(receipt, encoding='utf8')
assert snapshot('users', 'user_id') == original_users
assert snapshot('user_data_scopes', 'scope_id') == original_scopes


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


statements = ['START TRANSACTION;']
for account, hashed in zip(accounts, hashes):
    fields = [account['user_uuid'], account['user_no'], account['username'], account['email'],
              'STALL_MANAGER', 0, hashed, 1, 0]
    statements.append('INSERT INTO users (user_uuid,user_no,username,email,role_code,balance,'
                      'password_hash,status,version) VALUES (' + ','.join(map(quote, fields)) + ');')
    statements.append("INSERT INTO user_data_scopes (user_id,scope_type,scope_ref) "
                      "SELECT user_id,'WINDOW'," + str(account['window_id']) +
                      ' FROM users WHERE user_no=' + quote(account['user_no']) + ';')
statements.append('COMMIT;')
query('\n'.join(statements))
users = snapshot('users', 'user_id')
scopes = snapshot('user_data_scopes', 'scope_id')
assert len(users) == len(original_users) + 8 and len(scopes) == len(original_scopes) + 8
assert all(users[k] == row for k, row in original_users.items())
assert all(scopes[k] == row for k, row in original_scopes.items())
new_ids = []
for account, hashed in zip(accounts, hashes):
    matches = [u for u in users.values() if u['user_no'] == account['user_no']]
    assert len(matches) == 1
    user = matches[0]
    new_ids.append(user['user_id'])
    assert user['role_code'] == 'STALL_MANAGER' and user['status'] == 1
    assert user['password_hash'] == hashed and user['email'] == account['email']
    grants = [s for s in scopes.values() if s['user_id'] == user['user_id']]
    assert len(grants) == 1 and grants[0]['scope_type'] == 'WINDOW'
    assert grants[0]['scope_ref'] == account['window_id']
cache = subprocess.run(['wsl', '-d', 'Ubuntu-24.04', '--', 'docker', 'exec', '-e',
                        'REDISCLI_AUTH=' + values['REDIS_PASSWORD'], 'order-system-cloud-redis-1',
                        'redis-cli', 'DEL', *['authz:user-scope:' + str(i) for i in new_ids]],
                       capture_output=True)
if cache.returncode:
    raise RuntimeError('Accounts verified, but cache invalidation failed; credentials remain in ' + str(pending))
credentials = output / '店主测试账号.md'
pending.rename(credentials)
(output / 'manifest.json').write_text(json.dumps({
    'new_accounts': 8, 'new_window_scopes': 8, 'one_window_per_account': True,
    'existing_users_and_scopes_unchanged': True, 'bcrypt_verified': True,
    'backup_sha256': hashlib.sha256(dump.stdout).hexdigest()
}, indent=2), encoding='utf8')
print('Created 8 active STALL_MANAGER accounts, each with exactly one WINDOW scope.')
print('Existing users/scopes unchanged; BCrypt hashes and assignments verified.')
for account in accounts:
    print(account['restaurant_name'] + ' / ' + account['window_name'] + ' -> ' + account['user_no'])
print('Credentials:', credentials)
