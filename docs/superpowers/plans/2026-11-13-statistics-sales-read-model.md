# 统计服务销量读模型实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `superpowers:executing-plans` 按任务执行；本次请求已明确要求开始实施。

**Goal:** 新建统计服务承接累计菜品销量榜和学生端月售展示，并用支付成功时间建立可追溯的销量读模型。

**Architecture:** 支付服务传递不可变的支付成功时间，订单服务与支付状态在同库事务中写入 paid_at 和 outbox。统计服务消费 RocketMQ，持久化订单销量事实与 ALL/MONTH 汇总，再以 Redis ZSet 提供榜单；历史数据由开发库迁移从支付成功记录回填。学生端只读统计接口。

**Tech Stack:** Spring Boot、Spring Security JWT、MyBatis-Plus Mapper、Druid、MySQL、RocketMQ、`common-redis` 封装、Redisson 分布式锁、uni-app x。

**Spec:** [统计服务销量读模型约定](../specs/2026-11-13-statistics-sales-read-model.md)

## Global Constraints

- 不运行 Maven、Maven Wrapper、构建、测试或服务重启命令。
- 新增及修改的业务代码使用详细中文注释，明确支付时间、权限、事务、幂等、消息重试和缓存重建的失败行为。
- 统计服务不得直接连接订单库或支付库；所有事实来自订单事件/受令牌保护的订单快照接口。
- 退款不冲减销量；待支付和已取消订单不入榜。
- 学生端只承载 CUSTOMER 展示，不增加管理页面。
- 数据迁移执行前先确认目标开发库并备份 `order_db`、`payment_db`；迁移只新增字段/新库并回填可准确关联的成功支付事实。

## Review Focus

- 重放/重复投递同一订单：事件事实与汇总只增加一次。
- Redis 丢失或消费 Redis 更新失败：MySQL 事实不丢，重投/重建不重复累加。
- 订单支付与取消并发：仅订单状态 CAS 胜者写 paid_at/outbox。
- 支付时间为空或历史支付记录无法关联：不伪造时间，不纳入错误月份。
- 用户非 CUSTOMER、窗口 ID 非法、月售查询无记录：拒绝越权并保持稳定空结果。

---

### Task 1: 新服务边界与数据库迁移

**Files:**
- Create: `statistics-service/pom.xml`、启动类、`application.yml`、`application-mq.yml`
- Modify: 根 `pom.xml`
- Create: `docs/database/migrations/order_db/V8__order_paid_at.sql`
- Create: `docs/database/migrations/statistics_db/V1__create_sales_read_model.sql`
- Modify: 数据库服务清单及 `docs/ARCHITECTURE.md`

- [x] 注册 9111 统计服务与 `statistics_db`，沿用既有 Druid、JWT、Redis、Nacos、RocketMQ 依赖。
- [x] 给订单增加 nullable `paid_at`，用 `payment_db` 成功支付时间回填；只覆盖空值。
- [x] 建立事件、事件明细、ALL/MONTH 菜品与窗口汇总表；以历史成功支付事实初始化。
- [x] 核对 SQL 可重跑性、外键边界、唯一键和中文字段注释。

### Task 2: 精确支付时间与事件快照

**Files:**
- Modify: 支付 Mapper、支付服务、内部关闭支付结果接口
- Modify: `order-api` 内部支付命令/销量快照契约
- Modify: 订单 DO、Mapper、Persistence Service、Order Service、内部 Controller、销量 Outbox Writer
- Modify: 订单 RocketMQ outbox 发布配置

- [x] 支付服务从 `payments.success_time` 读取时间并传递给订单服务，包括关单竞争时发现支付成功的分支。
- [x] 订单首次支付状态 CAS 同事务写 paid_at、状态通知和带菜品快照的销量 outbox；重复通知不覆盖时间、不重复写 outbox。
- [x] 提供仅供服务间调用、要求 `X-Internal-Token` 的已支付销量快照接口，以兼容历史 ID-only 消息。

### Task 3: 统计服务投影、榜单与读取接口

**Files:**
- Create: `statistics-service` 的 Controller、Service、Mapper、DO/VO、MQ Consumer、Security、Redis Lua
- Modify: `docs/nacos/gateway-service.yaml`
- Modify: 订单服务旧排行榜路由/消费者，避免继续维护第二份活动榜数据

- [x] 消费新旧事件；稳定事件键与订单唯一键防止重复累计，事件、明细和两类汇总在本地事务内提交。
- [x] 通过 `RedisOperationsService` 执行 ZSet/Lua；用 MySQL 汇总重建 Redis，不能让 Redis 成为事实源。
- [x] CUSTOMER JWT 保护三条学生查询接口；订单服务不再拥有活跃的榜单消费者/重建任务。
- [x] 网关 Nacos 样例将 `/api/statistics/**` 转发到 `statistics-service`。

### Task 4: 学生端月售显示

**Files:**
- Modify/Create in `E:/order_system_cloud_user`: `api/ranking.uts`、统计 API、首页与菜单页

- [x] 累计榜改读 `/statistics/rankings/sales`，保留现有菜品定位行为。
- [x] 首页一次批量读取窗口月售；点餐页一次读取当前窗口菜品月售。
- [x] 统计接口失败不阻塞菜单/窗口展示，沿用现有移动端密度、颜色和注释约定。

### Task 5: 开发库迁移与静态复核

- [x] 在本地开发 MySQL 迁移前备份 `order_db` 与 `payment_db`，校验备份可读。
- [x] 执行两个迁移并核对 paid_at 与 payment success_time 一致、未支付订单仍为空、汇总与成功支付订单明细一致。
- [x] 更新 `docs/PROJECT_PROGRESS.md`、`docs/ARCHITECTURE.md`、`docs/SALES_RANKING.md`、此计划状态。
- [x] 运行允许的静态检查（差异空白、SQL/配置/代码调用链人工核对）；不运行 Maven、构建、测试或重启。

## 执行记录

- 2026-11-13：开发 MySQL 预检确认 18 笔订单、12 笔成功支付可关联，涉及 14 条订单明细、共 28 份；MySQL 容器健康。迁移前备份 `order_db` 和 `payment_db`，gzip 完整性检查通过：`C:\Users\DELL\.codex\backups\order_system_cloud\statistics_sales_20261113\order-payment-before-statistics-171302.sql.gz`，SHA-256 `aa5c7af1ee08c74ddf86ca685333bffb02a142fcae94de0548347ab001f1c4d2`。
- 2026-11-13：首次执行发现 MySQL CLI 字符集没有固定，导致中文状态条件未命中；当时 DDL 已成功，但销量事实为空，未写入错误数据。两份脚本加入 `SET NAMES utf8mb4` 后，以 `utf8mb4` 客户端重新执行幂等迁移。
- 2026-11-13：迁移核验通过：`orders=18`、`paid_at` 非空 12、与 `payments.success_time` 不一致 0、未支付订单误写 `paid_at` 0；统计事件 12、明细 14、数量 28；累计窗口/菜品各 28，按月份 2026-10 为 25、2026-11 为 3；投影版本 12。`statistics_service` 仅获 `statistics_db` 读写权限，并成功登录读取事实表。
- 2026-11-13：统计服务、订单/支付事件接线、Gateway Nacos 路由样例及学生端调用已补；本地静态检查已完成。未运行 Maven、构建、测试、应用启动或服务重启；RocketMQ/Redis/Gateway/Android 真机端到端运行验收待用户启动最新代码和发布 Nacos 路由后完成。
