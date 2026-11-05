"""Run V9 and actual enrollment UPDATE only in a disposable schema; never migrate user_db."""
from pathlib import Path
import os,re,subprocess,uuid
root=Path(__file__).resolve().parents[1];env=os.environ.copy()
for line in (root/'.env').read_text(encoding='utf-8').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key,value=line.split('=',1);env.setdefault(key.strip(),value.strip().strip('"').strip("'"))
secret=env.get('MYSQL_ROOT_PASSWORD')
if not secret:raise SystemExit('No local MySQL credentials; no checks executed.')
schema='codex_email_check_'+uuid.uuid4().hex
assert re.fullmatch(r'codex_email_check_[a-f0-9]{32}',schema)
def sql(query,use=True):
    args=['wsl','-d','Ubuntu-24.04','--','docker','exec','-i','-e','MYSQL_PWD='+secret,'order-system-cloud-mysql-1','mysql','-uroot','--batch','--skip-column-names','--default-character-set=utf8mb4']
    result=subprocess.run(args,input=('USE '+schema+';\n' if use else '')+query,text=True,capture_output=True,timeout=20)
    if result.returncode:raise RuntimeError('Isolated SQL failed: '+result.stderr.replace(secret,'[REDACTED]'))
    return result.stdout.strip()
try:
    sql('CREATE DATABASE '+schema+';',False)
    sql("CREATE TABLE users(user_id BIGINT PRIMARY KEY,email VARCHAR(254) UNIQUE,username VARCHAR(64),balance DECIMAL(10,2),credential_version BIGINT,version INT,status INT,deleted_at DATETIME,updated_at DATETIME);INSERT INTO users VALUES(9,NULL,'test-user',80.50,0,0,1,NULL,NOW());")
    sql((root/'docs/database/migrations/user_db/V9__email_verified_at.sql').read_text(encoding='utf-8'))
    assert sql('SELECT COUNT(*) FROM users WHERE email_verified_at IS NOT NULL;')=='0','migration must not approve historical email'
    source=(root/'user-service/src/main/java/com/hbue/ordering/user/mapper/UserMapper.java').read_text(encoding='utf-8')
    updates={name:body for body,name in re.findall(r'@Update\("""(.*?)"""\)\s*int (\w+)',source,re.S)}
    update=updates['completeEmailEnrollment']
    update=update.replace('#{email}',"'new@example.com'").replace('#{id}','9')
    assert sql('START TRANSACTION;'+update+';SELECT ROW_COUNT();COMMIT;')=='1'
    assert sql("SELECT email_verified_at IS NOT NULL,credential_version,balance,username FROM users WHERE user_id=9;")=='1\t1\t80.50\ttest-user'
    assert sql('START TRANSACTION;'+update+';SELECT ROW_COUNT();COMMIT;')=='0','repeat enrollment must not bump sessions twice'
    print('Email ownership SQL passed: V9 leaves history unverified, enrollment binds email and increments credentials once, balance/name unchanged. Real users were not touched.')
finally:
    sql('DROP DATABASE IF EXISTS '+schema+';',False)
