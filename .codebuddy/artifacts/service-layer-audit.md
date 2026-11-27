# service 层审查报告（全模块）

> 审查对象：`e:/order_system_cloud` 全部 26 个 Maven 模块中 `src/main/java/**/service/**` 下的 181 个 Java 文件。
> 审查方式：静态扫描（类型、Spring 注解、事务注解、类级中文注释）+ 关键实现逐行阅读。
> 未执行 Maven、未编译、未启动服务（遵守项目构建约束），以下结论均来自源码静态证据。

---

## 一、结论速览

| 级别 | 问题类别 | 数量 | 是否影响运行 |
| --- | --- | --- | --- |
| 高 | 会导致启动失败 / 数据错乱的缺陷 | 0 | — |
| 中 | 分层与命名不一致（两种风格并存、非实现类放 `impl`、配置类放 `service`） | 6 组 | 不影响，但持续腐化可读性 |
| 中 | 死代码与迁移残留（旧榜单链路 + v2 Lua 脚本） | 1 条链 | 不影响，但会误导维护者 |
| 中 | 类级中文注释缺失（违反 AGENTS.md 注释要求） | 31 个文件 | 不影响运行 |
| 低 | 魔法字符串 / 规则重复实现 | 4 处 | 有漂移风险 |
| 低 | 静默吞异常、`INSERT IGNORE` 语义 | 3 处 | 可观测性下降 |

**总体判断**：service 层的事务边界与幂等设计是健康的，写路径均有事务包裹或明确交由上层事务；没有发现"多表写入无事务"或"`@Transactional` 标在私有方法/自调用"这类典型缺陷。主要问题是**风格不统一和历史残留**，属于可维护性问题而非功能性故障。

---

## 二、分层与命名不一致（中）

### 2.1 两种服务层风格并存

项目整体采用 `service` 接口 + `service.impl` 实现，但存在两组"只有具体类、没有接口"的服务：

- `statistics-service`：`SalesProjectionService`、`SalesRankingCacheService`、`SalesStatisticsService` 全部是 `@Service` 具体类，**整个模块没有 `service/impl` 目录**。
- `support-service`：`SupportAccountDeletionService` 是 `service` 包下的具体类；`impl` 包内又混入 `SupportImageService`、`SupportScopeService`（具体类）与 `SupportTicketViewAssembler`（`@Component`）。

同时 `support-service` 的 `SupportManagementService` / `SupportTicketService` 又严格走接口 + `impl`。

结论：同一个模块内三种写法共存（接口+impl、`service` 下具体类、`impl` 下具体类），没有统一约定。建议二选一并写进 AGENTS.md，否则新代码会继续随机选边。

### 2.2 `impl` 包中的非实现类

| 文件 | 实际情况 | 问题 |
| --- | --- | --- |
| `support/.../service/impl/SupportImageService.java` | `@Service` 具体类，无接口 | 名字带 Service 且放在 impl，但没有任何接口 |
| `support/.../service/impl/SupportScopeService.java` | `@Service` 具体类，无接口 | 同上，且承担权限判定这类核心规则 |
| `support/.../service/impl/SupportTicketViewAssembler.java` | `@Component`，纯转换器 | 转换器不应放在 `service.impl` |
| `order/.../service/impl/OrderAccountDeletionService.java` | `@Service` 具体类，无接口 | 与同名 `SupportAccountDeletionService` 命名一致，但一个在 `impl` 一个在 `service` |

### 2.3 接口与实现类命名不匹配（user-service）

- `EmailChangeAuthorization` → 实现类名为 `EmailChangeAuthorizationService`（不是 `...Impl`）
- `EmailVerification` → 实现类名为 `EmailVerificationService`
- `EmailCodeSender` → `SmtpEmailCodeSender`（这组是合理的多实现命名，可保留）

前两组会让人误以为"`XxxService` 是接口、`XxxServiceImpl` 是它的实现"。

### 2.4 `service` 包中的非服务类型

| 文件 | 实际类型 | 建议归属 |
| --- | --- | --- |
| `file/.../service/FileContent.java` | 返回值载体（字节 + 类型 + 文件名） | `model` / `vo` |
| `order/.../service/CouponConsumeOutboxRow.java` | 数据库行模型 | `model` |
| `order/.../service/OrderCouponConsumeEvent.java` | MQ 事件载荷 | `dto` 或 `coupon` 包 |
| `order/.../service/OrderPaymentDeadline.java` | 纯静态工具（私有构造） | `support` / `util` |
| `order/.../service/RefundRules.java` | 纯规则计算 | `support`（与 `RefundRules` 相关的 `OrderRefundServiceImpl` 同层亦可） |
| `product/.../service/WindowImageGallery.java` | 全静态工具类（私有构造，被静态调用） | `support` / `util` |
| `support/.../service/SupportActor.java`、`SupportScope.java` | 上下文 record | 可保留（有完整注释），或移到 `model` |

### 2.5 `service` 包中的配置类（唯一一处）

`order/.../service/CouponConsumeOutboxPublisher.java` 标注 `@Configuration` + `@EnableScheduling`，实际是一个 `@Scheduled` 消息发布器。

对比同类实现，order-service 的发布器分别放在 `messaging`、`notification`、`ranking`、`recommendation` 包下，product-service 放在 `messaging` 包下（`ProductSearchOutboxPublisher`）。只有优惠券核销这一个落在 `service` 包，且顺带把 `CouponConsumeOutboxRow`、`OrderCouponConsumeEvent` 也带了进来。建议整体迁到 `order/coupon/` 或 `order/messaging/`。

另注：`@EnableScheduling` 在 `CouponConsumeOutboxPublisher` 与 `messaging/OrderTimeoutMessaging` 上各出现一次，重复开关调度但无害；集中到启动类或配置类更清晰。

---

## 三、死代码与迁移残留（中）

订单榜单已迁往 `statistics-service`，但 order-service 仍保留完整旧链路：

- `service/SalesRankingService.java`：`@Deprecated`，**无任何 Spring 注解**，不是 Bean。
- `ranking/SalesRankingJobs.java`：`@Deprecated`，无注解，`reconcile()` 只转调上面的服务。
- `ranking/SalesRankingMessaging.java`：`@Deprecated`，无注解，内含与 `SalesOutboxPublisher` **完全重复**的 `publishPending()`（同样发 `salesPaid-out-0`、同样校验载荷、同样退避重试）。
- `mapper/SalesRankingMapper.java`、`model/PaidSalesLine.java`：仅被上述废弃类引用，生产代码零调用。
- `src/main/resources/rankings/*.lua`（`apply-paid-sales-v2.lua`、`read-sales-v2.lua`、`rebuild-sales-v2.lua`）：随 order-service 一起打包，运行时永不加载。

现状是"靠注释声明停用 + 靠不写注解保证不注册"。这是可工作的，但代价是：任何人给 `SalesRankingMessaging` 补一个 `@Component` 就会重新产生一个和 `SalesOutboxPublisher` 抢同一 binding 的发布器。建议把测试用例改到 `SalesOutboxPublisher` 后整体删除这条链路。

---

## 四、类级中文注释缺失（中，违反 AGENTS.md）

AGENTS.md 明确要求"类、接口、方法和重要字段解释职责"，以下 31 个文件**类级 Javadoc 完全缺失**（不含仅有行内注释的情况）：

**auth-service（4）**
`service/EmailOwnershipService.java`、`service/PasswordRecoveryService.java`、`service/impl/EmailOwnershipServiceImpl.java`、`service/impl/PasswordRecoveryServiceImpl.java`

**order-service（8）**
`service/OrderPickupService.java`、`service/OrderTimeoutOutboxService.java`、`service/OrderTimeoutService.java`、`service/OrderWindowNameService.java`、`service/impl/OrderPickupServiceImpl.java`、`service/impl/OrderTimeoutOutboxServiceImpl.java`、`service/impl/OrderTimeoutServiceImpl.java`、`service/impl/OrderWindowNameServiceImpl.java`

**comment-service（9）**
`service/AuthorAvatarService.java`、`service/OrderReviewService.java`、`service/RatingBatchService.java`、`service/WindowReviewService.java`、`service/impl/CommentServiceImpl.java`、`service/impl/OrderReviewServiceImpl.java`、`service/impl/PurchaseVerifierImpl.java`、`service/impl/RatingBatchServiceImpl.java`、`service/impl/WindowReviewServiceImpl.java`

**user-service（6）**
`service/EmailOwnershipService.java`、`service/PasswordRecoveryService.java`、`service/PayPasswordRecoveryService.java`、`service/UserWalletService.java`、`service/impl/PasswordRecoveryServiceImpl.java`、`service/impl/UserUuidLookup.java`

**payment-service（2）**
`service/PaymentOrderGateService.java`、`service/impl/PaymentOrderGateServiceImpl.java`

**product-service（2）**
`service/WindowCatalogService.java`、`service/impl/WindowCatalogServiceImpl.java`

其中 `OrderTimeoutOutboxServiceImpl` 除缺类注释外，方法也全无注释，且承担 `Propagation.MANDATORY` 这种必须解释的事务语义——它依赖 `OrderPersistenceServiceImpl` 已开启事务，一旦被直接调用就会抛 `IllegalTransactionStateException`，这个约束目前只能靠读注解猜。

---

## 五、事务与一致性核对结果（无缺陷，附观察点）

**已核对正确的部分**：

- 所有真正写库的 service 都有事务：`FileStorageServiceImpl`、`OrderPersistenceServiceImpl`（含 `timeoutOutbox/couponConsumeOutbox` 的 `MANDATORY` 传播）、`OrderRefundServiceImpl`、`OrderTimeoutOutboxServiceImpl`、`RecommendationPurchaseOutboxWriterImpl`、`RecommendationPurchaseSnapshotQueryServiceImpl`、`ProductAdministrationServiceImpl`、`ProductQuoteServiceImpl`、`ProductRecommendationSourceServiceImpl`、`ProductSearchSourceServiceImpl`、`ProductSpecificationServiceImpl`、`RestaurantAdministrationServiceImpl`、`SalesProjectionService`、`SupportManagementServiceImpl`、`SupportTicketServiceImpl`。
- 未标事务的实现类确实是只读或只做远程编排：`ProductStockServiceImpl`、`ProductMenuServiceImpl`、`WindowCatalogServiceImpl`、`OrderWindowNameServiceImpl`、`SupportImageService`、`SalesRankingCacheService`（Redis）、`OrderServiceImpl`（写操作全部下沉到 `OrderPersistenceService`）。
- `auth-service` 没有 Mapper、没有本地库，全部状态在 Redis + Feign，**因此无 `@Transactional` 是正确设计**，不属于遗漏。
- 全仓未发现 `@Transactional` 标在 `private`/`protected` 方法上（代理失效）的写法。
- `SalesProjectionService.verifyDuplicate` 按下标逐行比对既有明细，其安全性依赖 `SalesProjectionMapper.selectEventItems` 的 `ORDER BY item_id`——已确认该 SQL 带排序，比对逻辑成立。

**观察点（低）**：

1. `statistics-service` 的 `SalesStatisticsService` 既做编排又直接注入 `SalesProjectionMapper` 查月销售（`listWindowMonthlySales`、`getWindowMonthlySales`），而同模块已有 `SalesProjectionService` 专职投影读写。建议把读查询也收进投影服务，避免同一模块两种数据访问入口。
2. `SalesProjectionMapper.insertEventIfAbsent` 使用 `INSERT IGNORE`。它能优雅解决并发判重，但 MySQL 的 `INSERT IGNORE` 同时会把数据截断、非法值等错误降级为 warning，可能掩盖脏数据；如需更严格，可改为捕获唯一键冲突。
3. `SupportImageService.parseKeys` 注释写"历史数据损坏时返回空列表并记录由调用方决定是否告警"，实现只返回空列表，**没有任何告警**；图片健损坏时前端会静默无图。
4. `SalesOutboxPublisher.publishPending` 与 `CouponConsumeOutboxPublisher.publishPending` 行为不一致：前者未校验 `markPublished` 的影响行数，且 `retryLater` 失败会让整个扫描中断；后者已对 `retryLater` 做二次 try/catch。

---

## 六、魔法字符串与规则重复（低）

1. **角色字符串硬编码 3 处**：`SupportScopeService`、`SupportManagementServiceImpl`、`SupportManagementController` 各自写字面量 `"SYSTEM_ADMIN"` / `"RESTAURANT_MANAGER"` / `"GENERAL_MANAGER"`，没有共享常量或枚举。角色改名时需要全仓搜索，且编译器不会报错。
2. **"系统管理员只能看软件工单"这条规则实现了两遍**：`SupportScopeService.requireTypeAllowed`（详情/操作路径）与 `SupportManagementServiceImpl.page` 第 155 行（队列路径）。两处目前一致，但任何一侧调整都会造成"列表能看见、点进去 403"。
3. **`OrderWindowNameServiceImpl` 硬编码业务码** `"COMMON_000"`，同仓其余代码统一用 `CommonErrorCode.SUCCESS.code()`。业务码一旦调整，这里会静默进入"名称暂不可用"分支而不报错。
4. **`SalesProjectionService` 与 `SalesRankingCacheService` 各自实现了一份"同订单同菜品汇总 + 取最新 itemId"的分组逻辑**（`addTotals` / `group`）。两者目标不同（MySQL 汇总 vs Redis 增量），但 `latest itemId` 取舍规则必须永远一致，属于隐性耦合。

---

## 七、建议整改顺序

1. **先删死代码**（零风险，收益最大）：`SalesRankingService`、`SalesRankingJobs`、`SalesRankingMessaging`、`SalesRankingMapper`、`PaidSalesLine`、`resources/rankings/*.lua`，同时把 `SalesRankingMessagingTest` 的用例迁到 `SalesOutboxPublisher`。
2. **把 `CouponConsumeOutboxPublisher` 迁出 `service` 包**，与 `notification`/`ranking`/`recommendation` 对齐，并抽出角色常量。
3. **统一服务层风格**并写进 AGENTS.md，二选一：全接口化（statistics/support 补接口 + `impl`），或全具体类化（新模块不再建接口）。
4. **补齐 32 个文件的类级中文注释**，优先 `OrderTimeoutOutboxServiceImpl`（`MANDATORY` 传播语义）和 comment-service 的 9 个文件。
5. 低优先级：把 `FileContent`、`CouponConsumeOutboxRow`、`OrderCouponConsumeEvent`、`OrderPaymentDeadline`、`RefundRules`、`WindowImageGallery` 移出 `service` 包。

---

## 八、未覆盖项（如实说明）

- 未运行 Maven、未编译、未跑测试，因此"A 文件引用的 B 类是否存在、注解是否生效"只做了源码级确认，未经验证。
- 未逐行审查 181 个文件的全部方法体，只对事务、幂等、分层归属、消息发布等关键路径做了针对性阅读；`payment-service`、`coupon-service`、`stock-service`、`search-service` 的 service 层仅完成类型/注解/注释扫描，业务逻辑细节未深入。
