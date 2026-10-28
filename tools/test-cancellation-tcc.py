"""JDK-only TCC state checks using isolated in-memory collaborators; no Maven or live services."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'order-service/src/main/java/com/hbue/ordering/order/notification'
cache = Path.home() / '.m2/repository/org/springframework'
jars = [cache / f'spring-{name}/6.2.11/spring-{name}-6.2.11.jar' for name in ('context', 'tx', 'core')]
jars += [Path.home() / '.m2/repository/org/apache/seata/seata-all/2.5.0/seata-all-2.5.0.jar',
         Path.home() / '.m2/repository/org/springframework/boot/spring-boot-autoconfigure/3.5.6/spring-boot-autoconfigure-3.5.6.jar']
if not all(p.exists() for p in jars):
    raise SystemExit('需要本机已有的 Spring 6.2.11 依赖才能运行此检查')

store = '''package com.hbue.ordering.order.notification;
import java.util.*;
public class CancellationNotificationTccStore {
 public record Row(long orderId,int orderVersion,long recipientId,String status) {}
 public final Map<String,Row> rows=new HashMap<>();
 public void insertIfMissing(String x,long o,int v,long u,String s){rows.putIfAbsent(x,new Row(o,v,u,s));}
 public Row lock(String x){return rows.get(x);}
 public void setStatus(String x,String s){Row r=rows.get(x);rows.put(x,new Row(r.orderId(),r.orderVersion(),r.recipientId(),s));}
}'''
outbox = '''package com.hbue.ordering.order.notification;
import java.util.*;
public class OrderNotificationOutboxWriter {
 public final List<OrderNotificationEvent> events=new ArrayList<>();
 public void record(OrderNotificationEvent e){events.add(e);}
}'''
test = '''package com.hbue.ordering.order.notification;
public class TestCancellationTcc {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void rejected(Runnable f){try{f.run();throw new AssertionError("expected failure");}catch(IllegalStateException expected){}}
 public static void main(String[] args){
  var s=new CancellationNotificationTccStore();var o=new OrderNotificationOutboxWriter();var t=new CancellationNotificationTccTransactions(s,o);
  check(t.prepare("one",7,2,9));check(o.events.isEmpty());check(t.confirm("one"));check(t.confirm("one"));check(o.events.size()==1);check(o.events.get(0).status().equals("已取消"));
  check(t.cancel("two",8,2,10));check(t.cancel("two",8,2,10));rejected(()->t.prepare("two",8,2,10));check(o.events.size()==1);
  check(t.prepare("three",8,2,10));check(t.cancel("three",8,2,10));rejected(()->t.confirm("three"));check(o.events.size()==1);
  rejected(()->t.cancel("one",7,2,9));rejected(()->t.confirm("missing"));
  System.out.println("TCC 状态检查通过：预备、确认幂等、空回滚、防悬挂及回滚后不发送");
 }
}'''
with tempfile.TemporaryDirectory(prefix='notification-tcc-') as directory:
    base = Path(directory)
    files = []
    for name, text in [('CancellationNotificationTccStore.java', store), ('OrderNotificationOutboxWriter.java', outbox), ('TestCancellationTcc.java', test)]:
        path = base / name
        path.write_text(text, encoding='utf-8')
        files.append(str(path))
    cp = os.pathsep.join(map(str, jars))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', str(base), *files,
                    str(source / 'OrderNotificationEvent.java'), str(source / 'CancellationNotificationTccTransactions.java'),
                    str(source / 'CancellationNotificationTcc.java'), str(source / 'CancellationNotificationTccImpl.java')], check=True)
    subprocess.run(['java', '-cp', str(base) + os.pathsep + cp, 'com.hbue.ordering.order.notification.TestCancellationTcc'], check=True)
