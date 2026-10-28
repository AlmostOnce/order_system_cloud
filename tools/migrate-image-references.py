"""Preflight/back up/migrate managed image URLs. Stop old business services before --apply."""
from pathlib import Path
from datetime import datetime
import argparse
import json
import re
import socket
import subprocess
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
TARGETS = [('user_db', 'users', 'user_id', 'avatar_url', 'avatar_file_key'),
           ('product_db', 'restaurants', 'restaurant_id', 'image_url', 'image_file_key'),
           ('product_db', 'dining_windows', 'window_id', 'image_url', 'image_file_key'),
           ('product_db', 'products', 'product_id', 'image_url', 'image_file_key')]

def extract_key(value):
    if value is None or value == '':
        return None
    parts = urlsplit(value)
    if parts.scheme and parts.scheme not in ('http', 'https'):
        raise ValueError('Unsupported legacy image URL')
    if parts.query or parts.fragment:
        raise ValueError('Legacy image URL contains query or fragment')
    match = re.fullmatch(r'/api/files/([a-fA-F0-9]{32})/content', parts.path)
    if not match:
        raise ValueError('Unknown legacy image URL; import it into file service first')
    return match[1].lower()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    env = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            env[key.strip()] = value.strip().strip('\"\'')
    prefix = ['wsl', '-d', 'Ubuntu-24.04', '--', 'docker', 'exec', '-i', '-e',
              'MYSQL_PWD=' + env['MYSQL_ROOT_PASSWORD'], 'order-system-cloud-mysql-1']

    def query(sql):
        result = subprocess.run(prefix + ['mysql', '-uroot', '--batch', '--raw',
                                '--skip-column-names', '--default-character-set=utf8mb4'],
                                input=sql.encode(), capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout.decode().strip()

    snapshots = []
    for db, table, pk, old, new in TARGETS:
        columns = query(f"SELECT column_name FROM information_schema.columns WHERE table_schema='{db}' AND table_name='{table}';").splitlines()
        assert old in columns or new in columns, 'Missing both image columns: ' + table
        selected = old if old in columns else new
        raw = query(f"SELECT JSON_ARRAYAGG(JSON_OBJECT('id',`{pk}`,'value',`{selected}`)) FROM `{db}`.`{table}`;")
        rows = json.loads(raw) or []
        for row in rows:
            value = row['value']
            key = extract_key(value) if old in columns else value
            assert key is None or re.fullmatch('[a-f0-9]{32}', key), 'Invalid stored key'
            if key:
                assert query(f"SELECT COUNT(*) FROM file_db.file_objects WHERE file_key='{key}' AND is_deleted=0;") == '1', 'Missing/deleted file reference'
            row['key'] = key
        snapshots.append((db, table, pk, old, new, columns, rows))
        print(f'{db}.{table}: {len(rows)} rows, {sum(x["key"] is not None for x in rows)} images; preflight OK')
    if not args.apply:
        print('Read-only preflight complete. No schema or data changed.')
        return
    for port in (9092, 9093, 9105):
        with socket.socket() as probe:
            probe.settimeout(1)
            assert probe.connect_ex(('127.0.0.1', port)) != 0, f'Port {port} still serving old code; stop business services before migration'
    backup = Path.home() / '.codex/backups/order_system_cloud' / ('image_references_' + datetime.now().strftime('%Y%m%d_%H%M%S'))
    backup.mkdir(parents=True)
    for db in ('user_db', 'product_db'):
        result = subprocess.run(prefix + ['mysqldump', '-uroot', '--single-transaction', '--skip-lock-tables',
                                '--no-tablespaces', '--set-gtid-purged=OFF', '--databases', db], capture_output=True)
        assert result.returncode == 0, 'Backup failed; migration stopped'
        (backup / (db + '.sql')).write_bytes(result.stdout)
    (backup / 'references.json').write_text(json.dumps(snapshots, ensure_ascii=False, indent=2), encoding='utf-8')
    # Keep the old columns until every row has passed comparison. Reruns tolerate partial progress.
    for db, table, pk, old, new, columns, rows in snapshots:
        if new not in columns:
            query(f"ALTER TABLE `{db}`.`{table}` ADD COLUMN `{new}` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '图片文件键，关联文件服务，空值使用默认图片';")
        if old in columns:
            for row in rows:
                key = 'NULL' if row['key'] is None else "'" + row['key'] + "'"
                query(f"UPDATE `{db}`.`{table}` SET `{new}`={key} WHERE `{pk}`={int(row['id'])};")
        actual = json.loads(query(f"SELECT JSON_ARRAYAGG(JSON_OBJECT('id',`{pk}`,'key',`{new}`)) FROM `{db}`.`{table}`;")) or []
        assert sorted(actual, key=lambda x: x['id']) == sorted([{'id': x['id'], 'key': x['key']} for x in rows], key=lambda x: x['id']), 'Migration verification failed; old columns retained'
    for db, table, pk, old, new, columns, rows in snapshots:
        if old in columns:
            query(f'ALTER TABLE `{db}`.`{table}` DROP COLUMN `{old}`;')
    print('MySQL image references migrated and verified. Backup:', backup)

if __name__ == '__main__':
    main()
