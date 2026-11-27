# 优惠券服务实施计划

> **执行方式：**当前会话逐任务实现，按本计划顺序推进。
> **目标：**实现首期平台预算承担的满减券，覆盖领取、订单锁券、支付核销/取消释放、退款金额和学生端选券。
> **架构：**新增 coupon-api 与 coupon-service，服务使用独立 coupon_db；订单服务核价和分摊优惠，Seata 覆盖下单锁券与取消释放，支付成功由订单
> Outbox 可靠通知券服务核销。
> **技术栈：**Java 21、Spring Boot/Cloud、MyBatis-Plus、MySQL、Seata AT、RocketMQ；学生端沿用 uni-app x / UTS。
> **方案：**docs/superpowers/specs/2026-11-17-coupon-service-design.md

## 全局约束

- 项目 AGENTS.md 禁止未经当前请求明确授权而运行 Maven、Wrapper、Maven 生命周期和相关启动/重启命令；本次不运行 Maven。
- 所有新增/修改 Java 代码按项目约定补充详细中文注释，特别说明权限、事务、幂等和失败行为。
- uni-app 仅承载 CUSTOMER；Vue 管理端仓库当前未找到，本次只实现后端管理接口，不在 uni-app 添加管理页面。
- coupon-service 只访问 coupon_db；其下单锁券和取消释放接口必须参加 Seata AT 并有 undo_log。
- 后端与学生端 worktree 都从 a82cd08 建立；不得复制、覆盖或回滚两个原工作区的未提交改动。

## Review Focus

- 券停发、过期或不属于请求用户时不能领取/锁定；Task 2 覆盖。
- 并发领取最后一张券不能超发，重复领取不能创建多张；Task 2 覆盖。
- 同一订单幂等键改用另一张券必须冲突；Task 3 覆盖。
- 关单结果为 PAID 或未知时不能释放券；支付事件只有券服务提交核销回执后才完成，未确认时由 Outbox 退避重投；Task 3 覆盖。
- 分摊舍入与部分退款不超过实际支付金额；Task 3 覆盖。

---

### Task 1：模块契约、服务骨架与数据库结构

**Files:**

- Create: coupon-api/pom.xml
- Create: coupon-api/src/main/java/com/hbue/ordering/coupon/api/client/CouponServiceClient.java
- Create: coupon-api/src/main/java/com/hbue/ordering/coupon/api/client/fallback/CouponServiceClientFallbackFactory.java
- Create: coupon-api/src/main/java/com/hbue/ordering/coupon/api/dto/CouponLockRequest.java
- Create: coupon-api/src/main/java/com/hbue/ordering/coupon/api/dto/CouponReleaseRequest.java
- Create: coupon-api/src/main/java/com/hbue/ordering/coupon/api/vo/CouponLockVO.java
- Create: coupon-service/pom.xml
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/CouponServiceApplication.java
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/config/CouponSecurityConfig.java
- Create: coupon-service/src/main/resources/application.yml
- Create: docs/database/migrations/coupon_db/V1__create_coupon_tables.sql
- Create: docs/database/migrations/order_db/V10__add_coupon_amount_snapshots.sql
- Modify: pom.xml, order-service/pom.xml, .env.example
- Modify: gateway-service/src/main/resources/application.yml and README.md

> `docker-compose.yml` 仅承载共享基础设施，不编排单个 Java 业务服务；复用现有 MySQL、Seata 和 RocketMQ 容器，因此本次不增加业务容器定义。

**Interfaces:**

- CouponLockRequest: userCouponId、userId、orderNo、windowId、eligibleAmount。
- CouponReleaseRequest: orderNo；只能释放此订单已锁定的券。
- CouponLockVO: userCouponId、templateId、title、discountAmount。

- [x] 在根 pom 登记两个模块；API POM 复用 stock-api 的依赖模式，service POM 复用 stock-service 并添加 Seata。
- [x] 建立 coupon_db 的模板、模板窗口关联、用户券、用券流水和 undo_log；为订单主表/明细增加优惠、应付与标题快照字段。
- [x] 为历史订单回填 discount_amount=0、payable_amount=total_amount；历史明细回填
  line_discount_amount=0、line_payable_amount=line_amount。
- [x] 配好独立 coupon_db、Nacos service name、Seata transaction group、JWT 校验与 X-Internal-Token 内部接口安全边界。
- [x] 静态核对 XML 和 SQL 字段/索引与模型命名；不得运行 Maven。

### Task 2：领取、查询、管理与锁券生命周期

**Files:**

- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/controller/CouponController.java
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/controller/CouponManagementController.java
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/controller/CouponInternalController.java
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/service/CouponService.java
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/service/impl/CouponServiceImpl.java
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/mapper/*
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/model/*
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/dto/*
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/vo/*
- Create: coupon-service/src/main/java/com/hbue/ordering/coupon/enums/CouponStatus.java
- Test: coupon-service/src/test/java/com/hbue/ordering/coupon/service/impl/CouponServiceImplTest.java

**Interfaces:**

- CUSTOMER: list claimable templates, claim once, and list own coupons.
- GENERAL_MANAGER with platform scope: create platform-funded template, stop new claims, stop unconsumed use.
- Internal API: lock userCouponId for orderNo/windowId/eligibleAmount; release by orderNo.
- First phase requires threshold and fixed discount, one per customer per template, no stacking, and no window-funded
  coupon.

- [x] 先为领取库存上限、重复领取、券归属、有效期和锁券状态转换写测试断言。
- [x] 用数据库条件更新及唯一索引实现领取上限和每人限领；不使用 Redis 锁替代数据库约束。
- [x] 实现 AVAILABLE→LOCKED→USED，以及订单取消时 LOCKED→AVAILABLE/EXPIRED；同订单重试幂等，其他订单不能释放或核销。
- [x] 管理角色只允许总经理且服务层验证平台 scope；CUSTOMER 身份从已验证 JWT 提取，不能信任请求体 userId。
- [x] 静态复核接口角色、数据范围、状态和异常行为；不运行 Maven。

### Task 3：订单金额、支付、取消、核销与退款

**Files:**

- Modify: order-service/src/main/java/com/hbue/ordering/order/dto/request/OrderCreateRequest.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/service/impl/OrderServiceImpl.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/service/impl/OrderPersistenceServiceImpl.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/model/OrderDO.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/model/OrderItemDO.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/mapper/OrderMapper.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/mapper/OrderItemMapper.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/vo/OrderDetailVO.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/vo/OrderItemVO.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/vo/OrderInternalVO.java
- Create: order-service coupon consume Outbox writer/mapper/publisher and RocketMQ profile configuration
- Create: order-api 券核销回执 DTO / Feign client；order-service 内部确认接口
- Modify: payment-service/src/main/java/com/hbue/ordering/payment/dto/OrderInternalDTO.java
- Modify: payment-service/src/main/java/com/hbue/ordering/payment/service/impl/PaymentServiceImpl.java
- Modify: order-service/src/main/java/com/hbue/ordering/order/service/impl/OrderRefundServiceImpl.java
- Test: order-service/src/test/java/com/hbue/ordering/order/service/impl/OrderServiceImplTest.java
- Test: order-service/src/test/java/com/hbue/ordering/order/service/impl/OrderRefundServiceImplTest.java
- Test: payment-service/src/test/java/com/hbue/ordering/payment/service/impl/PaymentServiceImplTest.java

**Interfaces:**

- OrderCreateRequest adds optional userCouponId; request hash includes it.
- OrderInternalVO and payment OrderInternalDTO expose totalAmount, discountAmount, payableAmount; PaymentServiceImpl
  uses payableAmount.
- Each order item persists lineDiscountAmount and linePayableAmount; refund calculation uses linePayableAmount.
- Coupon consume Outbox stores orderNo and userCouponId in the same local transaction that confirms payment;
  coupon-service confirms only after its local use transaction commits, and the publisher retries unconfirmed deliveries
  with backoff.
- cancelPendingOrder releases only after payment service confirms CLOSED and order cancellation succeeds inside the
  existing Seata transaction.

- [x] 为同一幂等键换券冲突、金额守恒、尾差分摊、PAID/UNKNOWN 不释放、Outbox 重复投递、部分退款实付上限编写测试断言。
- [x] 正式下单重新核价并锁券，按适用商品金额比例分摊到明细，分币舍入尾差固定分配，订单与明细均保存不可变快照。
- [x] Seata 覆盖锁券/订单/库存扣减与取消释放；券服务异常或业务拒绝必须失败关闭并回滚。
- [x] 支付使用 payableAmount；支付成功在订单本地事务写券核销 Outbox，券服务消费者幂等标记 USED 并在提交后回执确认。
- [x] 退款支付核验 payableAmount，并按明细 linePayableAmount 和份数计算累计退款上限。
- [x] 静态遍历所有 totalAmount 和 lineAmount 支付/退款调用点；不得运行 Maven。

### Task 4：学生端领取与结算选券

**Files:**

- Create: order_system_cloud_user/api/coupons.uts
- Create: order_system_cloud_user/pages/coupons/list.uvue
- Modify: order_system_cloud_user/pages.json
- Modify: order_system_cloud_user/pages/mine/mine.uvue
- Modify: order_system_cloud_user/pages/order/confirm.uvue
- Modify: order_system_cloud_user/pages/order/detail.uvue
- Modify: order_system_cloud_user/api/order.uts

**Interfaces:**

- 学生端只调用 CUSTOMER 的可领券、领取、我的券接口；订单请求仅发送 userCouponId。
- 结算页展示服务器确认的原价、优惠、应付金额及券门槛/范围/有效期。

- [x] 在“我的”增加 CUSTOMER 优惠券入口，并注册学生券页。
- [x] 新增券 API 及响应解析，复用现有统一 HTTP 层。
- [x] 在确认页将 userCouponId 保存到用户草稿、恢复并发送；重新提交失败时保留选择。
- [x] 订单详情和确认页展示优惠与实付；不添加管理功能。
- [x] 对照 uni-app 的 CUSTOMER 登录边界、DESIGN.md 和 UX-CONTRACT.md 静态核对。

### Task 5：运行配置、文档与开发库迁移

**Files:**

- Modify: README.md, docs/ARCHITECTURE.md, docs/API_DOCS.md, .env.example,
  gateway-service/src/main/resources/application.yml, docs/nacos/gateway-service.yaml
- Create/Modify: docs/database/migrations/coupon_db/V1__create_coupon_tables.sql and
  docs/database/migrations/order_db/V10__add_coupon_amount_snapshots.sql

- [x] 更新服务目录、端口、coupon_db、外部接口清单、架构与数据字段说明；注明 Vue 管理端仓库待接入。
- [ ] 检查脚本目标库、当前表/列/索引、重复执行风险及备份；只为已确认的本地开发数据库迁移。当前开发库端口不可连接、Windows
  MySQL 服务已停止且未提供本地 `.env`，因此无法检查现有 schema 或备份。
- [ ] 执行并查询 coupon_db 与 order_db 迁移结果，记录表、列、约束和 undo_log。需先恢复项目开发 MySQL `127.0.0.1:9095`
  并提供客户端工具，不能仅凭脚本静态检查擅自迁移。
- [x] 执行 git diff --check、POM XML 语法解析及源码静态调用面检查；因 AGENTS.md 限制，不运行 Maven、测试、构建或服务重启。
