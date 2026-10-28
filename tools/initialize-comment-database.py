"""Initialize only the comment schema and product rating projection; never reset data."""
from pathlib import Path
from datetime import datetime
import subprocess

root = Path(__file__).resolve().parents[1]
env = dict(line.split('=', 1) for line in (root / '.env').read_text(encoding='utf-8-sig').splitlines()
           if '=' in line and not line.lstrip().startswith('#'))
prefix = ['wsl', '-d', 'Ubuntu-24.04', '--', 'docker', 'exec', '-i', '-e',
          'MYSQL_PWD=' + env['MYSQL_ROOT_PASSWORD'].strip().strip('\"\''), 'order-system-cloud-mysql-1']

def mysql(sql, account='root', password=None):
    command = prefix.copy()
    if password is not None:
        command[command.index('-e') + 1] = 'MYSQL_PWD=' + password
    result = subprocess.run(command + ['mysql', '-u' + account, '--batch', '--skip-column-names',
                           '--default-character-set=utf8mb4'], input=sql.encode(), capture_output=True)
    if result.returncode:
        raise RuntimeError('MySQL operation failed: ' + result.stderr.decode(errors='replace'))
    return result.stdout.decode().strip()

print('Existing target tables:', mysql("SELECT CONCAT(table_schema,'.',table_name) FROM information_schema.tables WHERE table_schema='comment_db' OR (table_schema='product_db' AND table_name='product_rating_stats');") or '(none)')
backup = Path.home() / '.codex/backups/order_system_cloud' / ('comment_database_' + datetime.now().strftime('%Y%m%d_%H%M%S'))
backup.mkdir(parents=True)
databases = ['product_db']
if mysql("SELECT schema_name FROM information_schema.schemata WHERE schema_name='comment_db';"):
    databases.append('comment_db')
for database in databases:
    result = subprocess.run(prefix + ['mysqldump', '-uroot', '--single-transaction', '--skip-lock-tables',
                            '--no-tablespaces', '--set-gtid-purged=OFF', '--databases', database], capture_output=True)
    if result.returncode:
        raise RuntimeError('Backup failed; no migration executed')
    (backup / (database + '.sql')).write_bytes(result.stdout)

for name in ['comment_db/V1__create_comments.sql', 'product_db/V8__add_product_rating_projection.sql']:
    mysql((root / 'docs/database/migrations' / name).read_text(encoding='utf-8-sig'))

# Existing accounts are not silently assigned a new password.
password = env['COMMENT_DB_PASSWORD'].strip().strip('\"\'')
assert password and all(c.isalnum() or c in '_-' for c in password), 'Unsupported credential characters'
mysql("CREATE USER IF NOT EXISTS 'comment_service'@'%' IDENTIFIED BY '" + password + "';\n"
      "GRANT SELECT, INSERT, UPDATE ON comment_db.* TO 'comment_service'@'%';")
mysql('SELECT COUNT(*) FROM comment_db.comments; SELECT COUNT(*) FROM comment_db.comment_rating_stats; SELECT COUNT(*) FROM comment_db.comment_rating_outbox;', 'comment_service', password)
print('Verified tables:', mysql("SELECT CONCAT(table_schema,'.',table_name) FROM information_schema.tables WHERE table_schema='comment_db' OR (table_schema='product_db' AND table_name='product_rating_stats');"))
print('Application grants:', mysql("SHOW GRANTS FOR 'comment_service'@'%';"))
print('Backup:', backup)
