# 菜品累计销量榜：RocketMQ + Redis ZSet

## 当前状态

2026-10-23 23:57 跟进：本轮为验证而启动的订单服务实例已正常停止，释放 9091 供 IDEA 启动；下面的运行记录表示此前完成的启动核验，不表示该临时实例仍在运行。停止时同样出现 Nacos `InterruptedException`，这是关闭阶段的线程中断日志。

2026-10-23 本机 Linux 执行记录：已完成数据库备份与迁移核对、RocketMQ 容器启动及订单服务启动。`order_db.sales_event_outbox` 已存在，当前 outbox 为 0 条、`orders` 为 0 条；本次没有创建订单、支付或扣款。RocketMQ 5.3.2 NameServer、Broker 已运行，初始化任务成功创建 `order-paid-sales-v1`（4 个读/写队列）；`sales-ranking-v1` 消费组已连接并订阅该主题。`order-service` 以 `ranking-mq` profile 启动在 9091，已注册 Nacos，`/actuator/health` 返回 `UP`。但启动日志仍有 Spring Cloud Function 找不到 `salesRankingPaid` / `salesRankingRetryError` 的告警，且当前没有可消费的订单事件；所以只确认服务连接与订阅成功，**不能宣称 Producer → Broker → Consumer 的业务消息已端到端通过**。下一步先排查函数注册告警，再用真实或明确授权的测试订单验收发布、消费、重试和投影。

2026-10-23 另一设备/会话历史验收：当时消费组 `sales-ranking-v1` 在线，3 条既有 outbox 事件全部发布，4 个队列消费积压为 0；Redis 中三个菜品销量为 4、7、10，与当时数据库有效订单汇总一致，已有订单去重集合未重复累计。本机 Linux 当前数据库没有订单记录，这条历史结果不代表本机 Linux 已完成同样验收。

2026-10-22 已完成消息化源码和配置，order_db 的 sales_event_outbox 已创建。按用户本次授权，未运行 Maven、后端 JUnit、构建或服务启停，未进行 Java Producer/Broker/Consumer 端到端联调。不能将 Lua/SQL 测试通过等同于 MQ 已在运行。

## 启动故障修复

Nacos 日志隔离：九个服务的 main 方法在 Spring 启动前设置 JM.LOG.PATH，默认 ${user.home}/logs/order-system/{服务名}，客户端继续在其下的 nacos 目录写日志；已有 JVM -DJM.LOG.PATH 参数优先。避免 Windows 多服务共用 remote.log 导致滚动改名失败。订单服务已用对应 JVM 参数直接运行验证，独立 remote.log/config.log 成功写入，未出现原改名失败告警，MQ 消费者保持在线；其他服务未重启，入口源码待其后续编译启动生效。

2026-10-23 已修复 Docker MQ 卷权限问题：镜像以 UID/GID 3000 运行，而新建命名卷目录属于 root，导致日志不可写、Broker 存储初始化失败。Compose 增加一次性的 rocketmq-permissions 服务，仅调整 MQ 三个专用卷的归属，NameServer/Broker 等待其完成后启动，业务进程仍使用镜像默认用户。主题初始化改为检查 mqadmin 的成功输出，避免该工具打印异常但退出码仍为 0 时误报成功。已实际启动 Broker 并创建 order-paid-sales-v1（4 个读写队列）；这不等于订单 Java 生产消费链路已经完成验收。

订单服务注入 SalesRankingService 后，需要 DistributedLockService，但 common-redis 的 Redisson 为 optional，订单模块没有显式依赖，因此 Spring 找不到 Bean。订单 pom 已按现有商品模块的装配方式补充 redisson-spring-boot-starter，排除 spring-data-41，并显式加入与 Boot 3.5 匹配的 redisson-spring-data-35。新增订单模块上下文回归测试源码；尚未运行。

## 口径和接口

- GET `/api/orders/rankings/sales?limit=50`，需要登录，limit 1–100。
- data：`{updatedAt,items:[{rank,productId,windowId,productName,sales}]}`，前端接口保持兼容，不返回订单或用户信息。
- 按菜品和窗口累计售出份数；计入已支付、制作中、待取货、已完成，排除待支付与已取消。同销量按商品、窗口 ID 倒序稳定排序。
- 名称使用最新一条有效订单明细快照。历史菜品也参与榜单；是否仍可下单由现有菜单/库存核验。

## 消息链路

1. 订单状态 CAS 成功变为已支付时，与 sales_event_outbox INSERT 在同一个本地数据库事务中提交。并发/重放未赢得 CAS 不生成第二条事件；消息表写入失败会回滚订单状态。
2. 定时扫描未确认事件，通过 Spring Cloud Stream 的 StreamBridge 同步发送到 RocketMQ。确认发送成功后标记 published_at；失败按指数退避重试（最多间隔约 256 秒），不把 MQ 网络调用放入支付事务。
3. 事件使用确定性 eventId `order-paid-sales:{orderId}`，含 version 和 orderId，不含用户、密码或完整订单信息。消费者核实格式，并从本服务订单库取已支付订单的明细，避免把消息正文当作任意销量输入。
4. 消费端 Lua 原子执行订单去重、ZINCRBY、名称版本比较及更新时间。重复投递不加分；按 20 位明细 ID 比较，乱序消息不使名称回退。
5. 消费异常向外传播。专用 error-handler-definition 再抛异常，避免默认日志处理后消息被确认；交由 RocketMQ 重试，最多 16 次后进入 `%DLQ%sales-ranking-v1`。死信需运维告警、排查后重投；投影定时对账负责修复遗漏统计。

这是本地事务 outbox + MQ 的至少一次投递，不是 RocketMQ 半消息事务，也不更改 payment × user 的余额事务设计。Broker 短暂不可用不会中断支付；订单数据库/outbox 本身异常仍会使支付通知失败并等待既有回调重试。

## 投影、历史与重建

Redis keys 使用同一 `{sales:v2}` hash tag：data 为 ZSet，meta 为 Hash，seen 为已计入订单 Set，ready 为投影时间，generation 为写入序号，lock 为复用 Redisson 的互斥锁。

- 普通查询直接 ZREVRANGE，不再每 30 秒全量聚合。
- 冷启动、Redis 丢失或每 5 分钟对账时，用单条 SQL 的一致性快照取得所有有效明细，同时重建总量和全部已计入订单 ID。历史订单不需要伪造 MQ 事件。
- 消费与重建使用同一把锁；查询前递增 generation，Lua 拒绝租约过期后旧重建覆盖较新消费。历史快照中已有的订单收到延迟消息也不会重复计数。
- data/meta/seen/ready 不单独设 TTL，避免去重记录先过期导致重复累计；generation 持续递增。只使用同库专用命名空间，不复用或修改 v1 键。Redis 应保留现有 AOF，避免对该投影配置随机逐 key 驱逐；故障恢复应按整套投影重建。
- Redis、SQL 或锁异常只使排行榜查询/消费失败，不影响支付本地提交。Lua 在所有数量边界检查完成前不写增量，防止异常多菜品事件部分记分。
- 当前全量重建适用于校园规模，SQL 限时 15 秒，锁租约 30 秒，generation 提供过期写保护；规模增大后需要分代批量重建与更细的并发方案。

## 部署配置

1. 数据库：迁移 `docs/database/migrations/order_db/V1__add_sales_event_outbox.sql` 已在本机执行，当前新表为空。其他设备须在部署新代码前执行，确保 order_id 为 BIGINT UNSIGNED 与订单表一致。
2. 基础设施：docker-compose 中 `mq` profile 包含 RocketMQ 5.3.2 NameServer、Broker 与 topic 初始化任务；本机端口 9876、10911、10909、10912，数据和日志使用独立卷。主题 `order-paid-sales-v1` 有 4 个读/写队列。本机 NameServer/Broker 已启动，topic 初始化任务退出码为 0；Dashboard 当前未部署。
3. 服务：当前 application.yml 的默认 profile 为 `ranking-mq`；如果 IDEA 或环境变量显式指定其他 profile，需同时包含 `ranking-mq` 才能启用事件收发。Nameserver 可通过 ROCKETMQ_NAMESERVER 覆盖（默认 127.0.0.1:9876）；已提供 application-ranking-mq.yml。未激活该 profile 时不连接 Broker，仍可靠保存待发送 outbox，榜单保留初始化和 5 分钟对账。
4. `brokerIP1=127.0.0.1` 适用于 RocketMQ 客户端与 Broker 位于同一开发宿主机、通过宿主机映射端口通信的模式（本机 Linux 订单服务已成功连接）。跨主机或容器内运行的客户端必须改成其可达的 Broker 地址，不能照搬回环地址。
5. 已核对现有 SCA BOM 2025.0.0.0 管理的 spring-cloud-starter-stream-rocketmq；Producer 的 send-type=Sync，Producer error-channel-enabled=false 使发送错误传播；Consumer 的 push.max-reconsume-times=16，使用固定消费组与自定义抛异常错误处理器。

配置依据：[SCA 2025.0.0.0 Producer 配置源码](https://github.com/alibaba/spring-cloud-alibaba/blob/2025.0.0.0/spring-cloud-alibaba-starters/spring-cloud-starter-stream-rocketmq/src/main/java/com/alibaba/cloud/stream/binder/rocketmq/properties/RocketMQProducerProperties.java)、[Consumer 配置源码](https://github.com/alibaba/spring-cloud-alibaba/blob/2025.0.0.0/spring-cloud-alibaba-starters/spring-cloud-starter-stream-rocketmq/src/main/java/com/alibaba/cloud/stream/binder/rocketmq/properties/RocketMQConsumerProperties.java)、[Spring Cloud Stream 错误处理](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/overview-error-handling.html)、[RocketMQ Docker 文档](https://rocketmq.apache.org/docs/quickStart/02quickstartWithDocker/)。

## 验证

- tools/test-sales-ranking.py：实际 MySQL 临时数据和隔离 Redis keys，覆盖有效订单口径、增量、重复投递、历史重建去重、旧重建 fencing、名称乱序、精确整数上限、Redis 丢失和空榜，测试后清理专用 keys。
- 前端 scripts/test-sales-ranking.cjs 以及既有订单/登录/图片/刷新测试。
- 新增订单本地事务、消息发送确认/失败重试、消费失败传播、Redisson Bean 装配测试源码；未运行 Maven，因此这些 Java 测试与新依赖解析仍未验收。
- 本机 `docker compose --profile mq ps` 已确认 NameServer/Broker 运行，topic 初始化任务成功；`mqadmin consumerConnection` 与 `consumerStatus` 确认 `sales-ranking-v1` 在线并订阅 `order-paid-sales-v1`。
- 本机订单服务构建成功并启动，`curl http://127.0.0.1:9091/actuator/health` 返回 HTTP 200 / `UP`，Nacos 注册成功。启动日志有 Spring Cloud Function 函数发现告警，需要先排查；当前无订单/outbox 事件，Producer 与业务 Consumer 的真实消息处理、重试及死信仍未验证。
- 本机完整数据库备份位于 `/home/user/.codex/backups/order_system_cloud/db-migrations-20261023-DroIPN/user-order-before-migrations.sql.gz`；gzip 完整性校验通过。Maven 生产代码构建使用 `-Dmaven.test.skip=true` 成功；测试没有运行，且普通 `spring-boot:run` 会被已有过期测试源码 `OrderCartContractTest.userId()` 编译错误阻断。
