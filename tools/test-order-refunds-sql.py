"""单连接临时表验证真实退款Mapper SQL和迁移；不会向业务表写入测试订单或资金。"""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
commands = ['SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;']
expected = {}


def sql_for(module, mapper, method, values):
    """取实际注解SQL，绑定本文件固定样本值；测试不会另写一套业务更新条件。"""
    source = (ROOT / module / 'src/main/java/com/hbue/ordering' / mapper).read_text(encoding='utf-8')
    end = source.index(' '+method+'(')
    begin = max(source.rfind('@Select(',0,end),source.rfind('@Update(',0,end))
    assert begin>=0, method
    annotation = source[begin:end]
    match = re.search(r'@(Select|Update)\(("""[\s\S]*?"""|"[^"\n]*")\)', annotation)
    assert match, method
    text = match[2].strip('"')
    return re.sub(r'#\{(\w+)\}', lambda m: str(values[m[1]]) if isinstance(values[m[1]], int) else "'" + values[m[1]].replace("'", "''") + "'", text).strip()


def check(name, query, value):
    if query == "SELECT ROW_COUNT()":
        commands.append("SET @refund_check_count=ROW_COUNT();")
        query="SELECT @refund_check_count"
    commands.extend(["SELECT 'CASE:"+name+"';", query+';'])
    expected[name] = [str(x) for x in value]


# 使用独立临时表名，断开连接后测试数据自动消失。
for database, tables, migration in [
    ('order_db', ['orders','order_items'], 'V7__create_item_refunds.sql'),
    ('user_db', ['balance_records'], 'V10__balance_refund_origin.sql'),
    ('stock_db', ['stock_flow'], 'V3__partial_refund_origin.sql')]:
    commands.append('USE '+database+';')
    for table in tables:
        commands.append('CREATE TEMPORARY TABLE '+table+' LIKE '+table+';')
    text = (ROOT/'docs/database/migrations'/database/migration).read_text(encoding='utf-8')
    # 业务库迁移后复验时跳过已存在的新增列，仍验证新建表的完整DDL。
    existing = subprocess.run(['wsl.exe','--exec','docker','exec','-i','order-system-cloud-mysql-1','sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names'],
        input=("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='"+database+"' AND table_name='"+tables[-1]+"' AND column_name='"+('refund_reserved_quantity' if database=='order_db' else 'original_biz_ref')+"';").encode(), capture_output=True, timeout=20)
    if existing.returncode: raise RuntimeError(existing.stderr.decode())
    if existing.stdout.strip() == b'1':
        text = re.sub(r'ALTER TABLE [\s\S]*?;', '', text)
    commands.append(text.replace('CREATE TABLE order_refund','CREATE TEMPORARY TABLE order_refund'))
commands.append('USE order_db;')
commands.append("INSERT INTO orders(order_id,order_no,user_id,window_id,total_amount,status,version,created_at,updated_at) VALUES (1,'REFUND-1',7,1,30,'已支付',0,NOW(),NOW());")
commands.append("INSERT INTO order_items(item_id,order_id,product_id,product_code_snapshot,product_name_snapshot,unit_price_snapshot,quantity,line_amount) VALUES (1,1,1,'P1','鸡肉饭',10,3,30);")
item=lambda method, **v: sql_for('order-service','order/mapper/OrderItemMapper.java',method,v)
order=lambda method, **v: sql_for('order-service','order/mapper/OrderMapper.java',method,v)
refund=lambda method, **v: sql_for('order-service','order/mapper/OrderRefundMapper.java',method,v)
commands.append(item('reserveRefund',id=1,orderId=1,quantity=2,approved=2)+';')
check('reserve', 'SELECT refund_reserved_quantity,refund_approved_quantity,refunded_quantity FROM order_items WHERE item_id=1',['2\t2\t0'])
commands.append(item('reserveRefund',id=1,orderId=1,quantity=2,approved=2)+';')
check('no_over_reserve','SELECT ROW_COUNT()',[0])
commands.append(order('updateStatusIfVersionMatches',orderId=1,expectedStatus='已支付',expectedVersion=0,targetStatus='制作中')+';')
check('partial_can_accept','SELECT ROW_COUNT()',[1])
# 部分退款仍可核销剩余餐品，完成时间首次写入后不可由退款覆盖。
commands.append("UPDATE orders SET status='待取货',pickup_code='012345' WHERE order_id=1;")
commands.append(order('completeWithPickupCode',orderId=1,version=1,code='012345')+';')
check('remaining_items_can_complete','SELECT ROW_COUNT()',[1])
check('completion_time_recorded','SELECT completed_at IS NOT NULL FROM orders WHERE order_id=1',[1])
commands.append("UPDATE orders SET status='制作中',version=1,pickup_code='012345' WHERE order_id=1;")
commands.append(item('reserveRefund',id=1,orderId=1,quantity=1,approved=1)+';')
commands.append(order('updateStatusIfVersionMatches',orderId=1,expectedStatus='制作中',expectedVersion=1,targetStatus='待取货')+';')
check('all_approved_blocks_fulfillment','SELECT ROW_COUNT()',[0])
commands.append("UPDATE orders SET status='待取货' WHERE order_id=1;")
commands.append(order('completeWithPickupCode',orderId=1,version=1,code='012345')+';')
check('all_approved_blocks_pickup','SELECT ROW_COUNT()',[0])

commands.append(item('releaseRefund',id=1,quantity=1)+';')
check('approved_cannot_withdraw','SELECT ROW_COUNT()',[0])
commands.append(item('finishRefund',id=1,quantity=3)+';')
commands.append(order('finishRefundedOrder',id=1)+';')
check('all_refunded','SELECT status,pickup_code FROM orders WHERE order_id=1',['已退款\tNULL'])
commands.append(item('finishRefund',id=1,quantity=1)+';')
check('no_double_finish','SELECT ROW_COUNT()',[0])
commands.append("UPDATE orders SET status='已完成',completed_at='2026-11-01 12:00:00' WHERE order_id=1;")
commands.append(order('finishRefundedOrder',id=1)+';')
check('completed_preserved',"SELECT status,completed_at FROM orders WHERE order_id=1",['已完成\t2026-11-01 12:00:00'])
commands.append("INSERT INTO order_refunds(refund_id,refund_no,request_key,order_id,user_id,item_id,product_id,product_name,quantity,amount,reason,status,original_status,due_at,next_attempt_at) VALUES (1,REPEAT('a',32),'request-1',1,7,1,1,'鸡肉饭',1,10,'少餐','PENDING','制作中',DATE_SUB(NOW(),INTERVAL 1 SECOND),NOW()),(2,REPEAT('b',32),'request-2',1,7,1,1,'鸡肉饭',1,10,'少餐','PENDING','制作中',DATE_ADD(NOW(),INTERVAL 1 HOUR),NOW()),(3,REPEAT('c',32),'request-3',1,7,1,1,'鸡肉饭',1,10,'少餐','APPEALED','制作中',DATE_SUB(NOW(),INTERVAL 1 HOUR),NOW());")
check('only_pending_due',refund('expired'),[1])
commands.append("UPDATE order_refunds SET status='APPROVED' WHERE refund_id=1;")
commands.append(refund('claim',id=1)+';')
check('claim_once','SELECT ROW_COUNT()',[1])
commands.append(refund('claim',id=1)+';')
check('no_concurrent_claim','SELECT ROW_COUNT()',[0])
commands.append("UPDATE order_refunds SET next_attempt_at=DATE_SUB(NOW(),INTERVAL 61 SECOND) WHERE refund_id=1;")
commands.append(refund('claim',id=1)+';')
check('lease_recovers','SELECT ROW_COUNT()',[1])
commands.append('USE stock_db;')
commands.append("INSERT INTO stock_flow(biz_type,biz_ref,stock_id,window_id,product_id,change_qty,before_qty,after_qty,original_biz_ref) VALUES ('ORDER_DEDUCT','REFUND-1',1,1,1,-3,10,7,NULL),('REFUND_RESTORE',REPEAT('a',32),1,1,1,1,7,8,'REFUND-1');")
stock=lambda: sql_for('stock-service','stock/mapper/StockFlowMapper.java','refundableQuantity',dict(stockId=1,orderNo='REFUND-1'))
check('partial_stock_cap',stock(),[2])
commands.append("INSERT IGNORE INTO stock_flow(biz_type,biz_ref,stock_id,window_id,product_id,change_qty,before_qty,after_qty,original_biz_ref) VALUES ('REFUND_RESTORE',REPEAT('a',32),1,1,1,1,7,8,'REFUND-1');")
check('stock_refund_number_unique','SELECT ROW_COUNT()',[0])

commands.append("INSERT INTO stock_flow(biz_type,biz_ref,stock_id,window_id,product_id,change_qty,before_qty,after_qty) VALUES ('ORDER_RESTORE','REFUND-1',1,1,1,2,8,10);")
check('whole_and_partial_share_cap',stock(),[0])
commands.append('USE user_db;')
commands.append("INSERT INTO balance_records(user_id,change_type,amount,balance_after,biz_type,biz_ref,original_biz_ref) VALUES (7,'PAY',30,20,'PAYMENT','PAY1',NULL),(7,'REFUND',10,30,'PAYMENT_REFUND',REPEAT('a',32),'PAY1'),(8,'REFUND',99,99,'PAYMENT_REFUND',REPEAT('b',32),'PAY1'),(7,'REFUND',99,99,'PAYMENT_REFUND',REPEAT('c',32),'PAY2');")
commands.append("INSERT IGNORE INTO balance_records(user_id,change_type,amount,balance_after,biz_type,biz_ref,original_biz_ref) VALUES (7,'REFUND',10,30,'PAYMENT_REFUND',REPEAT('a',32),'PAY1');")
check('balance_refund_number_unique','SELECT ROW_COUNT()',[0])
check('refund_sum_isolates_account_and_payment',sql_for('user-service','user/mapper/BalanceRecordMapper.java','sumRefunded',dict(userId=7,paymentNo='PAY1')),['10.00'])
command=['wsl.exe','--exec','docker','exec','-i','order-system-cloud-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4']
text = '\n'.join(commands)
for table in ['orders','order_items','order_refunds','order_refund_actions','balance_records','stock_flow']:
    text = re.sub(r'\b'+table+r'\b', 'codex_refund_'+table, text)
    text = text.replace('LIKE codex_refund_'+table, 'LIKE '+table)
run=subprocess.run(command,input=text.encode('utf-8'),capture_output=True,timeout=30)
if run.returncode: raise RuntimeError(run.stderr.decode('utf-8',errors='replace'))
actual={}; current=None
for line in run.stdout.decode('utf-8').splitlines():
    if line.startswith('CASE:'): current=line[5:];actual[current]=[]
    elif current is not None: actual[current].append(line)
for name,value in expected.items(): assert actual[name]==value,(name,actual[name],value)
print('退款SQL检查通过：'+str(len(expected))+'项真实Mapper断言；迁移DDL、数量上限、履约拦截、完成时间、超时队列、租约恢复、资金及库存原单隔离。仅写单连接临时表。')
