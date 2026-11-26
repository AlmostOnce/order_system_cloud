# service 层审查问题修复报告

> 依据 `service-layer-audit.md` 的审查结论执行。共涉及 87 个文件（+1101 / -2841 行），另有 23 个新增文件。
> 未执行 Maven、未编译、未运行测试（项目约束），验证手段见第 5 节。

---

## 1. 删除旧榜单死代码链路

已删除（order-service，行为早已迁至 statistics-service）：

| 文件 | 说明 |
| --- | --- |
| `service/SalesRankingService.java` | 旧榜单实现，无 Spring 注解、非 Bean |
| `ranking/SalesRankingJobs.java` | 废弃对账任务 |
| `ranking/SalesRankingMessaging.java` | 与 `SalesOutboxPublisher` 完全重复的发布器（同 binding） |
| `mapper/SalesRankingMapper.java` | 仅被上述废弃类引用 |
| `model/PaidSalesLine.java` | 同上 |
| `resources/rankings/*-sales-v2.lua` | 3 个随包发布但永不加载的脚本 |
| `service/SalesRankingServiceTest.java`、`SalesRankingMessagingTest.java` | 对应测试 |

确认删除后全仓无残留引用（含测试）。

## 2. statistics-service 统一为接口 + impl

| 接口 | 实现 |
| --- | --- |
| `service/SalesProjectionService` | `service/impl/SalesProjectionServiceImpl` |
| `service/SalesRankingCacheService` | `service/impl/SalesRankingCacheServiceImpl` |
| `service/SalesStatisticsService` | `service/impl/SalesStatisticsServiceImpl` |

同时修掉审查观察点 1：`SalesStatisticsService` 不再注入 `SalesProjectionMapper`，月销售读取
（`listWindowMonthlySales` / `getWindowMonthlySales`）下沉到投影服务，编排层只做委派。
`@Scheduled` 随方法迁到实现类，`@Transactional` 边界不变。

## 3. support-service 统一为接口 + impl

| 原位置 | 现结构 |
| --- | --- |
| `service/impl/SupportImageService`（具体类） | `service/SupportImageService` + `service/impl/SupportImageServiceImpl` |
| `service/impl/SupportScopeService`（具体类） | `service/SupportScopeService` + `service/impl/SupportScopeServiceImpl` |
| `service/SupportAccountDeletionService`（具体类） | `service/SupportAccountDeletionService` + `service/impl/SupportAccountDeletionServiceImpl` |
| `service/impl/SupportTicketViewAssembler`（@Component 转换器） | `assembler/SupportTicketViewAssembler`，不再占用 impl 包 |

- 新增 `support/SupportRole`：集中 `GENERAL_MANAGER` / `RESTAURANT_MANAGER` / `SYSTEM_ADMIN`
  与 `MANAGEMENT_ACCESS` 放行表达式，`SupportManagementController` 的 `@PreAuthorize` 改为引用常量。
- 队列类型规则去重：`SupportManagementServiceImpl.page` 不再内联角色判断，改为调用
  `SupportScopeService.requireTypeFilterAllowed`，与详情路径共用同一套规则。
- `SupportImageService.parseKeys` 解析失败时补 warn 日志（原先注释称会告警但实际静默返回空列表）。

## 4. 非服务类迁出 service 包

| 原位置 | 现位置 |
| --- | --- |
| `order/service/CouponConsumeOutboxPublisher` | `order/coupon/`（与 ranking/notification/recommendation 的发布器组织方式一致） |
| `order/service/CouponConsumeOutboxWriter` | `order/coupon/` |
| `order/service/OrderCouponConsumeEvent` | `order/coupon/` |
| `order/service/CouponConsumeOutboxRow` | `order/model/` |
| `order/service/OrderPaymentDeadline` | `order/util/` |
| `order/service/RefundRules` | `order/util/`（状态字面量改为引用 `OrderStatus` 标签） |
| `file/service/FileContent` | `file/model/` |
| `product/service/WindowImageGallery` | `product/util/` |

迁移方式为"原文件删除 + 新位置重建"，逐行比对过原实现（`git show HEAD:`），逻辑未变；
相关测试同步迁到新包（`order/coupon`、`order/util`、`product/util`），断言内容保持原样。

另：`OrderAccountDeletionService` 由 `impl` 包中无接口的具体类拆为
`service/OrderAccountDeletionService` + `service/impl/OrderAccountDeletionServiceImpl`。

## 5. user-service 实现类命名对齐

- `service/impl/EmailVerificationService` → `EmailVerificationImpl`（含构造器、测试类）
- `service/impl/EmailChangeAuthorizationService` → `EmailChangeAuthorizationImpl`（含构造器、测试类）

确认无 `@Qualifier` / `@Resource` / 字符串形式的 Bean 名称依赖后执行。

## 6. 补齐 31 个文件的类级中文注释

按 AGENTS.md 要求为 service 层此前缺类级注释的文件补写职责、输入输出、事务与失败语义说明，
并在重要实现类补齐字段注释。覆盖 auth(4)、comment(9)、order(8)、payment(2)、product(2)、user(6)。

补充过程中同步修正：

- `CommentServiceImpl` 的日志类别由 `CommentService.class` 改为自身类。
- `OrderWindowNameServiceImpl`、`EmailOwnershipServiceImpl`、`PasswordRecoveryServiceImpl`、
  `PurchaseVerifierImpl`、`OrderReviewServiceImpl` 中硬编码的 `"COMMON_000"` 改为
  `CommonErrorCode.SUCCESS.code()`。

## 7. 两个 Outbox 发布器行为对齐

`SalesOutboxPublisher.publishPending` 改为与优惠券发布器一致：

- `markPublished` 返回值校验，未更新 1 行时按失败处理并退避重试（原先忽略返回值，会反复重发）；
- `retryLater` 二次 try-catch，退避写入失败只记错误日志，不中断本轮扫描。

---

## 8. 验证情况

已完成的静态验证：

1. **包声明与目录一致性**：9 个改动模块全部 java 文件（main + test）校验通过。
2. **`com.hbue.*` 导入可解析性**：全仓逐个 import 映射到真实类型文件，全部命中（含静态导入、内部类归属判断）。
3. **迁移类型的引用完整性**：19 个迁移/重命名类型的所有引用点均具备 import 或处于同包。
4. **旧包残留检查**：全仓不再出现 `order.service.CouponConsume*`、`order.service.RefundRules`、
   `order.service.OrderPaymentDeadline`、`file.service.FileContent`、`product.service.WindowImageGallery`、
   `service.impl.OrderAccountDeletionService` 等旧路径。
5. **service 层类级注释复查**：缺类注释文件数 = 0。
6. IDE 诊断（error 级）对改动目录无报错。

**未验证**：编译、单元测试、启动、MQ 与 Redis 联调。按项目约束不运行 Maven，需在 IDEA 中自行编译确认。

## 9. 本次未处理项（如需继续，请指定）

1. 其余 12 处 `"COMMON_000"` 硬编码（auth `AuthServiceImpl`、comment `AuthorAvatarServiceImpl`/`WindowReviewServiceImpl`、
   common-web 2 处、notification `ChatAccessServiceImpl`、order `OrderRefundServiceImpl`/`OrderRefundTask`、
   payment 3 处），其中 4 个文件还需补 import，且部分位于 service 层之外。
2. `SalesProjectionMapper.insertEventIfAbsent` 仍使用 `INSERT IGNORE`（会把截断等错误降级为 warning），
   改为捕获唯一键冲突需要确认对 `insertEventItems` 的影响。
3. `SalesProjectionService.addTotals` 与 `SalesRankingCacheService.group` 仍各自实现"同菜品汇总 + 取最新 itemId"，
   合并需改动两套持久化路径，风险高于收益，暂未动。
4. `SupportActor` / `SupportScope` 仍位于 `service` 包（审查结论为"可保留"），未迁 `model`。
5. `order-service/src/main/resources/rankings` 目录已空，未删除空目录。
