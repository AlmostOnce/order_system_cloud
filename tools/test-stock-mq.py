"""Static contract checks for stock MQ wiring; deliberately does not invoke Maven."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


assert (ROOT / "stock-service/src/main/java/com/hbue/ordering/stock/messaging/StockEventPublisher.java").exists(), \
    "stock MQ publisher has not been implemented"

service = read("stock-service/src/main/java/com/hbue/ordering/stock/service/impl/StockServiceImpl.java")
writer = read("stock-service/src/main/java/com/hbue/ordering/stock/messaging/StockEventOutboxWriter.java")
publisher = read("stock-service/src/main/java/com/hbue/ordering/stock/messaging/StockEventPublisher.java")
outbox_mapper = read("stock-service/src/main/java/com/hbue/ordering/stock/messaging/StockEventOutboxMapper.java")
consumer = read("stock-service/src/main/java/com/hbue/ordering/stock/messaging/StockEventConsumer.java")
consumer_mapper = read("stock-service/src/main/java/com/hbue/ordering/stock/messaging/StockEventConsumerMapper.java")
config = read("stock-service/src/main/resources/application-stock-mq.yml")
migration = read("docs/database/migrations/stock_db/V2__stock_event_outbox.sql")
pom = read("stock-service/pom.xml")
redis = read("stock-service/src/main/java/com/hbue/ordering/stock/support/StockRedisScript.java")
application = read("stock-service/src/main/java/com/hbue/ordering/stock/StockServiceApplication.java")
compose = read("docker-compose.yml")

assert "spring-cloud-starter-stream-rocketmq" in pom
assert '"com.hbue.ordering.stock.messaging"' in application
assert "stock_event_outbox" in migration and "stock_event_consumed" in migration
assert re.search(r"xid\s+VARCHAR\(128\)", migration, re.IGNORECASE)
assert "RootContext.getXID()" in writer
assert service.count("stockEventOutboxWriter.write") >= 2, "deduct and restore must persist events"
assert "undo_log" in outbox_mapper and "published_at IS NULL" in outbox_mapper
assert "NOT EXISTS" in outbox_mapper and "u.xid = o.xid" in outbox_mapper
assert "StreamBridge" in publisher and "stockChanged-out-0" in publisher
assert "stockChanged" in consumer and "invalidateSnapshot" in consumer
assert "stock_event_consumed" in consumer_mapper and "eventId" in consumer
assert consumer.index("countByEventId(event.eventId())") < consumer.index("invalidateSnapshot")
assert consumer.index("invalidateSnapshot") < consumer.index("markConsumed")
assert "stockChanged-in-0" in config and "stock-changed-v1" in config
assert "max-reconsume-times: 16" in config
assert "stock-changed-v1" in compose
assert "public void invalidateSnapshot(Long windowId, Long productId)" in redis
assert service.count("stockFlowMapper.insert(flow)") == 2
assert service.count("stockEventOutboxWriter.write(flow") == 2
assert migration.index("CREATE TABLE IF NOT EXISTS stock_event_outbox") < migration.index(
    "CREATE TABLE IF NOT EXISTS stock_event_consumed"
)

print("库存 MQ 源码契约检查通过（未运行 Maven）")
