# 服务与数据库清单（已落地现状）

> 文档性质：**现状清单**。回答两个问题：①我们现在一共有哪些服务；②每个服务对应哪个库、哪些表、哪个账号。
> 事实来源：根 `docker-compose.yml`、各服务 `application.yml`、`docs/database/migrations/**`、`backup/db-accounts-fix.sh`。
> 分工：**规划中**（阶段 B/C 未落地的服务与库）见 [ARCHITECTURE.md](../ARCHITECTURE.md) 第 3、8 节；本文只记"已经建好"的部分。
> 最后核对：2026-10-24（本机 Linux，逐表读取 MySQL 的字段、索引和约束；历史记录不能替代本机实际结构核对）。

## 1. 服务总表

| # | 服务 | Maven 模块 | 端口 | 独立数据库 | 依赖 Redis | 网关路由 | 状态 |
|---|---|---|---|---|---|---|---|
| 1 | `gateway-service` | `gateway-service` | 9090 | 无 | ✅（登录限流令牌桶） | — | ✅ |
| 2 | `auth-service` | `auth-service` | 9094 | 无 | ✅（Refresh Token + 黑名单 + 分布式锁） | `/api/auth/**` | ✅ |
| 3 | `user-service` | `user-service` | 9092 | `user_db` | ✅（范围缓存 + 黑名单） | `/api/users/**` | ✅ |
| 4 | `order-service` | `order-service` | 9091 | `order_db` | ✅（黑名单） | `/api/orders/**` | ✅ |
| 5 | `product-service` | `product-service` | 9093 | `product_db` | ✅（黑名单） | `/api/products/**` | ✅ |
| 6 | `payment-service` | `payment-service` | 9101 | `payment_db` | ✅（黑名单） | `/api/payments/**` | ✅ |
| 7 | `file-service` | `file-service` | 9102 | `file_db` | ✅（黑名单） | `/api/files/**` | ✅ |
| 8 | `stock-service` | `stock-service` | 9104 | `stock_db` | ✅（库存快照 + 黑名单） | 仅 `-docs` 路由 | ✅ |
| 9 | `cart-service` | `cart-service` | 9103 | 不建 MySQL 库，仅使用 Redis | ✅（购物车主存储 + 黑名单） | `/api/cart/**` | ✅ |

> `stock-service` 当前对外**只有** `/internal/stocks/**` 三个内部接口（扣减、回补、查询），
> 按项目约定 `/internal/**` 不进网关，故 Nacos 路由中只有 `stock-service-docs`
> （`/api/stocks/v3/api-docs`），**没有 `/api/stocks/**` 业务路由**。后续若新增顾客侧的
> 库存查询接口，再补业务路由（注意 `StripPrefix=1` 会把 `/api/stocks/internal/...`
> 映射成 `/internal/...`，届时需先把内部路径排除在业务路由之外）。

**公共库与契约库（不独立部署）**

| 模块 | 作用 | 依赖方 |
|---|---|---|
| `common-core` | 通用错误码、异常、角色枚举、`DataScopeService` 契约 | 全部服务 |
| `common-response` | `ApiResponse`（无 Servlet 依赖，供 WebFlux 用） | 全部服务 |
| `common-redis` | Redis 基础操作、缓存键常量 | gateway / auth / user / order / product / payment / file / stock / cart |
| `common-web` | 全局异常处理（仅 Servlet 服务） | auth / user / order / product / payment / file / stock / cart |
| `user-api` | `UserBasicVO` + `UserServiceClient` + 降级工厂 | auth / order |
| `product-api` | 报价契约、窗口/餐厅内部查询客户端 + 降级工厂 | order / cart（批量核价） |
| `order-api` | 当前用户订单的只读购买事实契约 + `OrderServiceClient` + 降级工厂 | cart（清理前核实）；order 仅在测试范围校验 JSON 契约 |
| `stock-api` | `StockDeductRequest` / `StockRestoreRequest` / `StockVO` + `StockServiceClient` + 降级工厂 | order |

> 端口为**当前设备工作区**实际值（`9090` 段）。另一台设备使用的旧端口段见 [ARCHITECTURE.md](../ARCHITECTURE.md) 第 3、8.1 节；两套端口不要互相合并。

## 2. 数据库、表与迁移脚本

| 数据库 | 表 | 归属服务 | 迁移脚本（按执行顺序） |
|---|---|---|---|
| `order_db` | `orders`、`order_items`、`undo_log`、`sales_event_outbox` | order-service | [V1__create_orders_table.sql](migrations/V1__create_orders_table.sql) → [V2__add_order_idempotency.sql](migrations/V2__add_order_idempotency.sql) → [V3__create_order_items_table.sql](migrations/V3__create_order_items_table.sql) → [V4__add_undo_log.sql](migrations/V4__add_undo_log.sql) → [销量事件 outbox](migrations/order_db/V1__add_sales_event_outbox.sql) |
| `user_db` | `users`（含 `pay_password_hash`、`user_uuid`、`user_no`、`email`；V6 后不含 `phone`）、`user_data_scopes`、`balance_records` | user-service | [user_db/V1__create_users_table.sql](migrations/user_db/V1__create_users_table.sql) → [user_db/V2__create_user_data_scopes_table.sql](migrations/user_db/V2__create_user_data_scopes_table.sql) → [user_db/V3__add_pay_password_and_balance_records.sql](migrations/user_db/V3__add_pay_password_and_balance_records.sql) → [user_db/V4__add_public_user_uuid.sql](migrations/user_db/V4__add_public_user_uuid.sql) → [user_db/V5__add_user_number_and_email_login.sql](migrations/user_db/V5__add_user_number_and_email_login.sql) → [user_db/V6__remove_user_phone.sql](migrations/user_db/V6__remove_user_phone.sql) |
| `product_db` | `dining_windows`（含 `image_url`/`is_open`）、`product_categories`、`products`、`restaurants`（含 `location`/`phone`/`image_url`/`notice`/`sort_order`）、`product_search_outbox` | product-service | [product_db/V1__create_product_catalog_tables.sql](migrations/product_db/V1__create_product_catalog_tables.sql) → [product_db/V2__create_restaurants_and_window_restaurant.sql](migrations/product_db/V2__create_restaurants_and_window_restaurant.sql) → [product_db/V4__enrich_restaurants.sql](migrations/product_db/V4__enrich_restaurants.sql) → [product_db/V5__enrich_dining_windows.sql](migrations/product_db/V5__enrich_dining_windows.sql) → [product_db/V6__add_window_description_and_sort_order.sql](migrations/product_db/V6__add_window_description_and_sort_order.sql) → [product_db/V7__seed_window_messages.sql](migrations/product_db/V7__seed_window_messages.sql) → [product_db/V8__add_product_rating_projection.sql](migrations/product_db/V8__add_product_rating_projection.sql) → [product_db/V9__create_product_search_outbox.sql](migrations/product_db/V9__create_product_search_outbox.sql) → [product_db/V10__add_search_outbox_product_id.sql](migrations/product_db/V10__add_search_outbox_product_id.sql)；V9、V10 于 2026-10-28 执行成功 |
| `payment_db` | `payments` | payment-service | [payment_db/V1__create_payments_table.sql](migrations/payment_db/V1__create_payments_table.sql) |
| `file_db` | `file_objects` | file-service | [file_db/V1__create_file_objects_table.sql](migrations/file_db/V1__create_file_objects_table.sql) |
| `stock_db` | `stock`、`stock_flow`、`undo_log` | stock-service | [stock_db/V1__create_stock_tables.sql](migrations/stock_db/V1__create_stock_tables.sql) |

**演示/种子数据（仅开发环境）**：早期 [product_catalog_dev.sql](seeds/product_catalog_dev.sql) 为 1 个窗口、3 个分类、5 道菜；扩展后的 [三食堂脚本](seeds/campus_three_canteens.sql) 共 3 个食堂、9 个窗口、77 道菜，配套 [已填写的早期库存](seeds/stock_initialization_20261022.sql)。本机可用 `python3 tools/seed-local-development-data.py --apply --verify-auth` 先备份再补齐目录、库存及 13 个管理账号；重复执行保留已有账号密码与库存。

> **本机 Linux 最新业务数据快照（2026-10-24）**：3 个食堂、9 个窗口、21 个分类、77 道菜、77 条库存；19 个用户（保留 6 个顾客，新增 1 个管理员、1 个总经理、1 个餐厅经理、10 个店主），12 条数据范围。13 个新账号的用户号/邮箱密码校验共 26 次成功。账号明文密码仅存 `/home/user/.codex/backups/order_system_cloud/local_demo_20261024_003743/管理端测试账号.md`，未入 Git；完整备份位于同目录。原有 6 个用户及旧菜品数据均保留，未创建订单、支付或账务流水。

> 历史快照（2026-10-19）：当时本机 MySQL 为 6 库 15 表；`order_db` 尚无 `sales_event_outbox`，`user_db.users` 尚未完成 V5/V6。不要将此段当作当前 Schema。

> **2026-10-23 历史记录修正**：当次实际核对并完成的是订单 outbox、用户号/邮箱及移除手机号后的结构；原文“6 库 16 表”没有经过完整逐表核验，该表数结论不准确。2026-10-24 补迁移前实际只有 14 张业务表，用户库的 V2/V3/V4 被遗漏。此前 `user_db` / `order_db` 备份仍保留在 `/home/user/.codex/backups/order_system_cloud/db-migrations-20261023-DroIPN/user-order-before-migrations.sql.gz`。V6 非幂等，手机号列及索引已不存在时不得重复执行。

> ✅ **本机 Linux 最新执行状态（2026-10-24）**：已补执行 `user_db/V2`、`V3`、`V4`，新增数据范围表、余额流水表、支付密码字段及用户 UUID；补齐订单表的 `idx_orders_user_created_at(user_id, created_at)`。现逐表确认 **6 库 16 表、169 个字段**，与仓库数据字典所列必需表/字段一致。6 个存量用户已分配唯一合法 UUID v4；原有用户号、密码哈希、余额及原有时间字段的查询摘要保持不变。中文表/字段注释已按本机实际定义生成并应用，列定义、索引、约束及各表行校验和通过保留性核验；使用应用实际凭据验证 `user_service` 可查询三张用户表。完整六库备份位于 `/home/user/.codex/backups/order_system_cloud/schema-completion-20261024-CZs2mF/business-databases-before.sql.gz`，gzip 校验通过。

**表设计说明**：商品库见 [PRODUCT_CATALOG.md](PRODUCT_CATALOG.md)；订单幂等键约定见 [ORDER_IDEMPOTENCY.md](ORDER_IDEMPOTENCY.md)；`user_data_scopes` 的设计见 [2026-10-13-role-authority-design.md](../superpowers/specs/2026-10-13-role-authority-design.md)。

## 3. 数据库账号与授权

每个服务使用**只授予本库权限**的独立账号，凭据统一从 `.env`（不入 Git）注入。

| 账号 | 授权库 | 账号如何创建 | 密码变量 |
|---|---|---|---|
| `order_user` | `order_db` | **自动**：MySQL 容器入口脚本按 `MYSQL_USER`/`MYSQL_PASSWORD` 建号并授权 | `MYSQL_PASSWORD` |
| `user_service` | `user_db` | **需手工**：仓库无 SQL，仅在 [backup/db-accounts-fix.sh](../../backup/db-accounts-fix.sh) 中 | `USER_DB_PASSWORD` |
| `product_service` | `product_db` | **需手工**：同上 | `PRODUCT_DB_PASSWORD` |
| `payment_service` | `payment_db` | **迁移脚本内置**：`payment_db/V1` 中 `CREATE USER` + `GRANT` | `PAYMENT_DB_PASSWORD` |
| `file_service` | `file_db` | **迁移脚本内置**：`file_db/V1` 中 `CREATE USER` + `GRANT` | `FILE_DB_PASSWORD` |
| `stock_service` | `stock_db` | **迁移脚本内置**：`stock_db/V1` 中 `CREATE USER` + `GRANT` | `STOCK_DB_PASSWORD` |

> ⚠️ MySQL 账号存放在 `mysql` 系统库，**不随数据卷迁移**。换 Docker 引擎、重建系统库后只有 `order_user` 会自动重建，其余五个账号必须重新创建（见第 4.2 节）。


> 2026-10-22 增量：本机 order_db 已新增 sales_event_outbox（销量消息本地待发送表），当前 0 条；上面的 2026-10-19 统计为历史快照，不代表最新表数。RocketMQ Compose 配置已补，尚未启动或联调，见 [销量榜](../SALES_RANKING.md)。

## 4. 新建环境（新建数据库）清单

### 4.1 按顺序执行

1. **前置工具链**：JDK 21、Maven 3.9.x（本地仓库可写）、Ubuntu-24.04（WSL2）内的 docker engine。
2. **外部密钥**：JWT RSA 密钥对放到 `E:\home\user\.config\order-system\keys\`（`jwt-private.pem` / `jwt-public.pem`）。服务 yml 写的是 `file:/home/user/.config/...`，Windows 下按当前工作目录所在盘符解析，因此 IDEA 的工作目录必须在 E 盘项目内。
3. **准备 `.env`**：复制 `.env.example`，填 MySQL、Redis、四个库账号密码、Nacos 账号与鉴权密钥、引导账号、`PAYMENT_CHANNEL_SECRET`、`INTERNAL_ORDER_TOKEN`。
4. **启动基础设施**：`docker compose up -d`（mysql / nacos / redis）。**禁止 `docker compose down -v`**，会删数据卷。
5. **建库与建账号**：执行 4.2 的 SQL（`order_db` 由容器自动创建，无需手工）。
6. **执行迁移**：按第 2 节顺序先检查实际表、字段及索引，再逐个执行缺失迁移。`CREATE TABLE IF NOT EXISTS` 不会给已有表补字段；订单 V2、用户 V3/V4/V6 及商品多个 `ALTER` 迁移均不可盲目重复执行。不能仅凭另一设备的执行记录判断本机已完成。
7. **灌种子数据**（开发环境）：执行 `docs/database/seeds/product_catalog_dev.sql`。
8. **发布 Nacos 配置**：`gateway-service.yaml`（网关路由，内容同步保存于 [docs/nacos/gateway-service.yaml](../nacos/gateway-service.yaml)）与 `order-service-degrade-rules`（`SENTINEL_GROUP`，内容 `[]`）；发布脚本参考 [backup/nacos-init.sh](../../backup/nacos-init.sh)。
9. **启动服务**：`gateway-service` 必须在环境变量中提供 `NACOS_PASSWORD`（默认值为占位串）；其余服务使用 yml 默认值即可。首次启动 `user-service` 时用 `USER_ADMIN_BOOTSTRAP_ENABLED=true` 配合 `USER_ADMIN_BOOTSTRAP_USERNAME` / `USER_ADMIN_BOOTSTRAP_PASSWORD` 引导 `SYSTEM_ADMIN` 账号，配合 `USER_MANAGER_BOOTSTRAP_*` 引导初始总经理（含全平台范围），共七个变量。
10. **核对**：`wsl -- docker ps` 看基础设施端口；Nacos 控制台（9098）看服务列表；网关地址 9090 打通业务路由。

### 4.2 缺失项补齐（本次核对发现，仓库现有脚本未覆盖）

以下两项此前**只存在于本机手工操作或 backup 脚本中**，仓库的迁移目录里没有对应 SQL，换环境时会漏。补齐 SQL 如下：

```sql
-- ① user_db 缺 CREATE DATABASE：user_db/V1 只建表、不建库
CREATE DATABASE IF NOT EXISTS user_db
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS product_db
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ② user_service / product_service 建号（原先只在 backup/db-accounts-fix.sh 里，未入库）
CREATE USER IF NOT EXISTS 'user_service'@'%'    IDENTIFIED BY '<USER_DB_PASSWORD>';
GRANT ALL PRIVILEGES ON user_db.*    TO 'user_service'@'%';
CREATE USER IF NOT EXISTS 'product_service'@'%' IDENTIFIED BY '<PRODUCT_DB_PASSWORD>';
GRANT ALL PRIVILEGES ON product_db.* TO 'product_service'@'%';
FLUSH PRIVILEGES;
```

（`order_db` / `order_user` 由 Compose 的 `MYSQL_DATABASE` / `MYSQL_USER` 自动创建；`payment_db` / `payment_service` 由 `payment_db/V1` 迁移创建。）

### 4.3 新建环境时容易踩的坑

| 坑 | 说明 |
|---|---|
| `product_db/V2` 不幂等 | 它用裸 `ALTER TABLE ... ADD COLUMN restaurant_id`，重复执行会报 `Duplicate column name`；全新库只执行一次，重复执行前先判断列是否存在 |
| `payment_db/V1` 密码明文 | 迁移脚本里硬编码了 `payment_password_dev_2026`。仅为开发环境；正式环境应改为外部注入，不要照抄 |
| order-service 数据源未走环境变量 | 其它服务用 `${XXX_DB_URL}` 占位，`order-service/application.yml` 把 `127.0.0.1:9095/order_db` 和 `order_user` 密码写死了，换环境需手工改（**待统一**） |
| `mvn` 需在沙箱外执行 | Maven 本地仓库（如 `D:\repository`）不在沙箱可写目录内，否则报 "hit restricted" |
| JDK 23+ 默认 `-proc:none` | Lombok 注解处理器需在根 pom 显式开 `<proc>full</proc>`（已配置），IDEA 也要勾选 annotation processing |

## 5. 中间件与端口（当前设备实际值）

| 组件 | 端口 | 容器/说明 |
|---|---|---|
| MySQL 8.0 | 9095 → 3306 | 卷 `order-system-cloud-mysql-data` |
| Redis 7.4 | 9096 → 6379 | 卷 `order-system-cloud-redis-data` |
| Nacos v3.0.3 API | 9097 → 8848 | gRPC `10097` / `10098` |
| Nacos 控制台 | 9098 → 8080 | 账号 `nacos` / `.env` 中的 `NACOS_PASSWORD` |
| Sentinel Dashboard | 9099 | `order-service` 通信端口 `9100`，`product-service` 通信端口 `9110`（原 9102 已让位给 `file-service`） |
| Seata Server（TC） | 9109 → 9109 | 控制台 `9108 → 7091`；`apache/seata-server:2.5.0`，存储模式 `file`，以 `seata-server` 之名注册到 Nacos（`SEATA_GROUP` 组，cluster `default`）；下单扣库存的全局事务由它协调 |
| RocketMQ NameServer / Broker | 9876 / 10911 / 10909 / 10912 | `apache/rocketmq:5.3.2`；本机 NameServer、Broker 已运行，topic 初始化任务已完成；数据卷独立。Dashboard 未部署 |

核对命令：`wsl -- docker ps --format "table {{.Names}}\t{{.Ports}}"`

中文注释维护：Windows 和 Linux 均可运行 `python tools/apply-database-comments.py --apply`（Linux 通常使用 `python3`）。工具会根据当前数据库的完整列定义生成注释 SQL、备份结构并验证元数据及行校验和；`all_chinese_comments.sql` 与数据字典是最近一次生成时的结构快照，跨设备应用前应在目标设备重新生成，避免把字段类型差异带到另一台设备。

## 6. 服务间内部接口与共享密钥

| 调用方 → 被调方 | 接口 | 鉴权方式 |
|---|---|---|
| auth → user | `POST /users/authenticate` | 透传内部调用（Feign + 降级工厂） |
| order → user | `GET /users/{userId}` | 透传用户 JWT |
| order → product | `POST /internal/products/quote` | 透传用户 JWT（"代表用户"） |
| order → product | `GET /internal/windows/{windowId}`、`GET /internal/restaurants/{restaurantId}` | `X-Internal-Token`（"系统事件"） |
| user → product | 同上两个查询接口（校验范围引用是否真实存在） | `X-Internal-Token` |
| user → 内部 | `GET /internal/users/{userId}/scopes` | `X-Internal-Token` |
| payment → order | `GET /internal/orders/{orderId}`、`PATCH /internal/orders/{orderId}/paid` | `X-Internal-Token`（`INTERNAL_ORDER_TOKEN`） |
| **order → stock** | `POST /internal/stocks/deduct`、`POST /internal/stocks/restore`、`GET /internal/stocks/{windowId}/{productId}` | `X-Internal-Token`（`INTERNAL_SCOPE_TOKEN`）；业务失败（如库存不足）返回 **HTTP 200 + 失败业务码**，避免 Feign 降级把原因吞成 503 |
| 支付渠道 → payment | `POST /internal/payments/notify` | HMAC-SHA256 验签（`PAYMENT_CHANNEL_SECRET`） |

统一约定：透传用户 JWT = "代表用户"；`X-Internal-Token` = "系统事件"（无用户上下文），密钥变量 `INTERNAL_SCOPE_TOKEN`，请求头 `X-Internal-Token`。

## 7. 待建库 / 表清单（尚未创建）

### 7.1 已建服务范围内的缺失项 —— ✅ 2026-10-13 已全部补齐

| 待建对象 | 迁移脚本 | 状态 |
|---|---|---|
| `user_db.user_data_scopes`（表） | [user_db/V2](migrations/user_db/V2__create_user_data_scopes_table.sql) | ✅ 已执行 |
| `product_db.restaurants`（表）+ `dining_windows.restaurant_id`（列） | [product_db/V2](migrations/product_db/V2__create_restaurants_and_window_restaurant.sql) | ✅ 已执行（含默认餐厅 `R0001`） |
| `users.pay_password_hash`（列，余额支付独立密码） | [user_db/V3](migrations/user_db/V3__add_pay_password_and_balance_records.sql) | ✅ 新增脚本并已执行 |
| `balance_records`（余额流水表，含记账幂等唯一键） | [user_db/V3](migrations/user_db/V3__add_pay_password_and_balance_records.sql) | ✅ 新增脚本并已执行 |
| `restaurants` 补 `location`/`phone`/`image_url`/`notice`/`sort_order`（对照旧单体 canteen 的 floor/phone） | [product_db/V4](migrations/product_db/V4__enrich_restaurants.sql) | ✅ 新增脚本并已执行（不含 `business_hours`） |
| `dining_windows` 补 `image_url`/`is_open`（对照旧单体 windows 的 image_url/is_open） | [product_db/V5](migrations/product_db/V5__enrich_dining_windows.sql) | ✅ 新增脚本并已执行 |
| `dining_windows` 补窗口简介 `description` / 排序 `sort_order` | [product_db/V6](migrations/product_db/V6__add_window_description_and_sort_order.sql) | ✅ 2026-10-22 已在本机 MySQL 执行，字段及非负约束已核对，原报错窗口查询已验证成功 |

> 说明：支付密码校验失败的计数与锁定**不落库**，用 Redis 带 TTL 的计数器实现。余额支付已实现；2026-10-22 补充模拟充值和本人流水查询代码，复用 `users`、`balance_records`，无需额外迁移，接口与验证范围见 [账户中心与模拟充值](../WALLET_API.md)。

### 7.2 未建服务（阶段 B / C）的库 —— 部分已落地

以下库属于**尚未创建的服务**，等该服务开工时再建（新增服务的标准动作见第 8 节）：

| 库 | 表 | 归属服务 | 备注 |
|---|---|---|---|
| ~~`stock_db`~~ | ~~`stock`、`stock_flow`、`undo_log`~~ | stock-service | ✅ **2026-10-19 已建**，见第 2 节 |
| ~~`order_db.undo_log`（补表）~~ | ~~`undo_log`~~ | order-service | ✅ **2026-10-19 已补**（`migrations/V4__add_undo_log.sql`），Seata 参与库都要有 `undo_log` |
| `comment_db` | `comments`（仅关系元数据） | comment-service | 正文/图片/标签/回复入 **MongoDB**（集合 `comments`） |
| `comment_db` | `window_reviews` | comment-service | 已完成订单的店铺评价，按订单唯一；与菜品评价和菜品评分独立 |
| — | MongoDB 集合 `comments` | comment-service | 需先引入 MongoDB 容器（27017） |
| `cart_db`（取消） | 不建设购物车备份表 | cart-service | 2026-10-17 用户确认仅使用 Redis（AOF + 数据卷），不做 MySQL 双存储，也不做游客合并 |
| ES 索引（无 MySQL 库） | — | search-service | MySQL 为唯一事实来源，索引可随时重建；Elasticsearch Compose profile 已配置，尚未启动 |
| `notification_db`（可选） | 离线消息表 | notification-service | 离线消息需要落库才能补投 |
| 无库 | — | statistics-service | 只读聚合，经 Feign 从 order/product 拉取 |

### 7.3 需配套新增的中间件（与库同期）

| 中间件 | 端口 | 阶段 | 用途 |
|---|---|---|---|
| ~~Seata Server（TC）~~ | 9109（旧端口段 8094） | B | ✅ **2026-10-19 已落地**：下单扣库存的分布式事务协调者，注册到现有 Nacos（见第 5 节） |
| RocketMQ NameServer / Broker / Dashboard | 9876 / 10911 / 8180 | B | ✅ 2026-10-23 本机 NameServer/Broker 已启动并创建销量主题；Dashboard 未部署，业务消息端到端仍待验收 |
| MongoDB | 27017 | B | 评论内容 |
| Elasticsearch | 9200 | C | 菜单全文检索；本地 Compose 使用 `search` profile，尚未启动 |

> 目标状态：数据库 Schema 从阶段 A 的 **4 个** → 阶段 B 后 **7 个**（+`stock_db`、`comment_db`、`file_db`）→ 阶段 C 再加 ES 索引与（可选）`notification_db`。当前已建 **6 个**（含 `stock_db`），仅剩 `comment_db` + MongoDB 未落地。服务数从 6 → 13，见 [ARCHITECTURE.md](../ARCHITECTURE.md) 第 1、3 节。


## 8. 新增服务时的标准动作

新增服务时按 [ARCHITECTURE.md](../ARCHITECTURE.md) 第 10 节执行；就数据库而言固定是四件事：**独立 Schema + 独立最小授权账号（凭据进 `.env`）→ 幂等迁移脚本放入 `docs/database/migrations/<db>/` → 需要跨服务一致性时同步补 `undo_log` 或对账方案 → 回填本清单第 1、2 节与架构文档总表。**

