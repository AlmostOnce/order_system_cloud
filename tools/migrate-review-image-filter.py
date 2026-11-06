"""备份并为开发库评价回填有图标记；只读Mongo引用，不改正文或评分，不启动服务。"""
from pathlib import Path
from datetime import datetime
import argparse
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = ['wsl.exe', '--exec', 'docker', 'exec', '-i']


def sql(query):
    """复用容器已有MySQL凭据，SQL从stdin传入，输出不包含密码。"""
    run = subprocess.run(BASE + ['order-system-cloud-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 comment_db'],
        input=query.encode(), capture_output=True, timeout=45)
    if run.returncode:
        raise RuntimeError(run.stderr.decode(errors='replace'))
    return run.stdout.decode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    assert sql('SELECT DATABASE();').strip() == 'comment_db'
    env = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            env[key.strip()] = value.strip().strip('\"\'')
    # 凭据仅放入mongosh stdin，不出现在命令参数、日志或备份中。
    script = "const c=new Mongo('mongodb://127.0.0.1:27017');const d=c.getDB('comment_db');"
    script += 'if(!d.auth(' + json.dumps(env['COMMENT_MONGO_USERNAME']) + ',' + json.dumps(env['COMMENT_MONGO_PASSWORD']) + ")) throw Error('Authentication failed');"
    script += r"""
const output=[];
for(const row of d.comments.find({}, {_id:1,imageFileKeys:1})) {
    if(typeof row._id!=='string' || !/^[a-f0-9-]{36}$/.test(row._id) || !Array.isArray(row.imageFileKeys)
        || row.imageFileKeys.some(key=>typeof key!=='string'||!/^[a-f0-9]{32}$/.test(key))) throw Error('Invalid comment image references');
    output.push({id:row._id,hasImages:row.imageFileKeys.length>0});
}
print(JSON.stringify(output));
"""
    mongo = subprocess.run(BASE + ['order-system-cloud-mongo-1', 'mongosh', '--quiet', '--nodb', '--file', '/dev/stdin'],
                           input=script.encode(), capture_output=True, timeout=30)
    if mongo.returncode:
        raise RuntimeError('Mongo图片引用预检失败，未迁移；凭据和内容不输出')
    references = {row['id']: row['hasImages'] for row in json.loads(mongo.stdout.decode().strip().splitlines()[-1])}
    published = sql('SELECT comment_id FROM comments WHERE status=1 ORDER BY id;').splitlines()
    assert all(re.fullmatch(r'[a-f0-9-]{36}', key) and key in references for key in published), '已发布评价存在缺失正文，不能当作无图回填'
    assert sql("SELECT COUNT(*) FROM window_reviews WHERE JSON_TYPE(image_file_keys)<>'ARRAY';").strip() == '0', '店铺图片引用不是数组'
    print(f'预检通过：{len(published)}条菜品评价已找到Mongo图片引用。')
    if not args.apply:
        print('只读检查结束，未修改数据库。')
        return
    backup = Path.home() / '.codex/backups/order-system-cloud' / (datetime.now().strftime('%Y%m%d-%H%M%S') + '-review-images')
    backup.mkdir(parents=True, exist_ok=False)
    dump = subprocess.run(BASE + ['order-system-cloud-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces comment_db comments window_reviews'], capture_output=True, timeout=45)
    assert dump.returncode == 0 and b'CREATE TABLE' in dump.stdout, '备份失败，未迁移'
    (backup / 'reviews.sql').write_bytes(dump.stdout)
    (backup / 'mongo-image-flags.json').write_text(json.dumps(references), encoding='utf-8')
    check = "SELECT 'comments',COUNT(*),COALESCE(SUM(score),0),COALESCE(MAX(id),0) FROM comments UNION ALL SELECT 'window_reviews',COUNT(*),COALESCE(SUM(score),0),COALESCE(MAX(id),0) FROM window_reviews;"
    before = sql(check)
    migration = (ROOT / 'docs/database/migrations/comment_db/V4__review_image_filter.sql').read_text(encoding='utf-8')
    sql(migration)
    # 只回填未知字段；不覆盖已有标记，防止掩盖新旧数据源不一致。
    updates = ['START TRANSACTION;'] + [f"UPDATE comments SET has_images={int(references[key])} WHERE comment_id='{key}' AND has_images IS NULL;" for key in published] + ['COMMIT;']
    sql('\n'.join(updates))
    actual = dict(line.split('\t') for line in sql('SELECT comment_id,has_images FROM comments WHERE status=1;').splitlines())
    assert all(actual.get(key) == str(int(references[key])) for key in published), '有图标记与Mongo不一致'
    assert sql('SELECT COUNT(*) FROM comments WHERE status=1 AND has_images IS NULL;').strip() == '0', '存在并发旧代码新增记录，请在加载新代码后再次回填'
    assert before == sql(check), '评价数量或评分在迁移期间变化，请核对并发写入'
    sql(migration)  # 幂等DDL不能重复增加列或索引。
    indexes = sql("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='comment_db' AND index_name IN ('idx_comment_product_images','idx_comment_user_images','idx_window_review_window_images','idx_window_review_user_images');").strip()
    assert indexes == '4'
    (backup / 'verification.json').write_text(json.dumps({'script': 'V4__review_image_filter.sql', 'comments': len(published), 'unknown': 0, 'indexes': 4, 'repeated': True}, indent=2), encoding='utf-8')
    print('有图迁移及Mongo对照核验通过；4个索引已建立，重复DDL通过，原评价数量/评分不变。')
    print('备份目录：' + str(backup))


if __name__ == '__main__':
    main()
