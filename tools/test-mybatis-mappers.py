"""Run isolated MyBatis mapper contracts against local MySQL; all fixtures roll back."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
repo = Path.home() / '.m2/repository'
jars = [repo / p for p in (
    'org/mybatis/mybatis/3.5.16/mybatis-3.5.16.jar',
    'com/baomidou/mybatis-plus-core/3.5.8/mybatis-plus-core-3.5.8.jar',
    'com/baomidou/mybatis-plus-annotation/3.5.8/mybatis-plus-annotation-3.5.8.jar',
    'com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar',
)]
if not all(jar.exists() for jar in jars):
    raise SystemExit('缺少本机 MyBatis 或 MySQL JDBC 依赖')
env = os.environ.copy()
env_file = root / '.env'
for line in (env_file.read_text(encoding='utf-8').splitlines() if env_file.exists() else []):
    if line and not line.lstrip().startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        if key in ('COMMENT_DB_USERNAME', 'COMMENT_DB_PASSWORD', 'PRODUCT_DB_USERNAME', 'PRODUCT_DB_PASSWORD', 'MYSQL_USER', 'MYSQL_PASSWORD'):
            env.setdefault(key, value.strip().strip('"').strip("'"))
env.setdefault('COMMENT_DB_USERNAME', 'comment_service')
env.setdefault('NOTIFICATION_DB_USERNAME', 'notification_service')
env.setdefault('NOTIFICATION_DB_PASSWORD', 'notification_password_dev_2026')
if any(not env.get(key) for key in ('COMMENT_DB_PASSWORD', 'PRODUCT_DB_USERNAME', 'PRODUCT_DB_PASSWORD', 'MYSQL_USER', 'MYSQL_PASSWORD')):
    raise SystemExit('缺少数据库测试凭据，请先配置本地数据库环境')

java = r'''import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import com.hbue.ordering.comment.mapper.*;
import com.hbue.ordering.comment.model.CommentRowDO;
import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.order.mapper.CancellationNotificationTccMapper;
import com.hbue.ordering.product.mapper.ProductRatingMapper;

public class MapperContractCheck {
 static void ok(boolean value, String message) { if (!value) throw new AssertionError(message); }
 static SqlSessionFactory factory(String db, String user, String pass, Class<?>... mappers) {
  DataSource ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", "jdbc:mysql://127.0.0.1:9095/"+db+"?serverTimezone=Asia/Shanghai",user,pass);
  var config = new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
  config.setMapUnderscoreToCamelCase(true);
  for (var mapper:mappers) config.addMapper(mapper);
  return new SqlSessionFactoryBuilder().build(config);
 }
 public static void main(String[] args) {
  long seed = 900000000L + (System.nanoTime() & 0xfffffff); String id=UUID.randomUUID().toString(); String key=UUID.randomUUID().toString();
  try (var s=factory("notification_db",System.getenv("NOTIFICATION_DB_USERNAME"),System.getenv("NOTIFICATION_DB_PASSWORD"),NotificationMapper.class).openSession(false)) {
   var m=s.getMapper(NotificationMapper.class);m.insertOrderEvent(id,seed,"test-"+id,seed,"测试");
   ok(m.existsForUser(seed,id),"通知查询");ok(m.unreadCount(seed)==1,"未读数量");
   var rows=m.page(seed,null,20);ok(rows.size()==1 && id.equals(rows.getFirst().getNotificationId()),"通知映射");
   ok(m.page(seed+1,id,20).isEmpty(),"游标隔离");m.markRead(seed,id);ok(m.unreadCount(seed)==0,"已读状态");s.rollback();
  }
  try (var s=factory("comment_db",System.getenv("COMMENT_DB_USERNAME"),System.getenv("COMMENT_DB_PASSWORD"),CommentMapper.class,CommentWriteMapper.class,WindowReviewMapper.class,RatingBatchMapper.class).openSession(false)) {
   var w=s.getMapper(CommentWriteMapper.class);var c=s.getMapper(CommentMapper.class);var b=s.getMapper(RatingBatchMapper.class);var r=s.getMapper(WindowReviewMapper.class);
   var row=new CommentRowDO(id,seed,"测试",seed,seed,seed,5,key,"a".repeat(64),LocalDateTime.now());
   w.insertComment(row);w.updateRating(seed,5);var stats=w.lockRating(seed);ok(stats!=null && stats.reviewCount()==1,"评分锁定映射");
   w.insertOutbox(id,seed,stats.revision(),"{}");ok(w.pending().stream().anyMatch(e->id.equals(e.id())),"outbox映射");
   ok(w.retry(id,"test")==1,"outbox重试");ok(w.markPublished(id)==1 && w.markPublished(id)==0,"outbox确认幂等");
   ok(id.equals(c.byId(id).commentId()) && c.page(seed,null,null,20).size()==1,"评论映射和分页");
   ok(b.products(List.of(seed)).size()==1,"菜品批量评分");
   var wr=new WindowReviewMapper.Row(UUID.randomUUID().toString(),seed,"测试",seed+1,seed,4,"测试","[]","[]",UUID.randomUUID().toString(),"a".repeat(64),LocalDateTime.now());
   r.insert(wr);ok(r.byRequest(seed,wr.requestKey()).id().equals(wr.id()),"店铺评价映射");
   ok(r.rating(seed).reviewCount()==1 && b.windows(List.of(seed)).size()==1,"店铺评分");s.rollback();
  }
  try (var s=factory("order_db",System.getenv("MYSQL_USER"),System.getenv("MYSQL_PASSWORD"),CancellationNotificationTccMapper.class).openSession(false)) {
   var m=s.getMapper(CancellationNotificationTccMapper.class);m.insertIfMissing(id,seed,2,seed,"PREPARED");
   ok(m.lock(id).orderId()==seed,"TCC 状态映射");m.setStatus(id,"CANCELLED");ok(m.lock(id).status().equals("CANCELLED"),"TCC 状态更新");s.rollback();
  }
  try (var s=factory("product_db",System.getenv("PRODUCT_DB_USERNAME"),System.getenv("PRODUCT_DB_PASSWORD"),ProductRatingMapper.class).openSession(false)) {
   long product;
   try (var statement=s.getConnection().createStatement();var result=statement.executeQuery("SELECT product_id FROM products LIMIT 1")) {
    if (!result.next()) throw new AssertionError("需要至少一道菜品验证评分映射"); product=result.getLong(1);
   } catch (java.sql.SQLException error) { throw new RuntimeException(error); }
   var m=s.getMapper(ProductRatingMapper.class);m.upsert(product,2,9,1000000000L);
   ok(m.get(product).reviewCount()==2 && m.get(product).scoreTotal()==9,"商品评分映射");
   m.upsert(product,1,1,999999999L);ok(m.get(product).reviewCount()==2,"旧版本不覆盖新评分");s.rollback();
  }
  System.out.println("通知、评论、评分与 TCC Mapper 的 MySQL 事务映射检查通过；测试数据已回滚。");
 }
}'''
sources = [root / p for p in (
    'notification-service/src/main/java/com/hbue/ordering/notification/model/NotificationDO.java',
    'notification-service/src/main/java/com/hbue/ordering/notification/mapper/NotificationMapper.java',
    'comment-service/src/main/java/com/hbue/ordering/comment/model/CommentRowDO.java',
    'comment-service/src/main/java/com/hbue/ordering/comment/mapper/CommentMapper.java',
    'comment-service/src/main/java/com/hbue/ordering/comment/mapper/CommentWriteMapper.java',
    'comment-service/src/main/java/com/hbue/ordering/comment/mapper/WindowReviewMapper.java',
    'comment-service/src/main/java/com/hbue/ordering/comment/mapper/RatingBatchMapper.java',
    'order-service/src/main/java/com/hbue/ordering/order/mapper/CancellationNotificationTccMapper.java',
    'product-service/src/main/java/com/hbue/ordering/product/mapper/ProductRatingMapper.java',
)]
with tempfile.TemporaryDirectory(prefix='mapper-contract-') as directory:
    base = Path(directory)
    check = base / 'MapperContractCheck.java'
    check.write_text(java, encoding='utf-8')
    cp = os.pathsep.join(map(str, [*jars, root / 'comment-api/target/classes']))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(base), str(check), *map(str, sources)], check=True, env=env)
    subprocess.run(['java', '-cp', str(base) + os.pathsep + cp, 'MapperContractCheck'], check=True, env=env)
