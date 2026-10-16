# 库存与分布式事务（stock-service + Seata）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `superpowers:executing-plans` 按任务完成；每个任务结束后进行相应模块验证。

**Goal:** 补齐下单链路的库存校验与扣减，消除"当前可无限下单"的真实缺陷；以 `order × stock` 场景落地 Seata AT 分布式事务，并建立「Redis 原子预扣 + DB 乐观锁 + 库存流水 + 定时对账」的最终一致防线。

**Architecture:** 新增 `stock_db`（`stock` / `stock_flow` / `undo_log`）与独立 `stock-service`（端口 9104），对 order-service 提供内部扣减/回补接口；order-service 引入 Seata 客户端，在下单事务上标 `@GlobalTransactional` 跨服务扣库存；Redis Lua 负责高频原子预扣，DB 为唯一事实来源，定时任务对账兜底。

**Tech Stack:** Java 21、Spring Boot 3.5.6、Spring Cloud 2025.0.0、Spring Cloud Alibaba 2025.0.0.0、Seata 2.x（AT 模式）、MyBatis-Plus、Redis、Nacos、Maven。

**Spec:** `docs/ARCHITECTURE.md` §9.1/§9.2、`docs/database/SERVICE_DATABASE_INVENTORY.md`。

## Global Constraints

- 迁移脚本必须幂等（`IF NOT EXISTS`），建库建表 DDL 需经用户确认后再执行。
- 禁止执行 `docker compose down -v`（会删数据卷）。
- Seata 仅用于 `order × stock` 下单扣库存场景；余额支付、支付推进订单沿用「幂等重试 + 定时对账」，不引入 Seata。
- Redis 不得作为库存事实来源，仅为加速快照；`stock.available_qty`（DB）为准。
- 所有 Java 逻辑代码（DO/VO/DTO 等数据实体除外）逐步骤中文注释；少用 Lambda/Stream。
- 新建源文件保持 CRLF 行尾。
- 不创建提交、不推送远程仓库。

## Review Focus

- 超卖防护：Redis Lua 预扣与 DB 乐观锁是否双重生效；并发下 `available_qty` 不得出现负数。
- 幂等：`stock_flow` 唯一键 `(biz_type, biz_ref, stock_id)` 是否覆盖重复扣减与重复回补。
- 回补正确性：`ORDER_RESTORE` 不得把已扣减库存重复加回。
- 对账有效性：Redis 快照与 DB 漂移能被定时任务发现并修正；流水累计与主表差值自洽。
- 事务边界：`@GlobalTransactional` 是否覆盖 order 落单 + stock 扣减；TC 不可用时是否显式失败而非静默成功。

---

### Task 1: 中间件与数据库落地

**Files:**
- Modify `docker-compose.yml`（新增 `seata` 服务）
- Add `docs/database/migrations/stock_db/V1__create_stock_tables.sql`
- Add `docs/database/migrations/order_db/V4__add_undo_log.sql`
- Modify `.env`、`.env.example`（新增 `STOCK_DB_*`）

- [ ] 编写 `stock_db` 迁移脚本（建库 + `stock_service` 账号 + 三张表，DDL 见文末「参考结构」）。
- [ ] 编写 `order_db` 的 `undo_log` 补表脚本。
- [ ] 在 `docker-compose.yml` 增加 Seata Server：镜像 `apache/seata-server:2.5.0`，端口 `9109:8091`，注册到容器内 Nacos（`nacos:8848`），存储模式 `file`。
- [ ] 启动 Seata 容器，确认 Nacos 服务列表出现 `seata-server`。
- [ ] 在本机 MySQL 执行两个迁移脚本，确认库、表、账号就绪。

### Task 2: 契约模块 `stock-api`

**Files:**
- Add `stock-api/pom.xml`、`stock-api/src/main/java/**`
- Modify 根 `pom.xml`（注册模块）

- [ ] 定义 `StockDeductRequest` / `StockRestoreRequest` / `StockVO`（字段级中文 Javadoc，不做逐步注释）。
- [ ] 定义 `StockServiceClient`（Feign 内部接口）+ 降级工厂（Sentinel fallback，TC/服务不可用时快速失败）。
- [ ] 根 `pom.xml` 注册 `<module>stock-api</module>`。

### Task 3: `stock-service` 骨架与领域层

**Files:**
- Add `stock-service/pom.xml`、`src/main/resources/application.yml`
- Add 启动类、`config/StockSecurityConfig`、`security/StockAuthenticationEntryPoint`、`security/StockAccessDeniedHandler`
- Add `model/StockDO`、`model/StockFlowDO`、`mapper/StockMapper`、`mapper/StockFlowMapper`
- Add `service/StockService` + `service/impl/StockServiceImpl`
- Add `support/StockRedisScript`（Lua 脚本加载与执行）

- [ ] 建模块骨架（端口 9104；datasource / redis / nacos / 安全 配置照 `file-service` 模板）。
- [ ] 实现 Redis Lua 原子预扣：库存不足直接失败并返回剩余量。
- [ ] 实现 DB 乐观锁扣减 + `stock_flow` 幂等记账（同一订单同一菜品只记一次）。
- [ ] 实现回补（`ORDER_RESTORE`）与库存查询。
- [ ] 单元测试：并发扣减不超卖、重复扣减幂等、回补幂等。

### Task 4: `stock-service` 内部接口与定时对账

**Files:**
- Add `controller/StockInternalController`
- Add `task/StockReconcileTask`

- [ ] 内部接口：`POST /internal/stocks/deduct`、`POST /internal/stocks/restore`、`GET /internal/stocks/{windowId}/{productId}`（校验 `X-Internal-Token`）。
- [ ] 定时对账：比对 Redis 快照与 `stock.available_qty`、流水累计与主表差值，漂移则修正并记录日志。
- [ ] 网关**不配置** `/internal/**` 路由（保持仅直连）。

### Task 5: `order-service` 接入 Seata 与扣库存改造

**Files:**
- Modify `order-service/pom.xml`（新增 Seata 与 `stock-api` 依赖）
- Modify `order-service/src/main/resources/application.yml`（seata 配置）
- Modify 下单流程实现类（`@GlobalTransactional` + 调用扣库存）

- [ ] 引入 `com.alibaba.cloud:spring-cloud-starter-alibaba-seata`，配置 `tx-service-group` / `registry` / `vgroup-mapping`。
- [ ] 下单流程在创建订单后调用 `StockServiceClient.deduct`，整段标 `@GlobalTransactional`。
- [ ] 校验：库存不足时全局回滚，订单与库存流水均不落库。
- [ ] 端到端验证：并发下单不超卖；模拟 stock 服务失败验证回滚。

### Task 6: 网关、文档回填与全量验证

**Files:**
- Modify `docs/nacos/gateway-service.yaml`（stock-service 业务路由 + `-docs` 路由）
- Modify `docs/ARCHITECTURE.md`（§3 总表、§8.1 端口、§9.1 阶段、§9.2 防线、§13 变更记录）
- Modify `docs/database/SERVICE_DATABASE_INVENTORY.md`（§1/§2/§3/§5/§7）
- Modify `docs/PROJECT_PROGRESS.md`

- [ ] 新增网关路由并发布到 Nacos（只放行业务路径 `/api/**`；`/internal/**` 不配路由）。
- [ ] 回填架构文档：服务总表、端口表、阶段状态与变更记录。
- [ ] 回填数据库清单：`stock_db` 表、账号、端口、执行状态。
- [ ] 全量 `mvn -B test` 并记录实际结果。

---

## 参考结构（Task 1 的 DDL）

```sql
-- ===== stock_db =====
CREATE DATABASE IF NOT EXISTS stock_db
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'stock_service'@'%' IDENTIFIED BY '<STOCK_DB_PASSWORD>';
GRANT ALL PRIVILEGES ON stock_db.* TO 'stock_service'@'%';
FLUSH PRIVILEGES;

USE stock_db;

-- 库存主表：DB 为唯一事实来源，Redis 仅作加速快照
CREATE TABLE IF NOT EXISTS stock (
    stock_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存记录主键',
    window_id     BIGINT UNSIGNED NOT NULL COMMENT '所属档口 ID',
    product_id    BIGINT UNSIGNED NOT NULL COMMENT '菜品 ID',
    total_qty     INT             NOT NULL DEFAULT 0 COMMENT '总可售上限',
    available_qty INT             NOT NULL DEFAULT 0 COMMENT '当前可售数量，DB 为准',
    version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    is_deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (stock_id),
    UNIQUE KEY uk_window_product (window_id, product_id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存主表';

-- 库存流水：幂等记账 + 对账依据
CREATE TABLE IF NOT EXISTS stock_flow (
    flow_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水主键',
    biz_type    VARCHAR(32)     NOT NULL COMMENT '业务类型：ORDER_DEDUCT/ORDER_RESTORE/ADMIN_ADJUST',
    biz_ref     VARCHAR(64)     NOT NULL COMMENT '业务单据号（订单号），用于幂等',
    stock_id    BIGINT UNSIGNED NOT NULL COMMENT '库存记录 ID',
    window_id   BIGINT UNSIGNED NOT NULL COMMENT '档口 ID（冗余，便于按档口对账）',
    product_id  BIGINT UNSIGNED NOT NULL COMMENT '菜品 ID（冗余）',
    change_qty  INT             NOT NULL COMMENT '变动数量：扣减为负、回补为正',
    before_qty  INT             NOT NULL COMMENT '变动前可售数量',
    after_qty   INT             NOT NULL COMMENT '变动后可售数量',
    operator_id BIGINT UNSIGNED     NULL COMMENT '操作者 user_id，系统触发可为空',
    remark      VARCHAR(255)        NULL COMMENT '备注',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (flow_id),
    UNIQUE KEY uk_biz_flow (biz_type, biz_ref, stock_id),
    KEY idx_stock (stock_id),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存流水';

-- Seata AT 回滚日志（官方标准结构）
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id     BIGINT       NOT NULL COMMENT '分支事务 ID',
    xid           VARCHAR(128) NOT NULL COMMENT '全局事务 ID',
    context       VARCHAR(128) NOT NULL COMMENT 'undo_log 序列化方式',
    rollback_info LONGBLOB     NOT NULL COMMENT '回滚信息',
    log_status    INT          NOT NULL COMMENT '0 正常 1 已防御',
    log_created   DATETIME(6)  NOT NULL,
    log_modified  DATETIME(6)  NOT NULL,
    PRIMARY KEY (branch_id),
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AT 模式回滚日志';

-- ===== order_db 追加（V4__add_undo_log.sql） =====
USE order_db;

CREATE TABLE IF NOT EXISTS undo_log (
    branch_id     BIGINT       NOT NULL COMMENT '分支事务 ID',
    xid           VARCHAR(128) NOT NULL COMMENT '全局事务 ID',
    context       VARCHAR(128) NOT NULL COMMENT 'undo_log 序列化方式',
    rollback_info LONGBLOB     NOT NULL COMMENT '回滚信息',
    log_status    INT          NOT NULL COMMENT '0 正常 1 已防御',
    log_created   DATETIME(6)  NOT NULL,
    log_modified  DATETIME(6)  NOT NULL,
    PRIMARY KEY (branch_id),
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AT 模式回滚日志';
```
