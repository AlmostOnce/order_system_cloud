"""Check the login Mapper SQL against the local MySQL without changing data."""
import os
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'user-service/src/main/java/com/hbue/ordering/user/mapper/UserMapper.java').read_text(encoding='utf-8')
match = re.search(r'@Select\("""\s*([^\"]+?)\s*"""\)\s*UserDO selectByAccountForLogin', source, re.S)
if not match:
    raise SystemExit('未找到登录查询 SQL')
sql = match.group(1).replace('#{account}', '?')
jar = Path.home() / '.m2/repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar'
if not jar.exists():
    raise SystemExit('缺少本机 MySQL JDBC 驱动')
env = os.environ.copy()
for line in (root / '.env').read_text(encoding='utf-8').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        if key in ('USER_DB_USERNAME', 'USER_DB_PASSWORD'):
            env.setdefault(key, value.strip().strip('"').strip("'"))
env.setdefault('USER_DB_USERNAME', 'user_service')
env.setdefault('USER_DB_PASSWORD', 'user_password_dev_2026')
env['LOGIN_SQL_CHECK'] = sql
java = r'''import java.sql.*;
public class LoginCollationCheck {
  static Long lookup(Connection c, String sql, String value, int params) throws Exception {
    try (PreparedStatement p = c.prepareStatement(sql)) {
      for (int i = 1; i <= params; i++) p.setString(i, value);
      try (ResultSet r = p.executeQuery()) { return r.next() ? r.getLong(1) : null; }
    }
  }
  public static void main(String[] args) throws Exception {
    String url = "jdbc:mysql://127.0.0.1:9095/user_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
    try (Connection c = DriverManager.getConnection(url, System.getenv("USER_DB_USERNAME"), System.getenv("USER_DB_PASSWORD"))) {
      String sql = System.getenv("LOGIN_SQL_CHECK");
      lookup(c, sql, "\u4e2d\u6587@example.invalid", 2);
      lookup(c, sql, "\ud83c\udf5c@example.invalid", 2);
      try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT user_no FROM users WHERE user_no IS NOT NULL LIMIT 1")) {
        if (!r.next()) throw new IllegalStateException("没有用户号用于只读校验");
        String number = r.getString(1);
        Long expected = lookup(c, "SELECT user_id FROM users WHERE user_no = ? LIMIT 1", number, 1);
        if (!java.util.Objects.equals(expected, lookup(c, sql, number, 2))) throw new AssertionError("用户号查询结果不一致");
      }
      try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT email FROM users WHERE email IS NOT NULL AND email <> '' LIMIT 1")) {
        if (!r.next()) throw new IllegalStateException("没有邮箱用于只读校验");
        String email = r.getString(1);
        Long expected = lookup(c, "SELECT user_id FROM users WHERE email = ? LIMIT 1", email, 1);
        if (!java.util.Objects.equals(expected, lookup(c, sql, email, 2))) throw new AssertionError("邮箱查询结果不一致");
      }
      System.out.println("用户号及邮箱登录 SQL 校验通过（只读）");
    }
  }
}'''
with tempfile.TemporaryDirectory(prefix='login-collation-') as tmp:
    path = Path(tmp) / 'LoginCollationCheck.java'
    path.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(path)], check=True)
    subprocess.run(['java', '-cp', os.pathsep.join((tmp, str(jar))), 'LoginCollationCheck'], env=env, check=True)
