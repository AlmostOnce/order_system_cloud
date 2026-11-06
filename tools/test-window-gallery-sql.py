"""使用实际V11脚本验证回填和数量约束；仅创建连接内临时表，不迁移业务表。"""
from pathlib import Path
import subprocess

TABLE = 'codex_window_gallery_check'
migration = (Path(__file__).resolve().parents[1] / 'docs/database/migrations/product_db/V11__add_window_image_gallery.sql').read_text(encoding='utf-8')
# 只替换本次脚本涉及的表及约束名；数据库连接退出时所有样本自动清理。
migration = migration.replace('dining_windows', TABLE).replace('chk_window_gallery_size', 'chk_codex_gallery_size')
setup = f"CREATE TEMPORARY TABLE {TABLE} (window_id BIGINT PRIMARY KEY, image_file_key VARCHAR(32));\n"
setup += f"INSERT INTO {TABLE} VALUES(1,REPEAT('a',32)),(2,NULL),(3,'');\n" + migration


def run(sql):
    """认证信息仅在容器内引用，不读取或打印密码；SQL通过标准输入发送。"""
    result = subprocess.run(['wsl.exe', '--exec', 'docker', 'exec', '-i', 'order-system-cloud-mysql-1',
                             'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 product_db'],
                            input=sql.encode('utf-8'), capture_output=True, timeout=20)
    return result.returncode, result.stdout.decode('utf-8'), result.stderr.decode('utf-8')


code, output, error = run(setup + f"\nSELECT window_id,JSON_LENGTH(image_file_keys) FROM {TABLE} ORDER BY window_id;\n"
                         + f"UPDATE {TABLE} SET image_file_keys=JSON_ARRAY('a','b','c','d','e') WHERE window_id=1;\n"
                         + f"SELECT JSON_LENGTH(image_file_keys) FROM {TABLE} WHERE window_id=1;\n")
assert code == 0, error
assert output.splitlines() == ['1\t1', '2\t0', '3\t0', '5'], output
for expression in ["JSON_ARRAY('a','b','c','d','e','f')", "JSON_OBJECT('key','a')"]:
    code, _, error = run(setup + f"\nUPDATE {TABLE} SET image_file_keys={expression} WHERE window_id=1;\n")
    assert code != 0 and '3819' in error, error
print('SQL检查通过：实际V11在临时表回填旧图/空图，允许5张，拒绝6张和非数组；业务表未迁移。')
