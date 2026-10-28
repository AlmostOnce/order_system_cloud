"""Static dependency/config audit only; does not run Maven or start an application."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ['user-service', 'product-service', 'order-service', 'payment-service',
            'stock-service', 'file-service', 'comment-service']
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
root = ET.parse(ROOT / 'pom.xml').getroot()
version = root.find('m:properties/m:druid.version', NS)
assert version is not None and version.text == '1.2.27', 'Missing centrally managed Druid version'
managed = root.findall('m:dependencyManagement/m:dependencies/m:dependency', NS)
assert any(d.findtext('m:artifactId', namespaces=NS) == 'druid-spring-boot-3-starter'
           and d.findtext('m:version', namespaces=NS) == '${druid.version}' for d in managed)

for service in SERVICES:
    pom = ET.parse(ROOT / service / 'pom.xml').getroot()
    dependencies = pom.findall('m:dependencies/m:dependency', NS)
    assert sum(d.findtext('m:artifactId', namespaces=NS) == 'druid-spring-boot-3-starter' for d in dependencies) == 1, service
    jdbc = [d for d in dependencies if d.findtext('m:artifactId', namespaces=NS)
            in ('mybatis-plus-spring-boot3-starter', 'spring-boot-starter-jdbc')]
    assert jdbc, service
    for dependency in jdbc:
        assert any(e.findtext('m:groupId', namespaces=NS) == 'com.zaxxer'
                   and e.findtext('m:artifactId', namespaces=NS) == 'HikariCP'
                   for e in dependency.findall('m:exclusions/m:exclusion', NS)), service
    source = (ROOT / service / 'src/main/resources/application.yml').read_text(encoding='utf-8')
    assert '    type: com.alibaba.druid.pool.DruidDataSource' in source, service
    assert '    hikari:' not in source, service
    assert '    druid:' in source and '      max-wait: 3000' in source, service
    assert '      max-active: ' + ('5' if service == 'comment-service' else '10') + '\n' in source, service
    for name in ('stat-view-servlet', 'web-stat-filter'):
        assert re.search(r'^      ' + name + r':\n        enabled: false$', source, re.M), service

print('Druid static contracts passed: 7 services, Boot 3 starter, managed version, Hikari exclusions and pool settings.')
