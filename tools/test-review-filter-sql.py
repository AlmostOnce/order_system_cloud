"""在连接内临时表执行真实Mapper SQL，验证两类评价分页、游标范围和全量概览。"""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
statements = ['SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;']
expected = {}


def literal(value):
    """测试值均由本文件提供；SQL参数逐个绑定，不拼接外部输入。"""
    return str(value) if isinstance(value, int) else "'" + value.replace("'", "''") + "'"


def render(node, values):
    """解释Mapper实际使用的choose/if；不另外实现一套业务SQL。"""
    if node.tag in ('if', 'when') and values[node.attrib['test'].split()[0]] is None:
        return ''
    if node.tag == 'choose':
        selected = next((child for child in node if child.tag == 'when' and values[child.attrib['test'].split()[0]] is not None), None)
        if selected is None:
            selected = next(child for child in node if child.tag == 'otherwise')
        return render(selected, values)
    text = node.text or ''
    for child in node:
        text += render(child, values) + (child.tail or '')
    return re.sub(r'#\{(\w+)\}', lambda match: literal(values[match[1]]), text)


def check(name, sql, rows):
    statements.extend(["SELECT 'CASE:" + name + "';", sql.rstrip(';') + ';'])
    expected[name] = rows


for mapper, table, target, cursor, size, public_id in [
    ('CommentMapper', 'comments', 'product', 'before', 'size', 'comment_id'),
    ('WindowReviewMapper', 'window_reviews', 'window', 'cursor', 'limit', 'review_id'),
]:
    text = (ROOT / 'comment-service/src/main/java/com/hbue/ordering/comment/mapper' / (mapper + '.java')).read_text(encoding='utf-8')
    script = next(value for value in re.findall(r'@Select\("""(.*?)"""\)', text, re.S) if '<script>' in value)
    temp = 'codex_filter_' + table
    lookup = temp + '_cursor'
    statements.append(f'CREATE TEMPORARY TABLE {temp} LIKE {table};')
    # 5/1之外保留3星及其他对象/用户样本，验证分类不会错误扩张或泄露别人的游标范围。
    fixtures = [(1, 11, 7, 5), (2, 11, 7, 1), (3, 12, 7, 3), (4, 11, 8, 5), (5, 11, 7, 5), (6, 11, 7, 1), (7, 12, 7, 5)]
    for key, user, subject, score in fixtures:
        uid = f'00000000-0000-4000-8000-{key:012d}'
        if table == 'comments':
            columns = 'id,comment_id,user_id,author_name,order_id,product_id,window_id,score,request_key,request_hash,created_at,has_images'
            values = f"{key},'{uid}',{user},'同学',{100+key},{subject},2,{score},'{uid}',REPEAT('a',64),NOW(),{int(key in (1,3,6))}"
        else:
            image_json = "JSON_ARRAY(REPEAT('a',32))" if key in (1,3,6) else 'JSON_ARRAY()'
            columns = 'id,review_id,user_id,author_name,order_id,window_id,score,content,image_file_keys,tags,request_key,request_hash,created_at'
            values = f"{key},'{uid}',{user},'同学',{100+key},{subject},{score},'',{image_json},JSON_ARRAY(),'{uid}',REPEAT('a',64),NOW()"
        statements.append(f'INSERT INTO {temp}({columns}) VALUES({values});')
    # MySQL不允许同一临时表在子查询中二次打开；同一份样本复制给游标子查询。
    statements.extend([f'CREATE TEMPORARY TABLE {lookup} LIKE {temp};', f'INSERT INTO {lookup}({columns}) SELECT {columns} FROM {temp};'])

    def page(score=None, before=None, user=None, subject=7, count=3, has_images=None):
        values = dict(user=user, score=score, hasImages=has_images, **{target: subject, cursor: before, size: count})
        sql = render(ET.fromstring(script.strip()), values).strip()
        sql = re.sub(r'^SELECT .*?FROM', 'SELECT id FROM', sql, count=1, flags=re.S)
        return sql.replace('FROM ' + table, 'FROM ' + temp, 1).replace('FROM ' + table, 'FROM ' + lookup)

    uuid = lambda key: f'00000000-0000-4000-8000-{key:012d}'
    check(mapper + '_all', page(count=20), ['7', '6', '5', '3', '2', '1'])
    check(mapper + '_positive', page(5), ['7', '5', '1'])
    check(mapper + '_negative', page(1), ['6', '2'])
    check(mapper + '_cursor', page(5, uuid(7)), ['5', '1'])
    check(mapper + '_wrong_score_cursor', page(5, uuid(6)), [])
    check(mapper + '_wrong_target_cursor', page(5, uuid(4)), [])
    check(mapper + '_mine', page(5, user=11), ['5', '4', '1'])
    check(mapper + '_wrong_user_cursor', page(5, uuid(7), user=11), [])
    check(mapper + '_photos', page(has_images=True), ['6', '3', '1'])
    check(mapper + '_photos_positive', page(5, has_images=True), ['1'])
    check(mapper + '_wrong_photo_cursor', page(before=uuid(5), has_images=True), [])
    check(mapper + '_photo_cursor', page(before=uuid(6), has_images=True), ['3', '1'])
    check(mapper + '_bound', page(5, count=2), ['7', '5'])
    annotation = text[text.rfind('@Select', 0, text.index('reviewSummary(')):text.index('reviewSummary(')]
    sql = ''.join(re.findall(r'"([^"]*)"', annotation)).replace('#{id}', '7').replace('FROM ' + table, 'FROM ' + temp)
    check(mapper + '_summary', sql, ['6\t3.3333\t3\t2\t3'])
    check(mapper + '_empty_summary', sql.replace('=7', '=999'), ['0\tNULL\t0\t0\t0'])
    if table == 'comments':
        statements.append(f'UPDATE {temp} SET has_images=NULL WHERE id=5;')
        check(mapper + '_unknown_summary', sql, ['6\t3.3333\t3\t2\tNULL'])
        annotation = text[text.rfind('@Select', 0, text.index('hasUnknownImages(')):text.index('hasUnknownImages(')]
        unknown = ''.join(re.findall(r'"([^"\n]*)"', annotation))
        unknown = render(ET.fromstring(unknown), dict(user=None, product=7)).replace('FROM comments', 'FROM ' + temp)
        check(mapper + '_unknown_image_guard', unknown, ['1'])
        statements.append(f'UPDATE {temp} SET has_images=0 WHERE id=5;')
        check(mapper + '_known_image_guard', unknown, ['0'])
        # 直接执行写Mapper的INSERT，确认has_images确实写入，而非只在Java内存标记。
        writer = (ROOT / 'comment-service/src/main/java/com/hbue/ordering/comment/mapper/CommentWriteMapper.java').read_text(encoding='utf-8')
        insert = re.findall(r'@Insert\("""(.*?)"""\)', writer, re.S)[0]
        values = dict(commentId='00000000-0000-4000-8000-000000000008', userId=11, authorName='同学', orderId=108, productId=7, windowId=2, score=5, requestKey='00000000-0000-4000-8000-000000000008', requestHash='a'*64, createdAt='2026-11-06 14:00:00', hasImages=1)
        insert = re.sub(r'#\{row\.(\w+)\}', lambda match: literal(values[match[1]]), insert).replace('INTO comments', 'INTO ' + temp)
        statements.append(insert + ';')
        check(mapper + '_image_projection_write', f"SELECT has_images FROM {temp} WHERE comment_id='{values['commentId']}'", ['1'])



command = ['wsl.exe', '--exec', 'docker', 'exec', '-i', 'order-system-cloud-mysql-1', 'sh', '-c',
           'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 comment_db']
run = subprocess.run(command, input='\n'.join(statements).encode('utf-8'), capture_output=True, timeout=30)
if run.returncode:
    raise RuntimeError(run.stderr.decode('utf-8', errors='replace'))
actual = {}
current = None
for line in run.stdout.decode('utf-8').splitlines():
    if line.startswith('CASE:'):
        current = line[5:]
        actual[current] = []
    elif current is not None:
        actual[current].append(line)
for name, rows in expected.items():
    assert actual[name] == rows, (name, actual[name], rows)
print(f'SQL检查通过：{len(expected)}项真实Mapper查询，覆盖全量/一星/五星、游标及用户隔离、分页上限和完整统计；业务数据未改。')
