# 商品服务基础模块设计（初始阶段）

## 目标

在 `order-system-cloud` Maven 多模块工程中新增 `product-service` 基础模块，接入已创建的 `product_db`，建立窗口、分类和菜品的持久化映射，并注册到 Nacos。本阶段不实现业务流程或 HTTP API。

## 当前背景

- 根工程使用 Java 21、Spring Boot 3.5.6 和 Spring Cloud Alibaba 2025.0.0.0。
- `product_db` 已有 `dining_windows`、`product_categories`、`products` 三张表；菜品及分类均归属单个窗口。
- `product-service` 只访问自己的 `product_db`，数据库账号通过环境变量提供。
- 现有业务服务采用 Maven 子模块、Nacos Discovery 和 MyBatis-Plus。

## 方案

- 在根 Maven reactor 登记 `product-service`，创建 `com.hbue.ordering.product.ProductServiceApplication`。
- 添加 Spring Boot Web、Nacos Discovery、MyBatis-Plus 和 MySQL 驱动依赖，本机服务端口使用 `8086`。
- 配置 `PRODUCT_DB_URL`、`PRODUCT_DB_USERNAME`、`PRODUCT_DB_PASSWORD` 和 Nacos 环境变量；不在 Java 代码中保存凭据。
- 为三张商品库表建立 DO 和 MyBatis-Plus Mapper，显式映射数据库字段，并由 `@MapperScan` 注册。
- 本机 MySQL 创建 `product_service` 账号，仅授予 `product_db` 的查询、写入、更新和删除权限。

## 明确不包含

- 不创建 Controller、HTTP API、Gateway 路由或服务间调用契约。
- 不创建业务 Service、DTO、VO，不实现商品管理或查询流程。
- 不接入 Redis、库存和订单业务；`order_items` 仍由 `order-service` 所有。
- 不修改 Docker Compose 服务清单。

## 验收标准

1. 根 Maven reactor 能识别 `product-service`。
2. 服务包含启动类、数据源配置、三个 DO 和三个 Mapper，不包含 Controller。
3. 商品库账号能够连接 `product_db`，无权访问其他业务库。
4. 执行 `mvn -pl product-service -am -DskipTests package` 成功。

## 后续阶段实现记录（2026-09-28）

初始骨架完成后，已继续实现最小下单核价闭环：

- 新增 `product-api`，定义商品批量核价请求、响应和 Feign 客户端。
- `product-service` 新增受 CUSTOMER JWT 保护的 `POST /internal/products/quote`，检查窗口状态、菜品所属窗口和在售状态。
- `order-service` 改为接收菜品 ID 与数量，由商品服务提供当前价格；服务端计算金额，并在同一事务内保存订单及菜品快照。
- 同一幂等键的请求摘要包含窗口、规范化后的菜品 ID/数量和备注；重试返回初次创建的订单价格快照。

下一阶段再实现面向菜单浏览和管理的商品接口，并验证完整 Gateway 下单链路。
