# Java 中文注释补齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `superpowers:executing-plans` 按任务完成；每个任务结束后进行相应模块验证。

**Goal:** 为所有生产 Java 源码补充准确、易读的中文类与方法注释，并说明关键业务步骤，不改变任何运行逻辑。

**Architecture:** 按共享组件、网关认证、用户订单、商品支付四个边界分批处理 `src/main/java`。类、接口、枚举使用中文 Javadoc；公共及非平凡方法说明用途、参数、返回值和异常；方法内部只在关键业务步骤、分支、并发、幂等、远程调用与异常转换处添加有解释价值的中文 `//` 注释。

**Tech Stack:** Java 21、Spring Boot、Spring Cloud Alibaba、Maven。

**Spec:** `docs/JAVA_CODING_STANDARDS.md`。

## Global Constraints

- 仅编辑 Java 注释及项目注释规范/进度文档；不得改动业务语句、签名、注解、导入、配置、端口或依赖。
- 注释必须与实际代码一致，不得声称代码尚未实现的校验、事务、锁、重试或幂等保证。
- 注释统一使用中文；技术名词、类名、方法名和协议名保留英文。
- 方法体注释解释业务意图、关键约束或容易误解的步骤，不逐行复述代码。
- 保留现有未提交的 `Dockerfile` 和 `docker-compose.yml` 修改，不触碰这两个文件。
- 不创建提交、不推送远程仓库。

## Review Focus

- 注释准确性：逐条对照实现，避免把注释写成比代码更强的承诺。
- 注释有效性：避免为 getter、赋值、显而易见的返回语句添加重复说明。
- 业务链路：检查支付状态变更、订单状态推进、权限范围、Token/Redis、远程调用及失败处理说明。
- 兼容性：确认改动未改变 Java 代码 token（注释以外）及 Maven 构建结果。
- 范围：确认只改生产 Java 源码与约定的文档，不顺带整理测试或配置。

---

### Task 1: 共享组件与远程契约

**Files:**
- Modify all Java files under `common-core/src/main/java`, `common-response/src/main/java`, `common-web/src/main/java`, `common-redis/src/main/java`, `user-api/src/main/java`, and `product-api/src/main/java`.
- Modify `docs/JAVA_CODING_STANDARDS.md` to make the requested comment-depth expectations explicit.

- [x] 补齐类型、公共方法和关键方法体的中文注释；保留既有行为。
- [x] 检查差异，确认业务代码无变化。
- [x] 运行上述模块的 Maven 测试。

### Task 2: Gateway 与认证服务

**Files:**
- Modify all Java files under `gateway-service/src/main/java` and `auth-service/src/main/java`。

- [x] 补齐中文类、方法及关键步骤注释，重点解释认证、JWT、刷新、注销、限流和安全失败路径。
- [x] 检查差异，确认业务代码无变化。
- [x] 运行上述模块的 Maven 测试。

### Task 3: 用户与订单服务

**Files:**
- Modify all Java files under `user-service/src/main/java` and `order-service/src/main/java`。

- [x] 补齐中文类、方法及关键步骤注释，重点解释权限、数据范围、余额、订单状态与幂等边界。
- [x] 检查差异，确认业务代码无变化。
- [x] 运行上述模块的 Maven 测试。

### Task 4: 商品与支付服务及全量检查

**Files:**
- Modify all Java files under `product-service/src/main/java` and `payment-service/src/main/java`。
- Modify `docs/PROJECT_PROGRESS.md` to record completed comment work.

- [x] 补齐中文类、方法及关键步骤注释，重点解释缓存、布隆过滤器、支付回调验签、状态条件更新与跨服务推进。
- [x] 检查全量差异，只包含注释与约定文档修改。
- [x] 运行全项目 `mvn test` 并记录实际结果。
