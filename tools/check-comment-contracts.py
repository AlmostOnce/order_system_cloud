"""Static cross-file contract audit only; does not build Java or connect to databases/MQ."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
def read(name):
    return (ROOT / name).read_text(encoding='utf-8-sig')

ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
parent = ET.fromstring(read('pom.xml'))
modules = {node.text for node in parent.findall('m:modules/m:module', ns)}
assert {'comment-service', 'comment-api'} <= modules
for file in ['comment-service/pom.xml', 'comment-api/pom.xml', 'product-service/pom.xml']:
    tree = ET.fromstring(read(file))
    assert tree.find('m:parent/m:version', ns).text == '1.0.0-SNAPSHOT'
    for dep in tree.findall('m:dependencies/m:dependency', ns):
        if dep.find('m:groupId', ns).text == 'com.hbue.ordering':
            assert dep.find('m:artifactId', ns).text in modules

service = read('comment-service/src/main/java/com/hbue/ordering/comment/service/CommentService.java')
assert service.index('mongo.insert(') < service.index('writer.commit(row)')
assert 'images.requireImageKey(file,userId,"COMMENT_IMAGE",null)' in service
writer = read('comment-service/src/main/java/com/hbue/ordering/comment/service/CommentMetadataWriter.java')
assert '@Transactional(timeout = 15)' in writer
for table in ['comments(', 'comment_rating_stats(', 'comment_rating_outbox(']:
    assert 'INSERT INTO ' + table in writer
verifier = read('comment-service/src/main/java/com/hbue/ordering/comment/service/PurchaseVerifier.java')
assert 'orders.getOrder(orderId, bearer)' in verifier
assert '"已完成".equals(order.status())' in verifier
assert 'productId.equals(i.productId())' in verifier
controller = read('comment-service/src/main/java/com/hbue/ordering/comment/controller/CommentController.java')
assert 'Long.valueOf(jwt.getSubject())' in controller
assert '"Bearer "+jwt.getTokenValue()' in controller
assert '@RequestHeader("Idempotency-Key")' in controller
assert controller.count('@PreAuthorize("hasRole(\'CUSTOMER\')")') == 2
security = read('comment-service/src/main/java/com/hbue/ordering/comment/config/CommentSecurityConfig.java')
for rule in ['toInternalPrincipal', 'hasKey(blacklistKeyPrefix + jwt.getId())', '.anyRequest().denyAll()']:
    assert rule in security
view = read('comment-service/src/main/java/com/hbue/ordering/comment/vo/CommentVO.java')
assert not any(field in view for field in ['userId', 'orderId', 'requestKey', 'requestHash'])

schema = read('docs/database/migrations/comment_db/V1__create_comments.sql')
for constraint in ['uk_comment_purchase(order_id,product_id)', 'uk_comment_request(user_id,request_key)',
                   'uk_comment_rating_version(product_id,revision)', 'score BETWEEN 1 AND 5']:
    assert constraint in schema
for line in schema.splitlines():
    if re.match(r'\s+\w+\s+(?:BIGINT|CHAR|VARCHAR|TINYINT|JSON|INT|DATETIME)\b', line):
        assert 'COMMENT ' in line, line
cleaner = read('comment-service/src/main/java/com/hbue/ordering/comment/task/CommentOrphanCleaner.java')
assert 'minus(24,ChronoUnit.HOURS)' in cleaner
assert cleaner.index('rows.byId(') < cleaner.index('mongo.remove(')
assert 'catch(RuntimeException exception)' in cleaner
projection = read('product-service/src/main/java/com/hbue/ordering/product/rating/ProductRatingProjection.java')
assert projection.count('VALUES(revision)>revision') == 2
assert 'revision=GREATEST(revision,VALUES(revision))' in projection
for config in ['comment-service/src/main/resources/application-comment-mq.yml',
               'product-service/src/main/resources/application-comment-mq.yml']:
    assert 'destination: product-rating-changed-v1' in read(config)
assert 'product-rating-changed-v1' in read('docker-compose.yml')
assert 'lb://comment-service' in read('docs/nacos/gateway-service.yaml')
assert '127.0.0.1:27017:27017' in read('docker-compose.yml')
assert not re.search(r'\$\{[A-Z_][A-Z0-9_]*:(?![-+?])', read('docker-compose.yml')), 'Compose defaults require :-, not Spring-style :'
print('Static comment contracts passed: module XML, identity boundary, purchase verification, write order, constraints, privacy, compensation and MQ projection wiring.')
