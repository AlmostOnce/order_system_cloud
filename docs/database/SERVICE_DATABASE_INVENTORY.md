# 服务与数据库清单（已落地现状）

> 文档性质：**现状清单**。回答两个问题：①我们现在一共有哪些服务；②每个服务对应哪个库、哪些表、哪个账号。
> 事实来源：根 `docker-compose.yml`、各服务 `application.yml`、`docs/database/migrations/**`、`backup/db-accounts-fix.sh`。
> 分工：**规划中**（阶段 B/C 未落地的服务与库）见 [ARCHITECTURE.md](../ARCHITECTURE.md) 第 3、8 节；本文只记"已经建好"的部分。
> 最后核对：2026-10-13（以 `wsl -- docker ps` 与本机工作区文件为准）。

## 1. 服务总表

| # | 服务 | Maven 模块 | 端口 | 独立数据库 | 依赖 Redis | 网关路由 | 状态 |
|---|---|---|---|---|---|---|---|
| 1 | `gateway-service` | `gateway-service` | 9090 | 无 | ✅（登录限流令牌桶） | — | ✅ |
| 2 | `auth-service` | `auth-service` | 9094 | 无 | ✅（Refresh Token + 黑名单 + 分布式锁） | `/api/auth/**` | ✅ |
| 3 | `user-service` | `user-service` | 9092 | `user_db` | ✅（范围缓存 + 黑名单） | `/api/users/**` | ✅ |
| 4 | `order-service` | `order-service` | 9091 | `order_db` | ✅（黑名单） | `/api/orders/**` | ✅ |
| 5 | `product-service` | `product-service` | 9093 | `product_db` | ✅（黑名单） | `/api/products/**` | ✅ |
| 6 | `payment-service` | `payment-service` | 9101 | `payment_db` | ✅（黑名单） | `/api/payments/**` | ✅ |

**公共库与契约库（不独立部署）**

| 模块 | 作用 | 依赖方 |
|---|---|---|
| `common-core` | 通用错误码、异常、角色枚举、`DataScopeService` 契约 | 全部服务 |
| `common-response` | `ApiResponse`（无 Servlet 依赖，供 WebFlux 用） | 全部服务 |
| `common-redis` | Redis 基础操作、缓存键常量 | gateway / auth / user / order / product / payment |
| `common-web` | 全局异常处理（仅 Servlet 服务） | auth / user / order / product / payment |
| `user-api` | `UserBasicVO` + `UserServiceClient` + 降级工厂 | auth / order |
| `product-api` | 报价契约、窗口/餐厅内部查询客户端 + 降级工厂 | order |

> 端口为**当前设备工作区**实际值（`9090` 段）。另一台设备使用的旧端口段见 [ARCHITECTURE.md](../ARCHITECTURE.md) 第 3、8.1 节；两套端口不要互相合并。

## 2. 数据库、表与迁移脚本

| 数据库 | 表 | 归属服务 | 迁移脚本（按执行顺序） |
|---|---|---|---|
| `order_db` | `orders`、`order_items` | order-service | [V1__create_orders_table.sql](migrations/V1__create_orders_table.sql) → [V2__add_order_idempotency.sql](migrations/V2__add_order_idempotency.sql) → [V3__create_order_items_table.sql](migrations/V3__create_order_items_table.sql) |
| `user_db` | `users`（含 `pay_password_hash`）、`user_data_scopes`、`balance_records` | user-service | [user_db/V1__create_users_table.sql](migrations/user_db/V1__create_users_table.sql) → [user_db/V2__create_user_data_scopes_table.sql](migrations/user_db/V2__create_user_data_scopes_table.sql) → [user_db/V3__add_pay_password_and_balance_records.sql](migrations/user_db/V3__add_pay_password_and_balance_records.sql) |
| `product_db` | `dining_windows`（含 `image_url`/`is_open`）、`product_categories`、`products`、`restaurants`（含 `location`/`phone`/`image_url`/`notice`/`sort_order`） | product-service | [product_db/V1__create_product_catalog_tables.sql](migrations/product_db/V1__create_product_catalog_tables.sql) → [product_db/V2__create_restaurants_and_window_restaurant.sql](migrations/product_db/V2__create_restaurants_and_window_restaurant.sql) → [product_db/V4__enrich_restaurants.sql](migrations/product_db/V4__enrich_restaurants.sql) → [product_db/V5__enrich_dining_windows.sql](migrations/product_db/V5__enrich_dining_windows.sql) |
| `payment_db` | `payments` | payment-service | [payment_db/V1__create_payments_table.sql](migrations/payment_db/V1__create_payments_table.sql) |

**演示/种子数据（仅开发环境）**：[seeds/product_catalog_dev.sql](seeds/product_catalog_dev.sql) —— 1 个档口、3 个分类（含 1 个停用）、5 个菜品（含未分类与下架示例）。

> ✅ **本机执行状态（2026-10-13）**：上表脚本**已全部在本机 MySQL 执行完毕**，实有 **4 库 10 表**：`order_db`(orders, order_items)、`user_db`(users, user_data_scopes, balance_records)、`product_db`(dining_windows, product_categories, products, restaurants)、`payment_db`(payments)；`dining_windows.restaurant_id` 与 `users.pay_password_hash` 两列已存在，默认餐厅 `R0001 默认餐厅` 已插入。剩余待建项（阶段 B/C）见第 7 节。

**表设计说明**：商品库见 [PRODUCT_CATALOG.md](PRODUCT_CATALOG.md)；订单幂等键约定见 [ORDER_IDEMPOTENCY.md](ORDER_IDEMPOTENCY.md)；`user_data_scopes` 的设计见 [2026-10-13-role-authority-design.md](../superpowers/specs/2026-10-13-role-authority-design.md)。

## 3. 数据库账号与授权

每个服务使用**只授予本库权限**的独立账号，凭据统一从 `.env`（不入 Git）注入。

| 账号 | 授权库 | 账号如何创建 | 密码变量 |
|---|---|---|---|
| `order_user` | `order_db` | **自动**：MySQL 容器入口脚本按 `MYSQL_USER`/`MYSQL_PASSWORD` 建号并授权 | `MYSQL_PASSWORD` |
| `user_service` | `user_db` | **需手工**：仓库无 SQL，仅在 [backup/db-accounts-fix.sh](../../backup/db-accounts-fix.sh) 中 | `USER_DB_PASSWORD` |
| `product_service` | `product_db` | **需手工**：同上 | `PRODUCT_DB_PASSWORD` |
| `payment_service` | `payment_db` | **迁移脚本内置**：`payment_db/V1` 中 `CREATE USER` + `GRANT` | `PAYMENT_DB_PASSWORD` |

> ⚠️ MySQL 账号存放在 `mysql` 系统库，**不随数据卷迁移**。换 Docker 引擎、重建系统库后只有 `order_user` 会自动重建，其余三个账号必须重新创建（见第 4.2 节）。

## 4. 新建环境（新建数据库）清单

### 4.1 按顺序执行

1. **前置工具链**：JDK 21、Maven 3.9.x（本地仓库可写）、Ubuntu-24.04（WSL2）内的 docker engine。
2. **外部密钥**：JWT RSA 密钥对放到 `E:\home\user\.config\order-system\keys\`（`jwt-private.pem` / `jwt-public.pem`）。服务 yml 写的是 `file:/home/user/.config/...`，Windows 下按当前工作目录所在盘符解析，因此 IDEA 的工作目录必须在 E 盘项目内。
3. **准备 `.env`**：复制 `.env.example`，填 MySQL、Redis、四个库账号密码、Nacos 账号与鉴权密钥、引导账号、`PAYMENT_CHANNEL_SECRET`、`INTERNAL_ORDER_TOKEN`。
4. **启动基础设施**：`docker compose up -d`（mysql / nacos / redis）。**禁止 `docker compose down -v`**，会删数据卷。
5. **建库与建账号**：执行 4.2 的 SQL（`order_db` 由容器自动创建，无需手工）。
6. **执行迁移**：按第 2 节顺序逐个执行迁移脚本（均为 `IF NOT EXISTS` 幂等写法，除 `product_db/V2` 的 `ALTER` 外可重复执行）。
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
| Sentinel Dashboard | 9099 | `order-service` 通信端口 `9100`，`product-service` 通信端口 `9102` |

核对命令：`wsl -- docker ps --format "table {{.Names}}\t{{.Ports}}"`

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

> 说明：支付密码校验失败的计数与锁定**不落库**，用 Redis 带 TTL 的计数器实现（临时状态，无需持久化）。`V3` 目前只建结构，余额充值与余额支付的**业务代码尚未实现**。

### 7.2 未建服务（阶段 B / C）的库 —— 仍未开始

以下库属于**尚未创建的服务**，等该服务开工时再建（新增服务的标准动作见第 8 节）：

| 库 | 表 | 归属服务 | 备注 |
|---|---|---|---|
| `stock_db` | `stock`、`stock_flow`、`undo_log` | stock-service | 库存量走 Redis Lua 原子扣减，流水落库供对账；`undo_log` 是 Seata AT 必需 |
| — | `order_db.undo_log`（补表） | order-service | Seata 参与库都要有 `undo_log`，随 stock 一起补 |
| `comment_db` | `comments`（仅关系元数据） | comment-service | 正文/图片/标签/回复入 **MongoDB**（集合 `comments`） |
| — | MongoDB 集合 `comments` | comment-service | 需先引入 MongoDB 容器（27017） |
| `file_db` | `file_objects` | file-service | 若改用对象存储且不需检索，可不建库 |
| `cart_db` | 购物车备份表 | cart-service | 主存储是 Redis，该库仅在 Redis 故障恢复时用；可延后 |
| ES 索引（无 MySQL 库） | — | search-service | MySQL 为唯一事实来源，索引可随时重建 |
| `notification_db`（可选） | 离线消息表 | notification-service | 离线消息需要落库才能补投 |
| 无库 | — | statistics-service | 只读聚合，经 Feign 从 order/product 拉取 |

### 7.3 需配套新增的中间件（与库同期）

| 中间件 | 端口 | 阶段 | 用途 |
|---|---|---|---|
| Seata Server（TC） | 9109（旧端口段 8094） | B | 下单扣库存的分布式事务，注册到现有 Nacos |
| RocketMQ NameServer / Broker / Dashboard | 9876 / 10911 / 8180 | B | 最终一致、ES 索引同步、站内消息 |
| MongoDB | 27017 | B | 评论内容 |
| Elasticsearch | 9200 | C | 菜单全文检索 |

> 目标状态：数据库 Schema 从现有 **4 个** → 阶段 B 后 **7 个**（+`stock_db`、`comment_db`、`file_db`）→ 阶段 C 再加 ES 索引与（可选）`notification_db`。服务数从 6 → 13，见 [ARCHITECTURE.md](../ARCHITECTURE.md) 第 1、3 节。


## 8. 新增服务时的标准动作

新增服务时按 [ARCHITECTURE.md](../ARCHITECTURE.md) 第 10 节执行；就数据库而言固定是四件事：**独立 Schema + 独立最小授权账号（凭据进 `.env`）→ 幂等迁移脚本放入 `docs/database/migrations/<db>/` → 需要跨服务一致性时同步补 `undo_log` 或对账方案 → 回填本清单第 1、2 节与架构文档总表。**

