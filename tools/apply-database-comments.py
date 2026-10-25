"""Generate/apply Chinese MySQL comments without changing column definitions or data."""
from pathlib import Path
from datetime import datetime, timezone, timedelta
import argparse
import hashlib
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true', help='Back up and execute the generated SQL')
options = parser.parse_args()
config = json.loads((ROOT / 'docs/database/chinese_comments.json').read_text(encoding='utf8'))
values = {}
for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip().strip('\"\'')
# Windows 沿用 WSL 中的 Docker；Linux 直接调用本机 Docker。
# 两种环境使用相同的数据库查询、备份和结构校验流程。
docker_command = (['wsl', '-d', 'Ubuntu-24.04', '--', 'docker']
                  if sys.platform == 'win32' else ['docker'])
prefix = docker_command + ['exec', '-i', '-e',
          'MYSQL_PWD=' + values['MYSQL_ROOT_PASSWORD'], 'order-system-cloud-mysql-1']


def query(sql):
    result = subprocess.run(prefix + ['mysql', '-uroot', '--batch', '--raw',
                            '--skip-column-names', '--default-character-set=utf8mb4'],
                            input=sql.encode(), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode())
    return result.stdout.decode().strip()


def quote(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "''") + "'"


schemas = sorted({name.split('.')[0] for name in config['tables']})
schema_list = ','.join(map(quote, schemas))


def metadata():
    fields = ['TABLE_SCHEMA', 'TABLE_NAME', 'COLUMN_NAME', 'ORDINAL_POSITION', 'COLUMN_DEFAULT',
              'IS_NULLABLE', 'DATA_TYPE', 'COLUMN_TYPE', 'CHARACTER_SET_NAME', 'COLLATION_NAME',
              'COLUMN_KEY', 'EXTRA', 'GENERATION_EXPRESSION', 'COLUMN_COMMENT']
    sql = 'SELECT JSON_ARRAYAGG(JSON_OBJECT(' + ','.join(quote(f) + ',' + f for f in fields)
    sql += ')) FROM information_schema.columns WHERE table_schema IN (' + schema_list + ');'
    rows = json.loads(query(sql))
    return sorted(rows, key=lambda row: (row['TABLE_SCHEMA'], row['TABLE_NAME'], row['ORDINAL_POSITION']))


before = metadata()
actual = {row['TABLE_SCHEMA'] + '.' + row['TABLE_NAME'] for row in before}
assert actual == set(config['tables']), 'Unmapped/new table found; review before modifying comments'
column_comments = {}
for row in before:
    table = row['TABLE_SCHEMA'] + '.' + row['TABLE_NAME']
    name = row['COLUMN_NAME']
    comment = config['tables'][table]['columns'].get(name, config['common_columns'].get(name))
    assert comment and re.search('[\u4e00-\u9fff]', comment), 'Missing Chinese comment: ' + table + '.' + name
    column_comments[(table, name)] = comment

comment_pattern = re.compile(r"\s+COMMENT(?:\s*=\s*|\s+)'(?:\\.|''|[^'\\])*'", re.IGNORECASE)
definitions = {}
forward = ['-- 根据当前 SHOW CREATE TABLE 生成；仅修改注释，保留完整字段定义、索引和约束。',
           '-- UTF-8 编码；请在业务表迁移完成后执行。执行前应备份表结构。',
           'SET NAMES utf8mb4;', 'SET SESSION lock_wait_timeout = 10;']
rollback = ['SET NAMES utf8mb4;']
dictionary = ['# 数据库中文数据字典', '', '覆盖当前六个业务数据库；金额单位为元，数量单位为份。', '']
for table, spec in config['tables'].items():
    database, name = table.split('.')
    quoted = '`' + database + '`.`' + name + '`'
    raw = query('SHOW CREATE TABLE ' + quoted + ';').split('\t', 1)[1]
    definitions[table] = raw
    table_columns = [r['COLUMN_NAME'] for r in before
                     if r['TABLE_SCHEMA'] == database and r['TABLE_NAME'] == name]
    clauses = []
    original_clauses = []
    for line in raw.splitlines():
        match = re.match(r'\s+`([^`]+)`\s+(.+)', line)
        if not match or match[1] not in table_columns:
            continue
        column, definition = match[1], match[2].removesuffix(',')
        original_clauses.append('MODIFY COLUMN `' + column + '` ' + definition)
        clean = comment_pattern.sub('', definition)
        clauses.append('MODIFY COLUMN `' + column + '` ' + clean +
                       ' COMMENT ' + quote(column_comments[(table, column)]))
    assert len(clauses) == len(table_columns), 'Could not preserve every column: ' + table
    assert all(re.fullmatch('[a-z_][a-z0-9_]*', c) for c in table_columns)
    old_table_comment = query('SELECT JSON_QUOTE(table_comment) FROM information_schema.tables '
                              'WHERE table_schema=' + quote(database) + ' AND table_name=' + quote(name) + ';')
    clauses.append('COMMENT = ' + quote(spec['comment']))
    original_clauses.append('COMMENT = ' + quote(json.loads(old_table_comment)))
    forward.append('\nALTER TABLE ' + quoted + '\n    ' + ',\n    '.join(clauses) + ',\n    ALGORITHM=INPLACE, LOCK=NONE;')
    rollback.append('\nALTER TABLE ' + quoted + '\n    ' + ',\n    '.join(original_clauses) + ';')
    # MODIFY may rewrite CHECK string-literal charset introducers. Reassert the
    # original expression atomically so the final constraint definition is preserved.
    for line in raw.splitlines():
        check = re.match(r'\s*CONSTRAINT `([^`]+)` CHECK .+', line)
        if check and re.search(r"\b_[a-z0-9]+'", line, re.IGNORECASE):
            restore = ('\nALTER TABLE ' + quoted + ' DROP CHECK `' + check[1] +
                       '`, ADD ' + line.strip().removesuffix(',') + ';')
            forward.append(restore)
            rollback.append(restore)
    dictionary += ['## ' + table + '：' + spec['comment'], '', '| 字段 | 类型 | 中文说明 |', '|---|---|---|']
    for row in before:
        if row['TABLE_SCHEMA'] == database and row['TABLE_NAME'] == name:
            dictionary.append('| ' + row['COLUMN_NAME'] + ' | ' + row['COLUMN_TYPE'] + ' | ' +
                              column_comments[(table, row['COLUMN_NAME'])] + ' |')
    dictionary.append('')

sql = '\n'.join(forward) + '\n'
sql_file = ROOT / 'docs/database/all_chinese_comments.sql'
sql_file.write_text(sql, encoding='utf8')
(ROOT / 'docs/database/DATA_DICTIONARY.md').write_text('\n'.join(dictionary), encoding='utf8')
print('Generated comments for', len(actual), 'tables and', len(before), 'columns.')
if not options.apply:
    print('Dry run: SQL generated only; database not modified.')
    raise SystemExit(0)

stamp = datetime.now(timezone(timedelta(hours=8))).strftime('%Y%m%d_%H%M%S')
output = Path.home() / '.codex/backups/order_system_cloud' / ('chinese_comments_' + stamp)
output.mkdir(parents=True, exist_ok=False)
dump = subprocess.run(prefix + ['mysqldump', '-uroot', '--no-data', '--no-tablespaces',
                      '--set-gtid-purged=OFF', '--default-character-set=utf8mb4', '--databases', *schemas],
                      capture_output=True)
if dump.returncode:
    raise RuntimeError(dump.stderr.decode())
assert b'CREATE TABLE' in dump.stdout
(output / 'schema_before_comments.sql').write_bytes(dump.stdout)
(output / 'rollback_comments.sql').write_text('\n'.join(rollback), encoding='utf8')
(output / 'metadata_before.json').write_text(json.dumps(before, ensure_ascii=False), encoding='utf8')


def checksums():
    result = {}
    for table in config['tables']:
        quoted = '.'.join('`' + part + '`' for part in table.split('.'))
        result[table] = query('CHECKSUM TABLE ' + quoted + ' EXTENDED;').split('\t')[1]
        assert result[table] != 'NULL', 'Cannot verify row checksum: ' + table
    return result


before_checksums = checksums()
(output / 'checksums_before.json').write_text(json.dumps(before_checksums, indent=2), encoding='utf8')
query(sql)
after = metadata()
without_comments = lambda rows: [{k: v for k, v in r.items() if k != 'COLUMN_COMMENT'} for r in rows]
assert without_comments(before) == without_comments(after), 'Non-comment column metadata changed'
for row in after:
    table = row['TABLE_SCHEMA'] + '.' + row['TABLE_NAME']
    assert row['COLUMN_COMMENT'] == column_comments[(table, row['COLUMN_NAME'])]
for table, original in definitions.items():
    quoted = '.'.join('`' + part + '`' for part in table.split('.'))
    current = query('SHOW CREATE TABLE ' + quoted + ';').split('\t', 1)[1]
    # Ignore comments and the allocator counter; verify all indexes, foreign keys and CHECK clauses.
    # MySQL may render an already-implied character set explicitly after MODIFY.
    # Exact character sets/collations were independently checked in column metadata above.
    normalize = lambda ddl: re.sub(r'\s+CHARACTER SET \w+', '',
                            re.sub(r'AUTO_INCREMENT=\d+', 'AUTO_INCREMENT=N', comment_pattern.sub('', ddl)))
    assert normalize(current) == normalize(original), 'Non-comment table DDL changed: ' + table
    database, name = table.split('.')
    actual_comment = json.loads(query('SELECT JSON_QUOTE(table_comment) FROM information_schema.tables '
                               'WHERE table_schema=' + quote(database) + ' AND table_name=' + quote(name) + ';'))
    assert actual_comment == config['tables'][table]['comment']
after_checksums = checksums()
assert before_checksums == after_checksums, 'Table row checksum changed; inspect concurrent writes'
(output / 'manifest.json').write_text(json.dumps({
    'schemas': schemas, 'tables': len(actual), 'columns': len(after),
    'sql_sha256': hashlib.sha256(sql.encode()).hexdigest(),
    'schema_backup_sha256': hashlib.sha256(dump.stdout).hexdigest(),
    'column_metadata_preserved': True, 'indexes_constraints_preserved': True,
    'row_checksums_unchanged': True, 'table_checksums': after_checksums
}, indent=2), encoding='utf8')
print('Applied and verified: all comments are Chinese; column metadata, indexes, constraints and row checksums unchanged.')
print('Backup:', output)
