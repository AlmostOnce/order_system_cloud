# order-system-cloud 架构与模块规划

> 文档性质：**定稿规划**（回答"我们一共要建多少个模块/服务"的唯一依据）。
> 与进度日志的分工：`PROJECT_PROGRESS.md` 记录"已经做完了什么"（追加式）；
> 本文档记录"打算建成什么样"（可修订，修订须更新第 13 节变更记录）。
>
> **立项前提（决定本文档所有判据）**：本项目是**学习项目**，目标是练到尽可能多的
> 分布式技术（分布式事务、最终一致、ES 检索、WebSocket 长连接、缓存热点、NoSQL）。
> "业务上是否需要"不是首要标准，"能学到什么"才是。**技术本身就是立项理由。**

## 1. 结论

| 维度 | 现状 | 阶段 B 完成后 | 阶段 C 完成后 |
|---|---|---|---|
| 可部署服务 | 9 个 | **10 个** | **13 个**（上限） |
| Maven 模块 | 17 个 | 约 19 个 | 约 21 个（服务 + 公共库 4 + 按需契约模块） |
| 数据库 Schema | 6 个 | + `comment_db` | + ES 索引、（可选）`notification_db` |

- **阶段 A 已有 6 个**：`gateway` / `auth` / `user` / `order` / `product` / `payment`
- **阶段 B 已落地 3 个**：`cart`（Redis 增删改查、商品实时核价、订单成功后的快照清理）、`stock`（Seata AT + Lua 原子扣减）、`file`（上传与存储抽象）
- **阶段 B 剩余 1 个**：`comment`（混合存储）
- **阶段 C（技术扩展，3 个）**：`notification`（WebSocket）/ `search`（ES）/ `statistics`
- **并入不拆**：餐厅层级 → `product-service`；订单超时取消 → `order-service`；余额流水 → `user-service`
- **中间件选型**：**RocketMQ（+ Spring Cloud Stream）**、**Seata**、**Elasticsearch** —— 见第 6 节

**唯一被否决的候选**：`restaurant-service` —— 不是因为它"业务上不重要"，
而是因为它**学不到任何新技术**（见第 4.3 节）。

## 2. 判定准则

拆一个服务，看它能否满足下列准则；**准则 1 是第一优先，其余为约束**。

1. **技术学习价值（第一优先）**：拆开后能练到已有服务练不到的技术吗？
   （分布式事务 / 最终一致 / 检索 / 长连接 / NoSQL / 缓存热点 / 消息可靠性）
   能 → 加；练不到 → 不加。
2. **风险可收敛**：它引入的复杂度必须有明确方案。高风险 + 有方案 → 加；
   高风险 + 无方案 → **先设计方案再加**（禁止"先建服务，一致性以后再说"）。
3. **依赖方向成 DAG**：新增依赖不得形成环（A 调 B、B 又调回 A）。
4. **可分期**：能排进阶段计划，不与当前主线冲突。

**唯一仍然禁止的**：拆开后只多一跳网络、学不到任何新技术。服务数量本身不是评分项，
**通过学习价值筛选出来的数量才是**。

## 3. 服务总表

端口列格式：`9090 段 / 当前设备旧端口段`。

| # | 服务 | 端口 | 存储 | 阶段 | 能练到的技术 |
|---|---|---|---|---|---|
| 1 | `gateway-service` | 9090 / 8080 | 无 | A ✅ | 路由、限流、认证前置（WebFlux） |
| 2 | `auth-service` | 9094 / 8084 | Redis | A ✅ | JWT 签发/刷新轮换、黑名单、Redisson 分布式锁 |
| 3 | `user-service` | 9092 / 8083 | `user_db` | A ✅ | RBAC + 数据范围 |
| 4 | `order-service` | 9091 / 8081 | `order_db` | A ✅ | 状态机、乐观锁、幂等键、定时任务 |
| 5 | `product-service` | 9093 / 8086 | `product_db` | A ✅ | 目录层级、软停用 |
| 6 | `payment-service` | 9101 / 9101 | `payment_db` | A ✅ | 渠道隔离、HMAC 验签、回调幂等 |
| 7 | `stock-service` | 9104 / 8089 | `stock_db` + Redis | B ✅ | **Seata AT 分布式事务**、Lua 原子扣减、对账补偿 |
| 8 | `cart-service` | 9103 / 8088 | 仅 Redis（AOF + 数据卷） | B ✅ | Redis Hash、Lua 并发修改与 TTL；仅登录顾客使用 |
| 9 | `comment-service` | 9105 / 8090 | MongoDB（内容）+ `comment_db`（关系元数据） | B ⬜ | **混合存储**、跨服务校验链、读写分离的缓存热点、评分聚合 |
| 10 | `file-service` | 9102 / 8087 | `file_db` | B ✅ | 多部分上传、存储抽象、横切复用 |
| 11 | `notification-service` | 9107 / 8092 | 可选 `notification_db` | C ⬜ | **WebSocket 长连接**、离线消息、消息可靠投递 |
| 12 | `search-service` | 9106 / 8091 | ES 索引 | C ⬜ | **ES 全文检索**、索引同步（RocketMQ）、重建 |
| 13 | `statistics-service` | 9108 / 8093 | 无（只读聚合） | C ⬜ | 读模型 / CQRS 雏形、Feign 扇出聚合、报表 |

> `auth-service` 的分拆**有代价**：登录链路多一跳 Feign，跨服务错误映射需额外处理
> （曾因此修复 401/503 映射）。它的学习价值在于令牌策略独立演进。

## 4. 三个待定服务的详细分析

### 4.1 `comment-service` —— **建议建**（收益高、风险低）

| 技术点 | 说明 |
|---|---|
| 跨服务校验链 | 评论前必须校验"该订单已完成且属于本人"：`comment → order`（可用 Feign + Sentinel 降级）；这是"下游只读依赖"的典型 DAG，不是环 |
| 读写比悬殊 | 评论是极端的读多写少，正好承接 `PROJECT_PROGRESS.md` 已规划的缓存防护（穿透/击穿/雪崩 + 布隆过滤器），是**缓存练习的最佳载体** |
| 评分聚合 | 菜品平均分/评论数属于读模型冗余字段，写入后异步更新 → 练"写后异步 + 最终一致" |
| **混合存储（已定）** | 评论**内容**（正文/图片/标签/回复）入 **MongoDB**；**关系元数据**（评论 ID、订单 ID、用户 ID、菜品 ID、评分、时间）留 **MySQL**。理由与写入顺序见 6.7 |

- **一致性风险：低**。评论写失败不影响交易，最终一致即可收敛。
- **结论：建**。三者中收益/风险比最好。

### 4.2 `stock-service` —— **建议建，用 Seata AT 做主方案**

| 技术点 | 说明 |
|---|---|
| 分布式事务主战场 | 下单 = `order-service` 建单 + `stock-service` 扣减，**跨服务**。这是本项目唯一需要"回滚"语义的场景（库存被扣了但建单失败，库存必须还回去） |
| Redis Lua 原子扣减 | 防超卖是硬技能；脚本原子性 + 库存流水 |
| 对账兜底 | 除 Seata 全局回滚外，仍保留库存流水 + 定时对账（TC 故障时的最后防线） |
| 方案对比 | 主方案 **Seata AT**；对比方案 **本地消息表 / 可靠消息**（见 6.4） |

- **一致性风险：高**。若只建服务不定方案，结果是超卖或库存对不上，且难以收敛。
- **必须配套**：
  1. 参与库（`order_db`、`stock_db`）建 `undo_log` 表；
  2. 下单方法加 `@GlobalTransactional`，由 OpenFeign 自动传播 `XID`；
  3. 库存流水 + 定时对账作为**兜底**保留；
  4. 完成后用"本地消息表"再实现一遍同一场景，做方案对比（学习重点）。
- **结论：建**。但**不能"建个服务就完"**，第 9 节的风险表必须同时落地。

### 4.3 `restaurant-service` —— **不建议建**（并入 `product-service`）

| 理由 | 说明 |
|---|---|
| 表少且低频 | 只有 `restaurants` + `dining_windows.restaurant_id`，变更频率极低 |
| 强绑定档口 | 查档口必须知道所属餐厅 → `product-service` 每次都要 Feign 拿餐厅，高频调用 + 必须加缓存 |
| **学不到新技术** | 不涉及分布式事务、不涉及高并发、不涉及检索或长连接，拆开**只是多一跳网络**（命中第 2 节唯一禁止条款） |
| 真正的价值在别处 | "按餐厅维度看经营数据"是 `statistics-service` 的读模型问题，不需要餐厅实体独立成服务 |

- **结论：不建**。这是三个里唯一建议不拆的。

### 4.4 你在上一轮明确要的另外三项（已纳入规划）

| 服务 | 定位 | 说明 |
|---|---|---|
| `cart-service` | 阶段 B | 购物车是学习 Redis 数据结构的标准场景（Hash 存明细、登录/未登录合并、TTL、与下单衔接） |
| `search-service`（ES） | 阶段 C | 按你的意见放稍后。技术点：索引与 MySQL 的同步（**RocketMQ 消息驱动**）、分词、重建索引 |
| `notification-service`（Web） | 阶段 C | 用 WebSocket/SSE 做站内消息，不接短信。技术点：长连接、网关 WS 路由、离线消息与可靠投递 |

## 5. 非服务模块

| 类别 | 模块 | 作用 |
|---|---|---|
| 公共库（4） | `common-core` | 通用常量、领域枚举、`DataScopeService` 契约 |
| | `common-redis` | Redis 基础操作、缓存键常量 |
| | `common-response` | `ApiResponse`（无 Servlet 依赖，供 Gateway WebFlux 使用） |
| | `common-web` | 全局异常处理（仅 Servlet 服务） |
| 契约库（3 → 按需） | `user-api` | `UserBasicVO` + `UserServiceClient` |
| | `product-api` | 报价契约 + `ProductServiceClient` |
| | `order-api` | 订单详情的购买事实只读投影 + `OrderServiceClient`，供购物车核实下单结果 |
| | `xxx-api` | **当且仅当**该服务被别的服务经 Feign 调用时才建（如 `stock-api`、`comment-api`） |

**规则**：契约模块按"被 Feign 调用"才建。只被前端调用、不被服务调用的服务（如 `cart`、`file`），
不建契约模块。

## 6. 中间件与分布式事务选型

### 6.1 选型总表

| 中间件 | 选型 | 用途 | 阶段 | 状态 |
|---|---|---|---|---|
| 消息队列 | **RocketMQ 5.x** + **Spring Cloud Stream**（Binder 抽象） | 事件驱动、事务消息、最终一致 | B | ⬜ 待引入 |
| 分布式事务 | **Seata**（AT 模式） | `order × stock` 下单扣库存 | B | ⬜ 待引入 |
| 搜索引擎 | Elasticsearch 8.x | 菜单全文检索 | C | ⬜ 待引入 |
| 文档库 | **MongoDB 7.x** | 评论**内容**（正文/图片/标签/回复），**非全量评论**（元数据仍在 MySQL） | B | ✅ 已选定 |
| 注册/配置 | Nacos | 服务发现与配置 | A | ✅ 已有 |
| 熔断限流 | Sentinel | 保护参与方，防雪崩（**非事务组件**） | A | ✅ 已有 |
| 缓存 | Redis | 缓存、分布式锁、库存扣减 | A | ✅ 已有 |

### 6.2 为什么是 RocketMQ + Spring Cloud Stream

| 决策 | 理由 |
|---|---|
| **具体产品选 RocketMQ** | ① 国内企业事实标准（阿里开源，电商/交易类系统默认选择）；② 与 Nacos/Sentinel/Seata 同属 SCA 生态；③ **只有 RocketMQ 原生支持「事务消息」（半消息 + 回查）** —— Kafka 与 RabbitMQ 都没有，而"事务消息"正是场景 ② 的解法（见 6.4） |
| **接入用 Spring Cloud Stream** | 业务代码面向 **Binder** 编程，**不绑定具体 MQ 实现**；将来若要换 Kafka，只改依赖与配置、不动业务代码。这层"抽象 vs 实现"本身就是学习点 |
| **备选/降级路径** | 直连 `rocketmq-spring-boot-starter`：更简单、国内更常见、出问题好排查。若 Stream binder 与 SCA 2025 版本不兼容，**降级为直连 starter，业务设计不变** |
| 版本提醒 | RocketMQ 5.x；Stream 的 RocketMQ binder 版本需在实施时确认，**不要照抄旧版教程** |

### 6.3 为什么是 Seata（AT 模式）

- **Spring Cloud 官方没有分布式事务组件**；官方的立场是"尽量避免分布式事务，改用事件驱动"。
  能对上的"Spring Cloud 元素"只有 SCA 家族里的 **Seata**。
- **四种模式选 AT**：无侵入（自动记录前后镜像 + 全局锁）、兼容 MySQL + MyBatis-Plus，
  是最该先掌握的一种。
  TCC（手写 Try/Confirm/Cancel）留作进阶；SAGA（长流程编排）我们链路太短；XA 性能差，了解即可。
- **只用在"需要回滚"的场景**（见 6.4）——这是选型的唯一判据。
- **依赖既有组件**：全局事务 `XID` 由 **OpenFeign 拦截器**自动传播给参与方；
  **Seata Server（TC）注册到现有 Nacos**，不必另装注册中心。

### 6.4 分布式事务场景归属（三个场景，两种方案）

| # | 场景 | 服务对 | 需要"回滚"吗 | 方案 |
|---|---|---|---|---|
| ① | 下单扣库存 | `order × stock` | **需要**（先占库存，失败必须还） | **Seata AT**：`@GlobalTransactional` + `undo_log` 表 + 对账兜底 |
| ② | 余额支付扣款 | `payment × user` | 不需要（单向、幂等、`WHERE balance >= ?` 条件更新） | **RocketMQ 事务消息** + 幂等消费 + 定时对账；**不用 Seata** |
| ③ | 支付成功推进订单 | `payment × order` | 不需要（已有幂等回调 + 重试） | 保持现状；阶段 B 起改为**异步事件**（RocketMQ） |

**判据一句话**：这个操作"先占了资源、后面可能失败要还回去"吗？是 → Seata；不是 → 最终一致。

**关键设计约束**：余额的**账务**（改 `users.balance` + 写 `balance_records`）留在 `user-service`
同库同事务；`payment-service` 只做**支付编排**（把余额当作一个 `PaymentChannel` 实现）。
即 `payment → user` 走内部接口调用，**不跨服务做分布式事务**。

**余额支付必须使用独立的「支付密码」（已定）**：不复用登录密码。理由——账号被盗时，
登录态窃取不应直接导致余额被花掉，支付需要第二道凭据。

- 独立存储：`users.pay_password_hash`（加盐哈希，与 `password_hash` 完全分离）；
- 设置在余额首次使用（充值 / 余额支付）前强制完成；
- 校验失败需计数与锁定（防暴力破解）；
- 它**不参与 JWT**，只在余额相关接口的请求体里传入，用后即弃。

### 6.5 中间件基础设施（容器与端口）

| 中间件 | 容器镜像 | 9090 段端口 | 当前设备端口 | 备注 |
|---|---|---|---|---|
| Seata Server（TC） | `seataio/seata-server`（2.x） | 9109 | 8094 | 注册到现有 Nacos |
| RocketMQ NameServer | `apache/rocketmq`（5.x） | 9876 | 9876 | |
| RocketMQ Broker | 同上 | 10911 | 10911 | |
| RocketMQ Dashboard | `apacherocketmq/rocketmq-dashboard` | 8180 | 8180 | **默认 8080 会与 gateway 撞**，必须改 |
| Elasticsearch | `elasticsearch`（8.x） | 9200 | 9200 | 阶段 C |
| MongoDB | `mongo`（7.x） | 27017 | 27017 | 阶段 B，随 `comment-service` 同期引入 |

### 6.6 中间件落地清单

1. 根 pom 加依赖：`spring-cloud-starter-alibaba-seata`、Stream 的 RocketMQ binder
   （或降级方案 `rocketmq-spring-boot-starter`）；
2. `order_db`、`stock_db` 建 `undo_log` 表（AT 模式必需）；
3. `docker-compose.yml` 加 `seata-server`、RocketMQ（NameServer + Broker + Dashboard）；
4. 端口按 6.5 登记，两套端口段同步；
5. 阶段 B 随 `comment-service` 引入 **MongoDB（27017）**；阶段 C 再加 Elasticsearch。

### 6.7 评论服务的存储方案（已定：混合存储）

企业里稳妥的形态**不是把评论整体搬进 MongoDB**，而是**混合存储**：内容进文档库，关系元数据留关系库。

| 存什么 | 放哪 | 为什么 |
|---|---|---|
| 正文、图片数组、标签数组、商家回复、点赞数 | **MongoDB**（集合 `comments`，内含与 MySQL 对应的 `commentId`） | 嵌套结构免 join，是评论这个**聚合根**的自然形态 |
| `comment_id`、`order_id`、`user_id`、`product_id`、`window_id`、`score`、`status`、`created_at` | **MySQL `comment_db.comments`** | 关联查询、评分聚合、统计、风控都需要关系型能力 |

- **写入顺序（重要）**：**先写 MongoDB（内容）→ 再写 MySQL（元数据，本地事务）**。
  兜底是"**孤儿文档清理**"：定时任务扫出 MongoDB 中存在、MySQL 中无对应元数据的文档并补偿。
  选这个顺序的理由：反过来会产生"**有元数据但没有内容**"的评论——用户能看到评论条目却点不开，比孤儿文档更难发现。
- **本方案自带两处不一致**（已在 9.2 风险表登记）：
  1. **内容（MongoDB）与元数据（MySQL）跨存储不一致** → 固定写入顺序 + 定时对账；
  2. **菜品评分写回 `product_db` 时跨服务不一致** → RocketMQ 事件 + 消费端幂等 + 定时对账。
- **取舍一句话**：宁可留孤儿文档（可清理、不可见），也不要留无内容的评论（可见但坏）。

## 7. 功能缺口与归属

对照早期单体项目（`C:\Users\DELL\order-sys-back`，10 张表）的差距：

| # | 功能 | 归属 | 决策依据 |
|---|---|---|---|
| 1 | 菜品库存 | **`stock-service`（独立）** | 唯一需要回滚语义的场景 → 用 Seata AT（见 6.3/6.4） |
| 2 | 评论/评价 | **`comment-service`（独立）** | 高读写比 → 缓存练习载体；风险低 |
| 3 | 购物车 | **`cart-service`（独立）** | Redis 数据结构练习 |
| 4 | 图片上传 | **`file-service`（独立）** | 横切复用（头像/菜品图/评论图） |
| 5 | 站内消息推送 | **`notification-service`（独立）** | WebSocket 长连接练习 |
| 6 | 菜单搜索 | **`search-service`（独立，稍后）** | ES 检索练习 |
| 7 | 经营报表 | **`statistics-service`（独立，稍后）** | 只读聚合 / CQRS 雏形 |
| 8 | 订单超时自动取消（15 分钟） | `order-service` | 只操作本服务库，本地定时任务，无新技术点 |
| 9 | 余额 + 余额流水 | `user-service` | 必须与余额字段同事务，拆开会制造无收益的分布式事务 |
| 10 | 餐厅（食堂）层级 | `product-service` | 见 4.3，拆开只多一跳 |
| 11 | 窗口申请 / 入驻审核 | `product-service` + `user-service` | 本质是归属关系的建立流程，无新技术点 |

## 8. 端口与数据库分配

### 8.1 端口

**9090 段**：`9090~9101` 已分配（gateway/order/user/product/auth/MySQL/Redis/Nacos/Sentinel/payment），
新服务顺延：

| 服务 | 9090 段 | 当前设备旧端口段 |
|---|---|---|
| `file-service` | 9102 | 8087 |
| `cart-service` | 9103 | 8088 |
| `stock-service` | 9104 | 8089 |
| `comment-service` | 9105 | 8090 |
| `search-service` | 9106 | 8091 |
| `notification-service` | 9107 | 8092 |
| `statistics-service` | 9108 | 8093 |
| Seata Server | 9109 | 8094 |

> 注：`file-service` 占用 9102 后，`product-service` 的 **Sentinel 通信端口**已由 9102 让位改为 `9110`
> （见 `product-service/application.yml` 的 `SENTINEL_TRANSPORT_PORT`）。
>
> `stock-service`（9104）与 `file-service`（9102）已按上表落地；Seata Server 已占用 9109。
>
> **注意（2026-10-20 修正）**：TC 注册到 Nacos 的地址是**容器内网 IP**（如 `172.19.0.3:9109`），
> 该镜像无法用 `SEATA_IP` 覆盖。而本机开发时 TM/RM 跑在 Windows 宿主机上，WSL2 NAT 下宿主机
> 没有到容器网段的路由（该网段还会被本机代理软件的 TUN 接管，连过去只是 `198.18.x.x` 的假连接），
> 表现为 TCP 看似可连、30 秒无响应。因此 `order-service` / `stock-service` 的 Seata 客户端
> **不走注册中心发现**，改为 `seata.registry.type: file` + `seata.service.grouplist.default`
> 直连 `127.0.0.1:9109`。容器化部署（服务与 TC 同处一个 Docker 网络）时改回 `type: nacos`。

**两套端口的规矩不变**：9090 段属于另一台设备，当前设备的改动不要合并过去
（见 `PROJECT_PROGRESS.md`"当前设备本地端口恢复"）。

### 8.2 数据库

| 服务 | 存储 | 说明 |
|---|---|---|
| `stock-service` | `stock_db`（`stock`、`stock_flow`）+ Redis | 库存量在 Redis 做原子扣减，流水落 MySQL 供对账 |
| `cart-service` | 仅 Redis | 共享 Redis 即可跨设备访问；开启 AOF 与数据卷，不建 cart_db，不做游客合并 |
| `comment-service` | **MongoDB**（评论内容）+ `comment_db`（关系元数据） | 混合存储：内容免 join，元数据供关联与统计（见 6.7） |
| `file-service` | `file_db`（`file_objects`） | 元数据；若用对象存储且无需检索，可不建库 |
| `search-service` | ES 索引 | 与 MySQL 的主从式关系：**MySQL 为准，索引可随时重建** |
| `notification-service` | 可选 `notification_db` | 离线消息需要落库才能补投 |
| `statistics-service` | 无库 | 只读聚合，经 Feign 从 order/product 拉取后汇总 |
| Seata 参与库 | `order_db`、`stock_db` 各加 `undo_log` 表 | AT 模式必需的全局回滚日志 |

## 9. 分期实施与风险

### 9.1 分期路线

```text
阶段 A（已完成，6 个）  gateway · auth · user · order · product · payment
阶段 B（交易闭环与内容，4 个）
    1) stock        先补真实漏洞（当前可无限下单）+ Seata AT 主战场   ✅ 已完成
    2) cart         交易链路前置环节   ✅ 已完成（仅 Redis，不含 MySQL 备份与游客合并）
    3) comment      缓存练习载体
    4) file         前端上传依赖   ✅ 已完成
阶段 C（技术扩展，3 个）
    5) notification WebSocket 长连接（消费 RocketMQ 事件）
    6) search       ES 检索（RocketMQ 消息同步索引）
    7) statistics   报表，依赖其它服务稳定后再做
```

### 9.2 风险表（必须与对应服务同时落地）

| 风险 | 触发点 | 缓解措施 |
|---|---|---|
| **超卖 / 库存不一致** | `stock-service` | Redis Lua 原子预扣；Seata 全局回滚；**库存流水 + 定时对账**作为 TC 故障时的最后防线。**已落地**（2026-10-19）：Redis Lua 预扣可降级、DB 行锁 + 版本号乐观锁条件更新、`stock_flow` 唯一键幂等、`StockReconcileTask` 定时对账 |
| 分布式事务不可收敛 | `order × stock` | 用 Seata AT 的全局回滚做主路径；全局锁 + `undo_log` 保证一致性；禁止"先建单后扣库存无补偿"的写法。**已落地**（2026-10-19）：`createOrder` 标 `@GlobalTransactional`，订单与明细落库后调用 `StockServiceClient.deduct`，扣减失败即整体回滚 |
| Seata TC 单点 | `seata-server` | 本地单 TC 即可；TC 不可用时事务挂起 → 必须靠对账修复，因此对账不能省 |
| ES 与 MySQL 数据漂移 | `search-service` | 以 MySQL 为唯一事实来源；提供全量重建索引脚本，索引随时可丢弃重建 |
| 消息重复 / 丢失 | RocketMQ 消费方 | 消费端幂等（用业务唯一键）+ 消费失败重试 + 死信队列 |
| **评论内容与元数据跨库不一致** | `comment-service`（MongoDB × MySQL） | 固定写入顺序（先内容后元数据）+ 定时对账清理孤儿文档（见 6.7） |
| **菜品评分跨服务不一致** | `comment × product` | RocketMQ 事件 + 消费端幂等 + 定时对账（见 6.7） |
| WebSocket 穿网关 | `notification-service` | 网关需支持 WS 路由；握手阶段校验 JWT（WebFlux 过滤链与 Servlet 不同，需单独配置） |
| 服务数膨胀、本地起不动 | 全部 | 用 `docker compose` 分档启动；本地开发只启动当前链路所需服务；端口按 8.1 统一登记 |

### 9.3 上限说明

**13 个服务是上限**。再往上加（如 `restaurant-service`）必须回答"能学到什么新技术"，
答不出来就不加。

## 10. 新增服务的标准动作

1. 根 `pom.xml` `<modules>` 注册子模块；
2. 独立 Schema + 独立数据库账号（最小授权，凭据进 `.env`）；无 MySQL 的服务要写明为何不建库；
3. 接入 Nacos 注册与配置；JWT 资源服务器（RSA 公钥 + Redis 黑名单）；
4. 端口按 8.1 分配，并同步另一套端口段的对应值；
5. 明确鉴权模式：透传用户 JWT = "代表用户"；`X-Internal-Token` = "系统事件"；
6. 被其它服务调用时，建 `xxx-api` 契约模块；
7. 网关注册路由（Nacos `gateway-service.yaml`），同步保存到 `docs/nacos/`；
8. 补 `docs/database/migrations/<db>/` 幂等迁移脚本；
9. 若该服务引入跨服务一致性，**必须同时落地 9.2 的缓解措施**，
   并按 6.4 明确它是"用 Seata"还是"走最终一致"；
10. 回填本文档第 3 节总表与第 13 节变更记录。

## 11. 待决策事项

| # | 事项 | 结论 / 影响 |
|---|---|---|
| 1 | 库存一致性方案 | ✅ **已定**：`order × stock` 用 **Seata AT**（见 6.3 / 6.4①）；本地消息表作为对比课题 |
| 2 | 评论存储 | ✅ **已定**：**混合存储** —— 内容入 MongoDB，关系元数据留 MySQL（见 6.7） |
| 3 | 购物车存储 | ✅ **已定（2026-10-17 修订）**：仅 Redis，AOF + 数据卷；不建 MySQL 备份，仅登录顾客使用，不做游客合并 |
| 4 | 余额与支付的关系 | ✅ **已定**：余额作为**支付渠道**接入 `payment-service`，扣款与流水记账归 `user-service`（见 6.4） |
| 5 | 餐厅命名 | ✅ **已定**：统一用**餐厅**（表名 `restaurants`）；原单体的"食堂 / `canteen`"只作为历史对照，不再出现在新代码中 |
| 6 | ES 索引同步方式 | ✅ **已定**：**RocketMQ 消息驱动**（见 6.1）；Canal / 定时全量不再作为首选 |

> **本节已全部收敛**（2026-10-13）。后续新增待决策项在此追加。

## 12. 已确认的口径（2026-10-13 用户裁定）

| 事项 | 裁定 |
|---|---|
| 权限体系设计 | ✅ 认可，可据 [2026-10-13-role-authority-design.md](superpowers/specs/2026-10-13-role-authority-design.md) 进入实施 |
| 业务端点角色 | 立刻改：`SYSTEM_ADMIN` **不得**再拥有推进订单状态、改商品目录的权限（但**必须与数据范围校验同时上线**，否则是拿一个洞换另一个洞） |
| 账户/档口归属的**授予权** | 归 **餐厅经理（本餐厅）** 与 **总经理（全部餐厅）**；**SYSTEM_ADMIN 不再拥有数据范围分配权** |
| 余额支付密码 | 必须有，且是**独立于登录密码**的支付密码 |
| 引导配置 | 对齐 `2026-10-12-admin-bootstrap-design.md`（显式开关 + 手机号 + 密码 12~64 位） |
| 方法名与路径改名 | 一并执行（`updateStatusByAdmin` → `advanceOrderStatus` 等，见设计文档第 7 节） |

## 13. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-13 | 初版（按"生产项目"判据：服务 7 ~ 8 个，购物车/ES/推送列为反模式） |
| 2026-10-13 | **修订**：判据改为"技术学习价值第一优先"；服务数 6 → 13（分 A/B/C 三阶段）；`cart`/`search`/`notification` 由"不建"改为"纳入规划"；新增第 4 节三个待定服务分析（`comment` 建、`stock` 建但须配一致性方案、`restaurant` 不建并入 product）；新增风险表 |
| 2026-10-13 | **新增第 6 节「中间件与分布式事务选型」**：MQ 选定 **RocketMQ + Spring Cloud Stream**（理由：国内主流 + 原生事务消息 + Binder 抽象，附直连 starter 降级路径）；分布式事务选定 **Seata AT** 且仅用于 `order × stock`；登记三场景两方案（6.4）、中间件端口（6.5）、落地清单（6.6）；同步替换正文中所有 MQ/Seata/索引同步的待定表述；原第 6~11 节顺延为 7~12，待决策 #1/#4/#6 标记为已收敛 |
| 2026-10-13 | **评论存储定稿为「混合存储」**（新增 6.7）：评论内容入 **MongoDB**（阶段 B 引入，端口 27017），关系元数据留 **MySQL `comment_db`**；明确写入顺序为"先内容后元数据"+ 孤儿文档清理；9.2 新增两行风险（内容/元数据跨库不一致、菜品评分跨服务不一致）；第 3/4.1/6.1/6.5/6.6/8.2/11 节同步更新，待决策 #2 标记为已收敛 |
| 2026-10-13 | **待决策全部收敛 + 用户裁定落档**：待决策 #3 购物车定为"纯 Redis 为主 + MySQL 备份表"、#5 餐厅统一命名"餐厅/`restaurants`"；6.4 追加**余额支付密码必须独立于登录密码**（`users.pay_password_hash`，不进 JWT，含失败计数与锁定）；新增第 12 节"已确认的口径"（含归属授予权归餐厅经理+总经理、SYSTEM_ADMIN 不再拥有数据范围分配权、业务端点改角色须与数据范围校验同时上线）；原"变更记录"顺延为第 13 节 |
| 2026-10-16 | **`file-service` 落地（阶段 B 第 4 个）**：独立 `file_db`（`file_objects`），端口 9102；存储抽象接口 `FileStorage` + 本地实现，落盘路径按内容摘要生成（天然秒传去重），魔数校验真实类型；内容接口 `/files/{fileKey}/content` 匿名开放供图片直接引用、上传/元数据/删除需登录；网关新增 `file-service` 与 `file-service-docs` 路由；因占用 9102，`product-service` 的 Sentinel 通信端口让位改为 `9110`；第 3 节总表与 `SERVICE_DATABASE_INVENTORY.md` 同步更新 |
| 2026-10-17 | 按用户安排优先构建 `cart-service`；新增 9103 服务骨架、JWT/Redis 与网关路由配置，MySQL 备份仍保留为后续步骤；详细计划见 [CART_MODULE_PLAN.md](CART_MODULE_PLAN.md) |
| 2026-10-17 | 用户确认简化购物车：**Redis 为唯一业务存储**，保留 AOF 和数据卷；取消 MySQL 备份与游客合并，仅登录顾客使用。覆盖此前双存储规划（第 11 节待决策 #3 同步修订） |
| 2026-10-17 | 新增 `order-api` 只读购买事实契约（`OrderCartSnapshotVO` / `OrderPurchasedItemVO` / `OrderServiceClient`），供购物车核实下单结果；`cart-service` 提供订单成功后的按提交快照清理接口，保留并发修改项；调用方显式串联，无跨服务事务与自动补偿 |
| 2026-10-19 | **`stock-service` 落地（阶段 B 第 1 个），Seata AT 首次投产**：新增 `stock_db`（`stock` / `stock_flow` / `undo_log`）与 `order_db.undo_log`；Seata Server 2.5.0 以 `file` 存储模式跑在 9109（控制台 9108）并注册到现有 Nacos（`SEATA_GROUP` / cluster `default`），事务分组统一 `order-system-tx-group`；新增契约模块 `stock-api`（`StockServiceClient` + 降级工厂），`order-service` 引入 `spring-cloud-starter-alibaba-seata` 并在 `createOrder` 上加 `@GlobalTransactional`，落单后跨服务扣减库存；库存侧三重防线 = Redis Lua 原子预扣（可降级）+ DB 行锁与版本号乐观锁条件更新 + `stock_flow` 唯一键 `(biz_type, biz_ref, stock_id)` 幂等，另有 `StockReconcileTask` 定时对账兜底（快照漂移自动回写、流水累计与主表差值只告警不改数）；内部接口 `POST /internal/stocks/deduct|restore`、`GET /internal/stocks/{windowId}/{productId}` 业务失败返回 HTTP 200 + 失败业务码，防止调用方 Feign 降级把「库存不足」吞成「服务不可用」；网关只注册 `stock-service-docs`（`/api/stocks/v3/api-docs`），**不注册 `/api/stocks/**` 业务路由**（该服务当前对外只有 `/internal/**`）；已确认**取消订单暂不回补库存**，留作后续任务 |
