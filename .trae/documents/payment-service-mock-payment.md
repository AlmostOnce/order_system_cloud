# 支付模块设计方案（payment-service 模拟支付）

## 背景

`docs/PROJECT_PROGRESS.md` "当前下一步"第 1 项：设计支付接入及回调幂等方案，支付成功事件将订单从待支付推进为已支付，客户端/管理员不得直接设置支付成功。用户已确认两个决策：**模拟支付渠道**（无商户号，预留策略接口便于未来接真实渠道）+ **独立 payment-service 微服务**（符合"每服务独立库"铁律）。

环境事实：端口 9090-9100 已占用，**payment-service 用 9101**；MySQL/Redis/Nacos 均在 WSL docker.io（Windows 侧无 docker CLI，运维命令走 `wsl --`）。

## 总体架构

```
顾客(JWT CUSTOMER) → Gateway 9090 → payment-service 9101 → payment_db
                                        │ Feign + X-Internal-Token（系统身份）
                                        ▼
                                   order-service 9091 新增 /internal/orders/**
模拟渠道（@Profile("dev")，进程内 /mock/channel/**，仅 9101 直连）
   → 异步 POST /internal/payments/notify（HMAC-SHA256 签名，无 JWT）
   → payment-service 验签 → 金额校验 → 支付单状态机 → Feign 推进订单 待支付→已支付
```

* 订单推进唯一入口 = payment-service 在"回调验签+金额校验+条件更新赢家"之后调用；顾客/管理员侧无"标记已支付"端点（`OrderStatus.canBeAdvancedByAdminTo` 本就禁止 ADMIN 设 PAID）。

* `/internal/**`、`/mock/**` 不在 Gateway 路由白名单，天然不对外暴露。

## 1. 数据库

新文件 `docs/database/migrations/payment_db/V1__create_payments_table.sql`（root 执行建库+建表，另用 root 建账号授权，参照 `backup/db-accounts-fix.sh` 的 GRANT 写法）：

* `payments` 表：`payment_id` PK、`payment_no`（UNIQUE，PAY+时间戳+随机，复用订单号生成模式）、`order_id`/`order_no`/`user_id`/`amount DECIMAL(10,2)`（创建时订单快照）、`paid_amount`、`channel_trade_no`、`channel_code` 默认 MOCK、`status`（CREATED/SUCCESS/FAILED/CLOSED，英文码，无存量包袱）、`fail_reason`、`notify_count`、`last_notify_at`、`success_time`、`created_at`/`updated_at`

* **关键唯一索引**：`active_flag TINYINT GENERATED ALWAYS AS (CASE WHEN status='CREATED' THEN 1 ELSE NULL END) VIRTUAL` + `UNIQUE(order_id, active_flag)` —— MySQL 8 虚拟生成列唯一索引，数据库级保证"同一订单只有一个进行中支付单"，FAILED 后可重建

* 不建 notify\_log 表（`notify_count`/`last_notify_at` 覆盖重放观察，宁简勿繁）；不做超时关单/退款/对账（见取舍）

账号：`payment_service` / `payment_password_dev_2026`，`GRANT ALL ON payment_db.*`；`.env` 加 `PAYMENT_DB_USERNAME/PAYMENT_DB_PASSWORD/PAYMENT_CHANNEL_SECRET=mock_channel_secret_dev_2026/INTERNAL_ORDER_TOKEN=internal_order_token_dev_2026`（.env.example 同步）。

## 2. API 设计

**顾客侧**（JWT+CUSTOMER，Gateway 路由 `/api/payments/**`，StripPrefix=1）：

| 方法   | 路径                          | 说明                                                                                     |
| ---- | --------------------------- | -------------------------------------------------------------------------------------- |
| POST | `/payments`                 | body `{orderId}`，创建支付单；金额取订单快照绝不取客户端；已有 CREATED 单幂等返回；归属不符按 404 防泄露（沿用 cancelOrder 模式） |
| GET  | `/payments/{paymentId}`     | 查支付状态（校验归属）                                                                            |
| GET  | `/payments/order/{orderId}` | 按订单查最新支付单（前端轮询用）                                                                       |

**渠道回调**（无 JWT，验签）：`POST /internal/payments/notify`，Header `X-Channel-Signature`(HMAC-SHA256 hex) + `X-Channel-Timestamp`；body 含 `paymentNo/channelTradeNo/paidAmount(字符串!)/status/timestamp`。

**服务间** payment→order（共享密钥，非 JWT——回调线程无用户身份，且现有 JwtDecoder 耦合 Redis 黑名单；Gateway 不路由 /internal 即不暴露）：

* `GET /internal/orders/{orderId}` → OrderInternalVO（orderId/orderNo/userId/totalAmount/status label），创建支付单时校验归属与待支付状态

* `PATCH /internal/orders/{orderId}/paid` → 原子推进

* 鉴权：order-service 新增 `InternalTokenVerifier`（`MessageDigest.isEqual` 常量时间比较），SecurityConfig 对 `/internal/orders/**` permitAll

* 与 product 先例的差异写进 Javadoc：/internal/products/quote 是"代表用户"的调用（透传 JWT）；/internal/orders 是"系统事件"调用（共享密钥）——两种模式并存是刻意设计

## 3. 幂等与一致性（核心）

`PaymentServiceImpl.handleNotify`（@Transactional）：

1. 验签+timestamp 窗口(±300s) → 失败 log.warn 返回 401，状态不动
2. 按 paymentNo 查单 → 不存在返回 404
3. 无条件 `notify_count+1, last_notify_at=NOW()`（重放可观察）
4. 金额校验：`paidAmount.compareTo(payment.amount) != 0` → 返回 CONFLICT，状态不动
5. **幂等核心**：`UPDATE payments SET status='SUCCESS',paid_amount=?,... WHERE payment_no=? AND status='CREATED'` → 行数=1 是赢家；=0 回读：已 SUCCESS 的重复通知幂等返回成功**且不调 order**；FAILED/CLOSED 收到 SUCCESS（迟到乱序）记日志返回成功不调 order
6. 仅赢家：Feign `PATCH /internal/orders/{orderId}/paid`；order 返回冲突（订单已取消）→ 支付单保持 SUCCESS + log.error"不自动退款需人工"（支付事实必须如实记录）

**双重防护**：payments 条件更新（第一重）+ order 侧复用 `OrderMapper.updateStatusIfVersionMatches` 乐观锁（第二重）。FAILED 回调：CREATED→FAILED+fail\_reason，不调 order，用户可重新发起（active\_flag 不阻塞）。

**签名**（`NotifySignatureUtils`，Mock 生成端与验签端共用）：规范化串 `paymentNo\nchannelTradeNo\npaidAmount原串\nstatus\ntimestamp`（\n 分隔防拼接歧义），HMAC-SHA256 hex；验签=时间戳窗口+重算+常量时间比较。

**模拟渠道**（`@Profile("dev")`，进程内，不用 Feign 不走发现——第三方本就在体系外，教学点）：

* `GET /mock/channel/checkout/{paymentNo}` 模拟收银台

* `POST /mock/channel/pay/{paymentNo}` "我支付成功了"按钮，body 可选 `{outcome, paidAmount:"0.01"}`（演练金额不符）

* `MockChannelNotifier`：JDK HttpClient + 虚拟线程异步 POST 回调，重试 3 次（非 2xx 或 code!=COMMON\_000），默认手动触发，`payment.channel.mock.auto-confirm-enabled`（默认 false）可开自动 2 秒延迟回调

**PaymentChannel 策略接口**：`channelCode()/createPayment()/queryStatus()/verifyNotify()/parseNotify()`，`MockPaymentChannel` 实现；未来接支付宝只加实现类。

**不建 payment-api 契约模块**：order 不调 payment（订单详情不带支付信息，前端直查），单消费方 Feign 直接声明在 payment-service 内。

## 4. 文件清单

**新建 payment-service**（包根 `com.hbue.ordering.payment`，模板=product-service）：

* pom.xml（对照 product-service + openfeign + loadbalancer，Surefire 3.5.4）

* `PaymentServiceApplication`（scanBasePackages="com.hbue.ordering" + @MapperScan + @EnableFeignClients）

* controller：`PaymentController`（顾客三接口）、`PaymentNotifyController`、`MockChannelController`(@Profile("dev"))

* service：`PaymentService`/`PaymentServiceImpl`（核心：状态机+幂等+编排）

* channel：`PaymentChannel`/`MockPaymentChannel`/`MockChannelNotifier`/`ChannelPaymentCreation`/`ChannelNotifyMessage`

* config：`PaymentSecurityConfig`（照抄 ProductSecurityConfig，notify 与 /mock/\*\* 放行）、`PaymentChannelProperties`、`PaymentFeignConfig`（RequestInterceptor 追加 X-Internal-Token）

* feign：`OrderInternalClient`

* enums：`PaymentStatus`（CREATED/SUCCESS/FAILED/CLOSED + canTransitionTo，仿 OrderStatus 风格）

* mapper：`PaymentMapper`（touchNotify/markResultIfCreated/selectActiveByOrderId/selectLatestByOrderId）

* model：`PaymentDO`；dto：`PaymentCreateRequest`/`ChannelNotifyRequest`/`OrderInternalDTO`；vo：`PaymentCreateVO`(含 checkoutUrl/qrCodeContent)/`PaymentStatusVO`

* util：`NotifySignatureUtils`

* application.yml：port 9101、`PAYMENT_DB_*` 环境变量（默认 127.0.0.1:9095/payment\_db）、redis/nacos/jwt 同 product-service、`payment.channel.*`、`payment.internal.order-token`

**修改既有文件**：

* 根 `pom.xml`：`<modules>` 加 payment-service

* order-service：新增 `OrderInternalController`/`security/InternalTokenVerifier`/`vo/OrderInternalVO`；修改 `OrderSecurityConfig`（放行 /internal/orders/\*\*）、`OrderService`/`OrderServiceImpl`（新增 `markOrderPaid`：待支付→已支付，复用 `persistStatusTransition` 乐观锁路径；已 PAID 幂等返回成功；application.yml 加 INTERNAL\_ORDER\_TOKEN 读取）

* `.env`/`.env.example`：4 个新变量

* `docs/nacos/gateway-service.yaml`：追加 payment 路由段（Path=/api/payments/\*\* → lb://payment-service）

* `docs/PROJECT_PROGRESS.md`：追加完成记录

## 5. 测试计划（纯 Mockito，风格=ProductQuoteServiceImplTest，不连真库）

* `PaymentServiceImplTest`（约 14 例）：创建成功/订单不存在404/归属不符404/非待支付409/已有CREATED幂等/DuplicateKeyException回查赢家/验签失败401/金额不符409/超时间窗/单不存在404/首次成功恰好调order一次/重复通知不调order/迟到回调吞冲突/FAILED不调order/Feign异常不回滚本地

* `PaymentStatusTest`、`NotifySignatureUtilsTest`（正确/篡改/超窗）、`MockPaymentChannelTest`、`MockChannelNotifierTest`（同步 executor 验证重试 3 次）

* order-service：`markOrderPaid` 3 例 + `InternalTokenVerifierTest`

## 6. 实施步骤（每步可独立验证）

1. 迁移 SQL + 账号 + .env → `wsl -- docker exec -i order-system-cloud-mysql-1 mysql -uroot -p... < V1...sql`，查表结构/账号
2. 根 pom + 模块骨架（pom/启动类/yml/SecurityConfig 骨架）→ 启动注册进 Nacos、/actuator/health 200
3. 领域层（枚举/DO/Mapper/签名工具）+ 单测 → `mvn -pl payment-service test`
4. order-service 内部接口 + 单测 → `mvn -pl order-service test`；curl 直连 9091 验 token
5. 策略层（Channel/Mock/Notifier）+ 单测
6. PaymentServiceImpl 核心流程 + 全量单测
7. Controller 三件套 + Feign + 配置 → 直连 9101 走通"创建→mock确认→查询"闭环
8. Nacos 路由发布（可用 backup/nacos-init.sh 同款 v3 API 脚本方式发布，或控制台 <http://127.0.0.1:9098> 手工）→ 经 Gateway 端到端：登录→创建支付→mock 确认→查支付 SUCCESS→查订单"已支付"
9. 异常路径手工回归：错误签名/金额不符/重放3次(notify\_count=3)/订单取消后回调/FAILED重建
10. 更新 PROJECT\_PROGRESS.md；全量 `mvn test` 回归

## 7. 取舍声明（写入 Javadoc 与进度文档）

1. 不做超时关单（CLOSED 预留）；2. 不做自动退款（订单已取消收到回调仅日志+人工）；3. 不做本地消息表/MQ 最终一致性（最坏结果=支付成功但订单短暂未推进，可人工修复——这正是后续 Spring Cloud Stream 课题的引入点）；4. 内网共享密钥明文，生产需 HTTPS/mTLS。

## 验证方式

* 每步 `mvn -pl <模块> test`；最后全量 `mvn test`（当前基线 86 项全过，预计新增约 22 项）

* 端到端手工验证链路见实施步骤 8/9（需要 IDEA 启动 order-service + payment-service + Gateway，容器基础设施已就绪）

