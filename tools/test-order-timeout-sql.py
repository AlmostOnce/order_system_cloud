"""Validate settlement fencing SQL on a disposable schema, never on project account data."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import os,re,subprocess,uuid
root=Path(__file__).resolve().parents[1]
env=os.environ.copy()
for line in (root/'.env').read_text(encoding='utf-8').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key,value=line.split('=',1);env.setdefault(key.strip(),value.strip().strip('"').strip("'"))
secret=env.get('MYSQL_ROOT_PASSWORD')
if not secret: raise SystemExit('No local MySQL test credentials; no SQL executed.')
schema='codex_timeout_check_'+uuid.uuid4().hex
assert re.fullmatch(r'codex_timeout_check_[a-f0-9]{32}',schema)
def sql(query,use=True):
    args=['wsl','-d','Ubuntu-24.04','--','docker','exec','-i','-e','MYSQL_PWD='+secret,'order-system-cloud-mysql-1','mysql','-uroot','--batch','--skip-column-names','--default-character-set=utf8mb4']
    result=subprocess.run(args,input=('USE '+schema+';\n' if use else '')+query,text=True,capture_output=True,timeout=20)
    if result.returncode: raise RuntimeError('Disposable SQL check failed: '+result.stderr.replace(secret,'[REDACTED]'))
    return result.stdout.strip()
source=(root/'user-service/src/main/java/com/hbue/ordering/user/mapper/BalanceSettlementGuardMapper.java').read_text(encoding='utf-8')
ensure=re.search(r'@Insert\("""(.*?)"""\)',source,re.S).group(1)
lock=re.search(r'@Select\("(.*?)"\)',source).group(1)
close=re.search(r'@Update\("(.*?)"\)',source).group(1)
def bind(text,ref):
    values={'id':'9','type':"'PAYMENT'",'ref':"'"+ref+"'",'amount':'18.50'}
    return re.sub(r'#\{(\w+)\}',lambda m:values[m[1]],text)
def acquire(ref): return bind(ensure,ref)+';'+bind(lock,ref)+';'
try:
    sql('CREATE DATABASE '+schema+';',False)
    sql((root/'docs/database/migrations/user_db/V8__balance_settlement_guard.sql').read_text(encoding='utf-8'))
    sql('CREATE TABLE ledger(ref VARCHAR(64) PRIMARY KEY, amount DECIMAL(10,2));')
    sql((root/'docs/database/migrations/payment_db/V2__order_payment_gate.sql').read_text(encoding='utf-8'))
    sql("CREATE TABLE orders(order_id BIGINT PRIMARY KEY,status VARCHAR(20),created_at DATETIME);INSERT INTO orders VALUES(1,'待支付','2026-11-04 12:00:00');")
    sql((root/'docs/database/migrations/order_db/V5__order_payment_timeout.sql').read_text(encoding='utf-8'))
    assert sql("SELECT TIMESTAMPDIFF(SECOND,created_at,expires_at) FROM orders WHERE order_id=1;")=='900'
    from datetime import datetime,timezone,timedelta
    expected=int(datetime(2026,11,4,12,15,tzinfo=timezone(timedelta(hours=8))).timestamp()*1000)
    assert sql("SELECT expires_at FROM order_timeout_outbox WHERE order_id=1;")==str(expected)

    # Close wins, then an already in-flight deduction resumes: observe closed=1 and leave ledger empty.
    sql('START TRANSACTION;'+acquire('close-first')+bind(close,'close-first')+';COMMIT;')
    state=sql('START TRANSACTION;'+acquire('close-first')+'SELECT closed FROM balance_settlement_guard WHERE biz_ref=\'close-first\';COMMIT;')
    assert state.splitlines()[-1]=='1'
    assert sql("SELECT COUNT(*) FROM ledger WHERE ref='close-first';")=='0'
    # A committed payment must survive closure checks.
    sql('START TRANSACTION;'+acquire('paid-first')+"INSERT INTO ledger VALUES('paid-first',18.50);COMMIT;")
    assert sql('START TRANSACTION;'+acquire('paid-first')+"SELECT COUNT(*) FROM ledger WHERE ref='paid-first';COMMIT;").splitlines()[-1]=='1'
    # Two real connections contend on the mapper's same unique key / row lock.
    def worker(closing):
        if closing:
            action="SELECT COUNT(*) INTO @paid FROM ledger WHERE ref='concurrent';"+bind(close,'concurrent')+" AND @paid=0"
        else:
            action="SELECT closed INTO @closed FROM balance_settlement_guard WHERE user_id=9 AND biz_type='PAYMENT' AND biz_ref='concurrent';INSERT INTO ledger SELECT 'concurrent',18.50 WHERE @closed=0"
        return sql('START TRANSACTION;'+acquire('concurrent')+'DO SLEEP(0.2);'+action+';COMMIT;')
    with ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(worker,[True,False]))
    assert sql("SELECT COUNT(*) FROM balance_settlement_guard WHERE biz_ref='concurrent';")=='1'
    count=sql("SELECT COUNT(*) FROM ledger WHERE ref='concurrent';")
    closed=sql("SELECT closed FROM balance_settlement_guard WHERE biz_ref='concurrent';")
    assert (closed,count) in (('1','0'),('0','1')),'only closed-without-charge or paid-without-close may commit'

    print('Settlement SQL checks passed: disposable schema, closed-before-deduct, paid-before-close, concurrent unique-key/row-lock serialization. Java services were not executed.')
finally:
    sql('DROP DATABASE IF EXISTS '+schema+';',False)
