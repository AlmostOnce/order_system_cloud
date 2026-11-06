# 订单退款与售后

## 业务范围

每笔退款针对一个订单明细及其份数，金额由服务端使用下单实付快照分摊，学生不能填写金额、用户编号或审批结果。一次退多道菜时分别申请，每笔独立处理，避免一道菜的问题影响整单。

| 订单当前状态 | 处理方 | 库存规则 |
|---|---|---|
| 已支付（商家尚未接单） | 平台直接批准 | 退回对应菜品份数 |
| 制作中、待取货 | 店主审批 | 同意时明确选择是否回库 |
| 已完成 | 店主审批，完成后72小时内申请 | 永不回库 |
| 待支付、已取消、已退款 | 不受理新申请 | 待支付使用原取消链路 |

商家待审批申请自创建起72小时未处理，系统自动同意并退款，已接单餐品不自动回库。拒绝后学生可申请经理介入：食堂经理只能处理其授权食堂/窗口，总经理具有平台数据范围时可处理全部争议。技术管理员没有审批角色。经理介入没有额外自动审批期限。

申请只占用退款额度，不暂停制作；批准后的份数退出履约。整单所有份数都批准退款后不能继续接单、出餐或凭旧取餐码核销。资金及必要库存都完成后，未完成订单转为“已退款”；此前已经完成的订单保持“已完成”和既有评价。其他菜品仍正常履约。排行榜保持已支付累计销量口径，不扣减退款份数，重建也包含已退款订单。

## 状态与一致性

退款状态：PENDING待商家、REJECTED商家拒绝、APPEALED经理介入、APPROVED已批准、PROCESSING执行中、SUCCESS完成、CLOSED学生撤回或经理最终拒绝。

- 未接单申请直接APPROVED；已接单先PENDING。拒绝后仍保留申请额度用于申诉，可撤回释放额度；经理最终拒绝也释放额度。
- 所有订单内变更先锁订单再锁退款单，和接单/出餐/核销共享版本条件；幂等键同用户唯一，不同内容复用原键会冲突。相同审批结果可重试，不能覆盖另一个决定。
- order_items的refund_reserved_quantity含有效申请及已成功份数；refund_approved_quantity含已批准/处理中/成功份数；refunded_quantity仅含资金及必要库存步骤都确认的份数。数据库检查保证0≤成功≤批准≤占用≤购买份数。
- 订单本地事务写入退款、计数和不可覆盖审批轨迹，不持数据库事务等待退款入账。后台每5秒有界读取最多10笔APPROVED/租约到期PROCESSING任务，原子领取60秒执行租约。异常保留处理中，租约到期按同一退款号恢复。
- 用户服务锁定账户行，核验原PAY余额扣款及同支付单累计退款上限，再把余额和REFUND流水在同一本地事务提交。相同refundNo核对原支付与金额后直接确认，不再加款；已入账后即使账户停用/软删除，重试仍能确认原事实。尚未入账且账户不可用则保留失败待处理，不能凭空成功。
- 支付服务只接受真实SUCCESS且渠道BALANCE的支付；原SUCCESS支付事实保持不变。MOCK记录没有真实余额扣款，不能借退款给账户充值。
- 库存服务锁定库存行，同一refundNo只记一次REFUND_RESTORE，original_biz_ref关联原ORDER_DEDUCT。原取消回补与多次部分退款共享原扣减总量上限；流水、库存及原有MQ Outbox同库提交，提交后刷新Redis，缓存失败由已有库存事件修正。
- 执行顺序为资金、必要库存、订单成功确认。任一步未知都不显示SUCCESS。资金可能已经到账而订单仍显示处理中，后续重试核对既有流水继续完成库存，绝不能再退一次钱。
- 到期任务每30秒最多处理50笔PENDING，到期查询走状态/时间索引。经理争议不进入商家超时自动审批。未新增跨服务长Seata事务或新的退款MQ通道；通知与库存继续复用既有Outbox/MQ。

## 学生接口

以下为网关路径，使用学生Bearer Token。orderId是本人订单主键，itemId是详情items返回的订单明细主键，不能使用productId代替。

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | /api/orders/{orderId}/refunds | 申请一项退款，必带Idempotency-Key |
| GET | /api/orders/{orderId}/refunds?cursor=... | 本人退款记录，默认最新20条 |
| POST | /api/orders/{orderId}/refunds/{refundId}/appeal | 商家拒绝后请求介入，无请求体 |
| POST | /api/orders/{orderId}/refunds/{refundId}/withdraw | 尚未批准时撤回，无请求体 |

申请示例，Idempotency-Key为8至64位字母、数字、下划线或短横线，同一请求重试必须保留原键及原内容：

```json
{"itemId":12,"quantity":1,"reason":"少了一份餐品"}
```

所有接口沿用ApiResponse。列表data为items/nextCursor/hasMore，不做全表COUNT。单项包含refundId、refundNo、requestKey、orderId、itemId、productName、quantity、amount、status、reason、decisionReason、restock、createdAt、dueAt。游标只能来自同一本人订单；未知/他人资源统一不可见。详情items新增itemId、refundableQuantity、fulfillableQuantity、refundedQuantity，原始购买数量与成交金额不改写。

## 管理接口（仅后端，未接入uni-app）

- GET /api/orders/refunds/manage?windowId=1&cursor=...：店主看PENDING；食堂经理/总经理看APPEALED。非平台总经理必须指定并验证授权窗口，平台总经理可以不指定查看全部争议。每页20条，数据库先限定范围再分页。
- POST /api/orders/refunds/{refundId}/decision：店主只处理PENDING，经理只处理APPEALED；已完成不能restock=true，拒绝也不能回库。

```json
{"approve":true,"restock":false,"reason":"同意退回缺少的餐品费用"}
```

审批轨迹写order_refund_actions，记录申请、决定、撤回、介入、自动审批和成功，系统操作actor_id为空。当前学生页面展示最新决定及记录列表，没有开放轨迹管理写接口。

## 服务间接口

均为服务直连路径，必须带X-Internal-Token，网关不转发/internal路径。

- GET /internal/payments/orders/{orderId}/refund-fact：返回userId/paidAmount/channelCode最小事实，仅用于申请时核验。
- POST /internal/payments/orders/{orderId}/refund：请求refundNo、amount，由支付服务解析真实paymentNo/收款用户。
- POST /internal/users/{userId}/balance/refund：请求paymentNo、refundNo、amount，最终核验原余额扣款及累计上限。
- POST /internal/stocks/refund：请求orderNo、refundNo、windowId、productId、quantity，最终核验原库存扣减及累计回补量。

内部写接口返回稳定refundNo；调用方同时核验成功码与相同编号，不把空响应当成功。

## 学生端交互

订单详情“退款与售后”进入独立页面，按菜品选择份数并确认，金额由服务端确定。处理记录支持到底加载、下拉刷新、拒绝后申请介入及未批准时撤回；所有确认复用统一确认组件，错误即时Toast。页面复用应用唯一通知Socket，推送只触发查权威接口，不在客户端宣布到账；阅读历史时保留已加载记录。

请求结果不明时把原申请键和内容按用户UUID/订单保存，回页或弱网重试复用同一份请求，避免重复建单。没有保存密码/Token。已知失败可重新编辑；账号切换、离页后的响应被丢弃。没有任何商家审批页面进入学生端。

## 迁移和验证记录（2026-11-06）

已执行并核验user_db/V10__balance_refund_origin.sql、stock_db/V3__partial_refund_origin.sql、order_db/V7__create_item_refunds.sql。备份位于C:/Users/DELL/.codex/backups/order-system-cloud/20261106-105952-refunds。原业务字段逐字节一致：10条余额流水、21条库存流水、17笔订单、18条订单明细；退款计数初值0、两张新表为空，历史已完成时间用原updated_at回填。脚本是单次迁移，不能盲目重复执行。

临时表执行真实Mapper SQL验证数量上限、全量获批后履约拦截、完成时间不被退款修改、到期队列、租约恢复及原资金/库存单隔离。Node隔离检查API、页面重试和并发行为。Java测试源码已写但未经授权不运行；尚未加载新后端、真机编译或真实资金/库存端到端验证。上线需要各相关服务同步加载新代码，不能长期混用旧版履约逻辑。新代码所需服务为订单、支付、用户、库存、通知，以及既有网关/注册中心/数据库/Redis/MQ；不自动重启。
