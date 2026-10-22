# 菜品累计销量榜：RocketMQ + Redis ZSet

## 当前状态

2026-10-22 已完成消息化源码和配置，order_db 的 sales_event_outbox 已创建。按用户本次授权，未运行 Maven、后端 JUnit、构建或服务启停，未进行 Java Producer/Broker/Consumer 端到端联调。不能将 Lua/SQL 测试通过等同于 MQ 已在运行。

## 启动故障修复

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
2. 基础设施：docker-compose 中 `mq` profile 包含 RocketMQ 5.3.2 NameServer、Broker 与 topic 初始化任务；本机端口 9876、10911、10909、10912，数据和日志使用独立卷。主题 order-paid-sales-v1，4 个读/写队列。未在本次启动这些容器。
3. 服务：在 IDEA 的 Active profiles 激活 `ranking-mq`，Nameserver 可通过 ROCKETMQ_NAMESERVER 覆盖（默认 127.0.0.1:9876）；已提供 application-ranking-mq.yml。未激活该 profile 时不连接 Broker，仍可靠保存待发送 outbox，榜单保留初始化和 5 分钟对账；不能宣称此时已启用消息更新。
4. Broker 的 brokerIP1=127.0.0.1 面向当前 Windows 宿主机服务。容器化服务需改成容器可达地址，不能照搬回环地址。
5. 已核对现有 SCA BOM 2025.0.0.0 管理的 spring-cloud-starter-stream-rocketmq；Producer 的 send-type=Sync，Producer error-channel-enabled=false 使发送错误传播；Consumer 的 push.max-reconsume-times=16，使用固定消费组与自定义抛异常错误处理器。

配置依据：[SCA 2025.0.0.0 Producer 配置源码](https://github.com/alibaba/spring-cloud-alibaba/blob/2025.0.0.0/spring-cloud-alibaba-starters/spring-cloud-starter-stream-rocketmq/src/main/java/com/alibaba/cloud/stream/binder/rocketmq/properties/RocketMQProducerProperties.java)、[Consumer 配置源码](https://github.com/alibaba/spring-cloud-alibaba/blob/2025.0.0.0/spring-cloud-alibaba-starters/spring-cloud-starter-stream-rocketmq/src/main/java/com/alibaba/cloud/stream/binder/rocketmq/properties/RocketMQConsumerProperties.java)、[Spring Cloud Stream 错误处理](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/overview-error-handling.html)、[RocketMQ Docker 文档](https://rocketmq.apache.org/docs/quickStart/02quickstartWithDocker/)。

## 验证

- tools/test-sales-ranking.py：实际 MySQL 临时数据和隔离 Redis keys，覆盖有效订单口径、增量、重复投递、历史重建去重、旧重建 fencing、名称乱序、精确整数上限、Redis 丢失和空榜，测试后清理专用 keys。
- 前端 scripts/test-sales-ranking.cjs 以及既有订单/登录/图片/刷新测试。
- 新增订单本地事务、消息发送确认/失败重试、消费失败传播、Redisson Bean 装配测试源码；未运行 Maven，因此这些 Java 测试与新依赖解析仍未验收。
- Docker compose 配置静态校验通过；RocketMQ 容器尚未启动，服务未重启，不宣称消息端到端已通过。
