# 订单十五分钟自动取消

## 当前交付状态

2026-11-04：后端、学生端源码与迁移脚本已补齐，三库迁移已执行，Broker第19级与两个Topic已生效，默认profile已切换为order-timeout。用户端已同步Nginx（HTTP200）。订单和支付服务原本未运行，为安全迁移停止了旧用户服务，未自动启动业务服务。未运行Maven；实际消费者与端到端业务由用户启动后验证。

## 确认后的范围

采用Outbox可靠投递、RocketMQ延迟消息、消费重试和死信人工补偿。不增加订单定时扫描。唯一新增定时任务只读 order_timeout_outbox 待发送事件，每次最多50条；不会全量查询 orders。

## 订单与消息

新订单在创建时固定 expires_at=created_at+15分钟，时区UTC+8。订单、明细、超时事件在同一本地事务内保存。客户端与支付内部视图返回 paymentExpiresAt/serverTime 毫秒时间戳；页面只做展示，服务端决定是否可支付。

消息结构为 eventId=order-timeout-v1:订单ID、orderId、expiresAt。Topic与消费组均为 order-payment-timeout-v1。同步发送得到确认后标记Outbox已投递；发送失败保留并退避重试。发送后未标记成功就崩溃可能产生重复消息，不能声称恰好一次。

Broker保留原18个延迟等级，并追加第19级十五分钟。发送时按“截止时间减当前时间”选取不超过剩余时间的最大等级；不足1秒使用1秒，过期事件立即发送。晚发消息不会重新等完整十五分钟。消费者从数据库核对真实剩余时间，若未到期，则同步投递下一条延迟消息后再确认当前消息；若投递失败，当前消息交Broker重试。

到期消费者调用统一 OrderTimeoutService，经Spring代理进入 OrderService.cancelExpiredOrder 的Seata事务（与顾客取消共用同一内部编排）。终态订单直接幂等结束；待支付订单必须先安全关闭支付才可取消与回补库存。自动回补的操作者为空，备注标明系统超时取消，不伪记为顾客手动操作。库存业务引用仍为原订单号，通知仍经原取消通知TCC确认后生成。

## 支付竞争与结算屏障

payment_db.order_payment_gate 使用订单唯一键与本地行锁，让创建/复用支付单和关闭意图互斥。行锁内只操作支付库，不持锁远程扣款或回调订单。关闭意图持久保存，关单失败不会自动重新开放支付入口；用户可重试取消，超时消息亦会重试。

user_db.balance_settlement_guard 与余额和流水同库事务、同一业务引用行锁：
- 扣款先提交：关闭请求读到既有流水返回PAID，取消路径补推进订单，不能回补库存。
- 关闭先提交：写入CLOSED，晚到扣款请求拒绝，不修改余额。
- 请求超时：按结算屏障/流水核对，不能用暂时没查到流水推断不会再扣款。

关单会核对所有支付尝试。余额扣款已成功、支付单仍CREATED时补记支付成功；重复支付请求发现已成功支付单时只补推进订单，不生成新支付单。无法确认、终态与扣款事实冲突、尚未支持关单的渠道均报错等待重试或人工核查，不能当作未支付。

顾客手动取消也接入同一支付关闭流程。十五分钟内已获准进入的支付请求，与到期关闭按用户库结算屏障的先后决定结果；不能把一个已扣款请求强制当失败。当前MOCK无真实外部扣款，关闭屏障阻断迟到成功回调；真实收款渠道必须另接渠道关单/查询及必要退款协议。

## 分层与主要代码

- 订单业务：service/OrderTimeoutService、service/impl/OrderTimeoutServiceImpl；复用原OrderService取消与回补，不另写更新状态SQL。
- 本地事件：service/OrderTimeoutOutboxService → mapper/OrderTimeoutOutboxMapper → model/OrderTimeoutOutboxDO。
- MQ：messaging/OrderTimeoutMessaging，仅负责适配、可靠投递和调用业务服务。
- 支付屏障：payment-service的PaymentOrderGateService、PaymentCloseInternalController与PaymentServiceImpl。
- 用户结算屏障：user-service的UserBalanceServiceImpl、BalanceSettlementGuardMapper；跨服务契约位于user-api。
- 学生端：api/order.uts、pages/order/detail.uvue，无商家或管理功能。

新增内部接口（均要求X-Internal-Token，网关不公开）：
- POST /internal/payments/orders/{orderId}/close：无请求体，返回PAID/CLOSED，异常不能当CLOSED。
- POST /internal/users/{userId}/balance/close：请求bizType=PAYMENT、bizRef=支付单号、amount=应付金额；返回PAID/CLOSED。

## 迁移与启用顺序

1. 备份，核对历史订单、成功支付单及余额流水；确认没有旧扣款或建单请求继续运行。不能混跑不识别关闭屏障的旧代码后直接开启自动取消。
2. 按版本管理执行 user_db/V8__balance_settlement_guard.sql、payment_db/V2__order_payment_gate.sql、order_db/V5__order_payment_timeout.sql。V5列变更是单次迁移，不要盲目重复执行。
3. V5只补历史截止时间及待支付订单Outbox，不直接改订单状态。历史已经超时的待支付订单，在启用发布后会进入同一安全关单流程；历史数据量大时分批回填，不直接在生产运行大事务脚本。
4. 用户、支付、订单服务加载全部新代码；同步更新user-api调用方。先验证扣款/关闭及取消，不提前激活自动消息处理。
5. 使docker/rocketmq/broker.conf新延迟等级生效，并通过现有rocketmq-init创建 order-payment-timeout-v1 和 %DLQ%order-payment-timeout-v1。只改文件不会让运行中的Broker自动加载配置；没有验证第19级生效前不得启用发布。
6. 订单服务选择 order-timeout profile组，自动包含 ranking-mq 与 order-timeout-mq。本次迁移完成后默认已切换为order-timeout；若IDE显式设置了其他active profile，需自行选择该组，避免覆盖默认。不要只选择子profile而丢失已有MQ配置。
7. 实际验证投递、重试/死信、Seata回滚与库存幂等后，再发布本轮Nginx前端产物。这里只记录启用前置，没有执行构建或重启后端。

## 死信与人工补偿

消费者异常不吞掉，Broker最多重试16次后进入该组死信Topic。死信观察消费者把事件记录的 dead_letter_at 写入Outbox，并输出 ERROR 标记 ORDER_TIMEOUT_DEAD_LETTER；发送失败也输出 ORDER_TIMEOUT_PUBLISH_RETRY。这里只提供持久记录和错误日志，没有新增外部告警平台或自动处理订单的扫描器。

人工先查看订单状态、支付单、结算屏障及库存回补事实，修复依赖故障后，按明确选定的 event_id 将对应Outbox的 published_at/dead_letter_at 清空并将 next_attempt_at 设为当前时间，发布器会重发原事件。只重放消息，不手工UPDATE订单状态或库存数量。已支付/已取消订单重复事件会幂等结束。

可用查询：
```sql
SELECT event_id,order_id,attempts,last_error,dead_letter_at
FROM order_timeout_outbox
WHERE dead_letter_at IS NOT NULL OR (published_at IS NULL AND attempts>0)
ORDER BY next_attempt_at LIMIT 100;
```
没有定时订单扫描意味着死信、MQ长期停机或依赖故障时，自动取消可能延后；十五分钟是支付期限，不是任何故障下都恰好第900秒回补的承诺。

## 本次迁移与MQ配置执行记录

- user_db/V8、payment_db/V2、order_db/V5均已执行；14笔订单全部正确回填十五分钟截止时间。
- 迁移前后用户18、余额总额80.50、资金流水8、订单14、支付单10均未变化。
- 当前待支付订单0，历史超时Outbox为0，不会因开启功能立即取消旧订单。
- 备份：C:/Users/DELL/.codex/backups/order_system_cloud/order_timeout_20261104_171843/before.sql；仅本机私有备份，不纳入Git。
- 已重启现有Broker以加载配置，并从getBrokerConfig核实末尾第19级15m；显式创建order-payment-timeout-v1与%DLQ%order-payment-timeout-v1，topicRoute均可找到order-system-broker。
- 已将默认profile设为order-timeout，未启动订单/支付/用户服务；实际消费、重试、回补及通知尚未运行验证。

## 已做验证与未验收项

- 学生端倒计时、API时间戳解析、过期/缺失期限禁用支付、终态处理检查通过；既有评价与账户恢复回归检查通过。
- Vue模板与原生CSS约束检查、HBuilderX 5.26 Web构建通过。
- Java相关源码语法解析通过；没有类型检查、Java测试执行或后端构建。
- 随机隔离MySQL测试库验证三份迁移、十五分钟/UTC+8回填及结算锁协议，含关闭先到、已扣款、双连接竞争。测试库已删除，未修改实际余额、流水或订单。
- 尚待真实MQ投递、死信观察、支付并发、库存失败Seata回滚及通知TCC联调；没有将这些项目标为验收通过。
