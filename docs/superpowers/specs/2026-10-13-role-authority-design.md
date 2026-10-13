# 角色权限与数据范围体系设计

> 文档性质：设计方案（待评审）。
> 关系：本文档是 `2026-10-12-admin-bootstrap-design.md` 在"真实管理接口出现"后的落地细化，
> 落实其第 19 行确立的原则——**管理接口必须同时校验角色与数据范围**。
>
> **2026-10-13 修订（用户裁定）**：数据范围的**授予权**从 `SYSTEM_ADMIN` 移出，
> 归 **餐厅经理（限本餐厅）** 与 **总经理（全平台）**；技术角色只管"身份（角色）"。
> 为此新增 3.5「引导期的信任根」，并同步改写 3.1 / 3.2 / 3.3 / 3.4 / 5.5 / 7 / 8 / 9 / 10 / 11 节。

## 1. 背景与已证实的问题

当前项目已完成首个 `SYSTEM_ADMIN` 账号引导，登录后 JWT 携带 `roles:["SYSTEM_ADMIN"]`。
但在实现授权前，核查代码与数据库发现四个必须一并解决的问题：

| 编号 | 问题 | 证据 |
|---|---|---|
| P1 | 管理接口使用了**不存在的角色码** `ADMIN`，导致接口无人可调 | `OrderController:154`、`ProductAdministrationController:37` 均为 `hasRole('ADMIN')`，而系统只产生 `SYSTEM_ADMIN` |
| P2 | 若改为放行 `SYSTEM_ADMIN` 则**技术角色越权**，违背业务职责划分 | 系统管理员的职责是账号、角色、配置，不参与订单履约 |
| P3 | `users` 表**没有数据范围归属**，无法判断某个 `STALL_MANAGER` 管哪个档口 | `users` 仅有 `role_code`，无 `window_id`、无范围关联表 |
| P4 | 数据库**没有餐厅维度**，`RESTAURANT_MANAGER` / `GENERAL_MANAGER` 无数据可管 | 只有 `dining_windows`，没有 `restaurants` 表，`dining_windows` 无 `restaurant_id` |

同时确认两项有利条件：

- `orders.window_id BIGINT NOT NULL`（`V1__create_orders_table.sql:5`），**订单天然归属某个档口**，
  按档口授权在数据层面可行。
- `product_categories`、`products` 也都有 `window_id`，菜单数据同样可按档口划分。

`users.role_code VARCHAR(20)` 足以容纳全部五种角色编码（最长 `RESTAURANT_MANAGER` 为 18 字符），无需扩容。

## 2. 核心原则

1. **双维度授权**：功能权限（角色：能执行哪类操作）与数据权限（范围：能操作哪些数据）
   必须**同时校验**，任何一方不满足即拒绝。
2. **认证不等于授权**：Gateway 只负责 JWT 验签、黑名单、路由与限流；
   业务授权一律由数据所有者在自己的服务内完成，Gateway 的认证不能替代业务服务的授权。
3. **最小权限与职责分离**：技术角色（`SYSTEM_ADMIN`）不触碰业务数据，**也不分配数据范围**；
   业务角色不能执行平台级操作、也不得把范围授予自己不具备的区域。
   身份（角色）由技术角色管，权限（范围）由业务角色管，两者边界清晰、不互相兜底。
4. **默认拒绝**：未显式放行的接口一律要求认证；未显式授权的操作一律拒绝。
5. **授权判定只看服务端事实**：数据范围来自服务端数据源，不采信客户端传入的任何角色或范围参数。
6. **失败语义统一且不泄露**：越权与不存在对外表现一致，避免被用于探测数据是否存在（见第 6 节）。

## 3. 角色模型

### 3.1 角色定义

| 角色编码 | 业务身份 | 本质 | 数据范围 |
|---|---|---|---|
| `CUSTOMER` | 顾客 | 业务 | 仅本人数据 |
| `STALL_MANAGER` | 档口负责人/店主 | 业务 | 所辖档口（1..N 个 `window_id`） |
| `RESTAURANT_MANAGER` | 餐厅经理 | 业务 | 所属餐厅下全部档口（`restaurant_id`）；**并负责授予/撤销本餐厅内的档口范围** |
| `GENERAL_MANAGER` | 总经理 | 业务 | 全平台经营数据；**并负责授予/撤销任意餐厅的范围（范围授予链的根，见 3.5）** |
| `SYSTEM_ADMIN` | 系统管理员 | **技术** | 账号、角色、系统配置；**不碰业务数据，也不分配数据范围** |

新增 `restaurants` 表后，餐厅成为真实层级：`restaurant 1 → N window`。

### 3.2 职责矩阵

`✓` 允许，`—` 不允许。所有允许项还需通过第 4 节的数据范围校验。

| 操作 | CUSTOMER | STALL_MANAGER | RESTAURANT_MANAGER | GENERAL_MANAGER | SYSTEM_ADMIN |
|---|---|---|---|---|---|
| 创建订单 | ✓ | — | — | — | — |
| 取消本人待支付订单 | ✓ | — | — | — | — |
| 查看本人订单/支付 | ✓ | — | — | — | — |
| 发起支付 | ✓ | — | — | — | — |
| 查看本范围内订单 | — | ✓ | ✓ | ✓ | — |
| 推进订单：已支付→制作中 | — | ✓ | ✓ | — | — |
| 推进订单：制作中→待取货 | — | ✓ | ✓ | — | — |
| 推进订单：待取货→已完成 | — | ✓ | ✓ | — | — |
| 标记订单已支付 | — | — | — | — | — |
| 管理档口菜单（分类/商品） | — | ✓ | ✓ | — | — |
| 新建/停用档口 | — | — | ✓ | — | — |
| 查看经营汇总数据 | — | — | ✓ | ✓ | — |
| 分配角色（写 `users.role_code`） | — | — | — | — | ✓ |
| 授予/撤销数据范围：**本餐厅内档口** | — | — | ✓ | — | — |
| 授予/撤销数据范围：**任意餐厅** | — | — | — | ✓ | — |
| 启用/禁用账号 | — | — | — | — | ✓ |
| 系统配置管理 | — | — | — | — | ✓ |

三条不可逾越的红线：

1. **`待支付 → 已支付` 由支付回调独占**，任何角色（含全部管理员）都不能手动推进——
   现有 `OrderStatus.canBeAdvancedByAdminTo` 已强制该规则，改造后必须保留。
2. **`SYSTEM_ADMIN` 不能推进任何订单状态、不能改任何菜品数据、也不能授予数据范围**。
   它的正当操作是"把某个账号的身份设成某角色"，而不是"决定谁能管哪些档口"，
   更不是"替某人操作业务"。
3. **`CUSTOMER` 只能操作本人数据**，永远不因角色而在他人数据上获得任何许可。

> **口径分界（2026-10-13 用户裁定）**：
> **角色（能做什么）** 是技术身份，归 `SYSTEM_ADMIN`；
> **数据范围（能管哪些）** 是业务权限，归**餐厅经理**（限本餐厅）与**总经理**（全平台）。
> 两者由**不同角色、不同接口**分别授予，一次操作不得同时改变身份与范围（见 5.5）。

### 3.3 SYSTEM_ADMIN 的权限边界

`SYSTEM_ADMIN` 是**技术角色**：授予的是"配置系统"的能力，不是"操作业务"的能力。
判断口径只有一句——**它决定"谁是哪种角色"，但不决定"谁管哪些数据"，更不"替谁去做"**。

允许的四类能力：

| 类别 | 具体动作 | 边界 |
|---|---|---|
| 账号生命周期 | 创建账号、启用/禁用账号、重置密码、解锁 | 只改 `users` 的身份与凭据字段，不提供余额等业务字段的修改入口 |
| 角色分配 | 设置某账号的 `role_code` | 受 3.4 约束①；**只写 `role_code`，不写范围** |
| 平台配置 | Nacos 配置、网关路由、限流与熔断规则 | 只影响"系统如何运行"，不影响业务数据本身 |
| 审计只读 | 查看管理操作审计日志 | 只读，见 3.4 约束④ |

**已移除的能力（2026-10-13 用户裁定）**：~~数据范围分配~~。
数据范围的授予权归**餐厅经理**（限本餐厅）与**总经理**（全平台），见 3.5 与 5.5。
理由：范围分配是**业务权限**，应由对经营结果负责的业务角色决定；
把它放在技术角色手里，等于让运维身份实质掌握业务授权，
既是越权，也削弱了"谁经营谁授权"的问责链。

明确禁止：

- 推进任何订单状态（含 `待支付 → 已支付`，该流转由支付回调独占）
- 写入任何菜单、档口、餐厅业务数据
- **写入 `user_data_scopes`（授予/撤销数据范围）**
- 提供业务明细的明文查询入口（订单、支付流水属用户隐私）；
  排障依赖审计日志与脱敏聚合，而非开通万能查询接口

### 3.4 四条硬约束（防止红线被绕过）

3.2 的红线与 3.3 的禁止项，只有在下列约束同时成立时才是真的。

**① 不可自我提权。** 若 `SYSTEM_ADMIN` 能修改自己的 `role_code`，
它就能一次操作把自己变成 `STALL_MANAGER`，直接绕开"技术角色不碰业务数据"的红线。
故：**禁止修改本人 `role_code`**（对外表现为 403）。
（范围维度已由 3.3 整体移出，此处只需守住角色维度。）

**② 授权不得超出自身范围。** 授予者只能授予**自己已具备的范围**：
- `RESTAURANT_MANAGER` 只能授予/撤销**本餐厅下**的档口范围；
- `GENERAL_MANAGER` 可授予/撤销任意餐厅的范围；
- 任何人**不得给自己授予范围**（对外表现为 403）——否则总经理之外的授权者
  可通过自授权扩张范围，绕过 3.2 的"本餐厅内"限制。

**③ 不可锁死自己。** 禁止禁用或删除**最后一个**处于启用状态的 `SYSTEM_ADMIN`，
否则系统进入"无人可管理"的死局（对外表现为 409）。

**④ 审计不可篡改。** 审计日志只允许追加，`SYSTEM_ADMIN` 同样不能修改或删除，
否则审计记录失去追溯价值。

### 3.5 引导期的信任根（第一个总经理从哪来）

范围授予权既然不在 `SYSTEM_ADMIN` 手上，就必须回答：**最初那位能授权的人是谁给的**？
这是任何权限体系都存在的"信任根"问题，只能由**带外初始化**产生，不能自举。

因此引导流程（`2026-10-12-admin-bootstrap-design.md`）需一并创建**两个**账号：

| 账号 | 角色 | 作用 | 范围来源 |
|---|---|---|---|
| 系统管理员 | `SYSTEM_ADMIN` | 管账号与角色 | 不需要业务范围 |
| 初始总经理 | `GENERAL_MANAGER` | **范围授予链的根** | 由引导直接写入 `PLATFORM` 范围 |

之后的下沉链条为：**初始总经理 → 授予各餐厅经理（`RESTAURANT`）→ 餐厅经理授予档口负责人（`WINDOW`）**。
`SYSTEM_ADMIN` 只在这些账号存在后为其设置 `role_code`，不参与范围授予。

> `users.role_code` 为单值，一个账号只能持一个角色，因此两个身份必须分为两个账号；
> 多角色支持列入 Phase 2（见第 9 节）。

## 4. 数据范围模型

### 4.1 设计决策

用户与档口/餐厅的归属采用**独立关联表**，而不是在 `users` 表加单个 `window_id` 列。

理由：单个 `STALL_MANAGER` 管理两个档口是常态，单列模型下只能开两个账号；
此后一旦要支持多档口，就必须做一次表结构迁移 + 数据搬迁。
关联表初期每用户只驻留一行，成本差异极小，但避免了必然发生的返工。

> 取舍声明：这偏离了 `2026-10-12-admin-bootstrap-design.md` 中"本次不提前增加该数据库模型"一句。
> 偏离理由即上述返工成本；`users.role_code` 仍保持单值，本次**不引入多角色**。

### 4.2 DDL

`product_db`（餐厅与档口层级，由 product-service 拥有）：

```sql
CREATE TABLE IF NOT EXISTS restaurants (
    restaurant_id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_code VARCHAR(32) NOT NULL,
    restaurant_name VARCHAR(64) NOT NULL,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (restaurant_id),
    UNIQUE KEY uk_restaurants_code (restaurant_code),
    CONSTRAINT chk_restaurants_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 先插入默认餐厅，再给存量档口补 restaurant_id，最后收紧为 NOT NULL
INSERT INTO restaurants (restaurant_code, restaurant_name)
SELECT 'R0001', '默认餐厅'
WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE restaurant_code = 'R0001');

ALTER TABLE dining_windows
    ADD COLUMN restaurant_id BIGINT NOT NULL DEFAULT 1 AFTER window_id,
    ADD KEY idx_dining_windows_restaurant (restaurant_id),
    ADD CONSTRAINT fk_dining_windows_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (restaurant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
```

`user_db`（范围授权，由 user-service 拥有）：

```sql
CREATE TABLE IF NOT EXISTS user_data_scopes (
    scope_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_ref BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (scope_id),
    UNIQUE KEY uk_user_data_scopes (user_id, scope_type, scope_ref),
    KEY idx_user_data_scopes_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`scope_type` 取值：`WINDOW`（`scope_ref` = `window_id`）、
`RESTAURANT`（`scope_ref` = `restaurant_id`）、`PLATFORM`（`scope_ref` = 0，全平台）。

**不建跨库外键**：`scope_ref` 指向的档口/餐厅位于 `product_db`，MySQL 无法跨库建外键。
其有效性由分配范围时调 product-service 内部接口校验保证（见 5.4），
这与项目既有的跨服务引用方式一致（`orders.user_id` 同样没有指向 `user_db` 的外键）。

## 5. 技术架构

### 5.1 分层职责

```text
客户端
  │  Authorization: Bearer <JWT>
  ▼
Gateway 9090 ──── 仅认证：验签、黑名单、路由、限流。不做业务授权
  │  透传 JWT
  ▼
业务服务 ──── ① 角色门禁：@PreAuthorize（粗粒度，类或方法级）
  │           ② 数据范围校验：Service 层（细粒度，逐资源）
  │  X-Internal-Token
  ▼
user-service ──── 角色与数据范围的唯一数据所有者
                    （角色由 SYSTEM_ADMIN 写；范围由餐厅经理/总经理写，见 3.3/3.5）
```

### 5.2 角色门禁（粗粒度）

沿用现有机制：JWT 的 `roles` 声明由资源服务器映射为 `ROLE_` 前缀权限，因此注解直接写角色名。

改造点：

| 位置 | 现状 | 改为 |
|---|---|---|
| `OrderController:154` | `hasRole('ADMIN')` | `hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')` |
| `ProductAdministrationController:37` | `hasRole('ADMIN')` | `hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')` |

`hasRole('ADMIN')` 必须彻底删除，不保留兼容别名——保留只会让"死角色码"重新出现。

### 5.3 数据范围校验（细粒度）

在 `common-core` 提供统一的范围判定服务，各业务服务在 Service 层调用：

```java
public interface DataScopeService {

    /**
     * 校验用户是否有权操作指定档口的数据。
     * 主管档口、所属餐厅、全平台范围均视为有权。
     *
     * @param userId   当前登录用户 ID
     * @param windowId 目标档口 ID
     */
    void checkWindowAccess(Long userId, Long windowId);

    /**
     * 校验用户是否有权操作指定餐厅的数据。
     *
     * @param userId       当前登录用户 ID
     * @param restaurantId 目标餐厅 ID
     */
    void checkRestaurantAccess(Long userId, Long restaurantId);

    /**
     * 查询用户的数据范围快照。
     *
     * @param userId 用户 ID
     * @return 范围集合
     */
    UserScopeSnapshot loadScopes(Long userId);
}
```

调用示例（order-service 状态推进）：

```java
public OrderStatusVO advanceOrderStatus(Long operatorId, Long orderId, OrderStatus target) {
    OrderDO order = requireOrder(orderId);

    // 细粒度：先确认该订单落在操作者的数据范围内
    dataScopeService.checkWindowAccess(operatorId, order.getWindowId());

    // 状态机：待支付→已支付 由支付回调独占，此处必然拒绝
    OrderStatus current = OrderStatus.findByLabel(order.getStatus());
    if (current == null || !current.canBeAdvancedByManagerTo(target)) {
        throw new BusinessException(CommonErrorCode.CONFLICT, "当前状态不允许该流转");
    }

    return persistStatusTransition(order, target, operatorId);
}
```

### 5.4 范围来源与缓存

数据范围**不放入 JWT**，改由 user-service 作为唯一事实来源提供：

- 内部接口：`GET /internal/users/{userId}/scopes`（沿用既有的 `X-Internal-Token` 共享密钥模式）
- 各服务经 Feign 调用，结果缓存到 Redis：键 `authz:user-scope:{userId}`，TTL 300 秒
- **范围变更即时生效**：user-service 在分配/撤销范围后主动删除该键
- 键名常量收敛到 `common-redis` 的一个常量类，避免各服务硬编码字符串

不放 JWT 的理由：JWT 有效期 30 分钟，若范围写入 JWT，
撤销某个档口负责人的权限后，其旧 Token 在有效期内仍可操作该档口——这是真实的越权窗口。
缓存 + 主动失效可以把该窗口压缩到"一次 Redis 删除"。

### 5.5 范围授予接口（餐厅经理 / 总经理的职责，**不再是 SYSTEM_ADMIN**）

```text
PUT    /users/{userId}/scopes          覆盖式设置某用户的范围列表
GET    /users/{userId}/scopes          查询某用户的范围
DELETE /users/{userId}/scopes/{scopeId} 撤销单条范围
```

鉴权与约束（对应 3.3 / 3.4②）：

- 可调用角色：`RESTAURANT_MANAGER`、`GENERAL_MANAGER`
  （`@PreAuthorize("hasAnyRole('RESTAURANT_MANAGER','GENERAL_MANAGER')")`）；
- **授予者只能授予自己已具备的范围**：
  - `RESTAURANT_MANAGER` 只能写入 `scope_type=RESTAURANT`/`WINDOW` 且落在**本餐厅**的记录；
  - `GENERAL_MANAGER` 不受餐厅限制；
  - 越出自身范围的写入返回 403（**不是 404**：授权对象是"权限"，不是私有业务数据）；
- **不得操作本人**：`userId` 等于操作者时返回 403（对应 3.4② 的自授权禁令）；
- 写入前校验 `scope_ref` 指向的档口/餐厅真实存在（调 product-service 内部接口），
  且其所属餐厅必须落在授予者范围内；
- 写入后记审计日志，并删除 `authz:user-scope:{userId}` 缓存键；
- **该接口只写 `user_data_scopes`，不修改 `users.role_code`**；
  角色变更走 `SYSTEM_ADMIN` 的账号管理接口，两者**分属不同角色、不同接口，不合并**，
  避免一次操作同时改变身份与范围（见 3.2 口径分界）。

## 6. 失败语义

| 情况 | HTTP | 错误码 | 说明 |
|---|---|---|---|
| 未携带或无效 Token | 401 | `COMMON_401` | 认证失败 |
| 角色不具备该操作类别 | 403 | `COMMON_403` | 授权失败，与数据无关 |
| 角色具备，但目标属**私有数据**且不在范围内 | 404 | `COMMON_404` | 不泄露存在性（订单、支付单） |
| 角色具备，但目标属**公开资源**且不在范围内 | 403 | `COMMON_403` | 档口、菜单本身公开，无需隐藏 |
| 范围授予越出自身范围，或对本人授权 | 403 | `COMMON_403` | 授权类操作不涉及"隐藏存在性" |
| 目标资源不存在 | 404 | `COMMON_404` | — |
| 状态机不允许的流转 | 409 | `COMMON_409` | 与 payment-service 现有语义一致 |

判定规则一句话：**"这类操作你能不能做"不通过 → 403；"这条数据你能不能碰"不通过 → 私有数据 404、公开资源 403。**

## 7. 代码改造清单

1. **删除死角色码**：清除全部 `hasRole('ADMIN')`（`OrderController`、`ProductAdministrationController`）。
2. **重命名去掉 admin 语义**：状态推进的操作者是业务角色而非管理员
   - `OrderService.updateStatusByAdmin` → `advanceOrderStatus(Long operatorId, Long orderId, OrderStatus target)`
   - `OrderStatus.canBeAdvancedByAdminTo` → `canBeAdvancedByManagerTo`
   - 端点 `PATCH /orders/admin/{orderId}/status` → `PATCH /orders/{orderId}/status`
   - 同时把 `operatorId` 显式传入 Service，供范围校验与审计使用
3. **新增范围校验**：状态推进前调用 `dataScopeService.checkWindowAccess(operatorId, order.getWindowId())`。
4. **product-service 同步**：菜单与档口管理接口在 Service 层校验档口范围；
   新建/停用档口限制为 `RESTAURANT_MANAGER` 并校验餐厅范围。
5. **user-service 扩展**：
   - 范围内部接口（供各服务查询，`X-Internal-Token`）；
   - 范围授予接口，调用者角色为 `RESTAURANT_MANAGER` / `GENERAL_MANAGER`（**不是 `SYSTEM_ADMIN`**），
     含"授予者不得越出自身范围""不得对本人授权"两项校验（3.4②、5.5）；
   - 角色分配接口单独存在，仅 `SYSTEM_ADMIN` 可调用，且只写 `role_code`；
   - 缓存失效。
6. **数据库迁移**：`restaurants` 表 + `dining_windows.restaurant_id`（product_db）；
   `user_data_scopes` 表（user_db）。迁移脚本保持幂等，沿用 `backup/*.sh` 的容器内执行模式。
7. **越权守卫**（对应 3.4 四条约束）：
   - ① `SYSTEM_ADMIN` 的账号管理接口拒绝修改本人 `role_code`；
   - ② 范围授予接口校验"授予者范围 ⊇ 被授予范围"，且拒绝 `userId` 等于操作者本人；
   - ③ 禁用/删除账号前校验其是否为最后一个启用的 `SYSTEM_ADMIN`；
   - ④ 审计日志只提供写入与查询入口，不提供修改与删除入口。

## 8. 审计

**本期（Phase 1）**：管理类操作写入结构化日志，固定字段
`actorUserId`、`roleCode`、`action`、`targetType`、`targetId`、`before`、`after`、`result`。
覆盖：订单状态推进、角色分配、范围授予、菜单与档口变更。

**后续（Phase 2）**：落 `authorization_audit_log` 表长期留存，供追溯与合规检查。
本期先用日志，是因为表结构一旦定下就会长期承载历史数据，
宜在管理操作类型稳定后再固化，避免频繁变更。

## 9. 实施阶段

### Phase 1 —— 打通"档口负责人推进订单"闭环（本期）

1. 迁移：`restaurants`、`dining_windows.restaurant_id`、`user_data_scopes`
2. `common-core`：`DataScopeService`、范围常量、错误语义支持
3. `user-service`：内部范围查询接口 + 范围授予接口（餐厅经理/总经理）+ 角色分配接口（`SYSTEM_ADMIN`）+ 缓存失效
4. `user-service` 引导扩展：除 `SYSTEM_ADMIN` 外，另创建**初始总经理**并写入 `PLATFORM` 范围（破解信任根，见 3.5）
5. `order-service`：删除 `ADMIN`、改名与端点调整、范围校验、审计日志
6. `product-service`：删除 `ADMIN`、菜单/档口范围校验
7. 端到端验证（见第 10 节）
8. 准备可登录的 `STALL_MANAGER` 测试账号，**由初始总经理**为其授予档口范围

### Phase 2 —— 后续按需

- 多角色支持（`users.role_code` 单值 → 用户角色关联表）
- 审计落表
- `GENERAL_MANAGER` 跨餐厅报表
- 范围批量管理界面

## 10. 验收与测试

| 场景 | 期望结果 |
|---|---|
| `STALL_MANAGER` 推进**本档口** 订单 `已支付→制作中` | 200，状态变更 |
| `STALL_MANAGER` 推进**他人档口**订单 | 404（订单为私有数据） |
| `STALL_MANAGER` 调用范围授予接口 | 403（无此角色权限） |
| `SYSTEM_ADMIN` 推进任意订单状态 | 403（技术角色不碰业务） |
| `SYSTEM_ADMIN` 调用范围授予接口 | 403（**范围授予权已移出技术角色**） |
| `SYSTEM_ADMIN` 修改账号 `role_code` | 200（这是它的正当职责） |
| `GENERAL_MANAGER` 授予任意餐厅范围 | 200，且被授予人的缓存键被删除 |
| `RESTAURANT_MANAGER` 授予**本餐厅**档口范围 | 200 |
| `RESTAURANT_MANAGER` 授予**其他餐厅**范围 | 403（不得越出自身范围） |
| 撤销范围后，该用户用**旧 Token** 再操作原档口 | 404（缓存失效即刻生效） |
| `CUSTOMER` 推进订单状态 | 403 |
| 任何角色手动把订单标记为 `已支付` | 409（支付回调独占） |
| `RESTAURANT_MANAGER` 推进本餐厅下任一档口订单 | 200 |
| `RESTAURANT_MANAGER` 推进其他餐厅订单 | 404 |
| 无 Token 访问管理接口 | 401 |
| 范围授予时 `scope_ref` 指向不存在的档口 | 400，拒绝写入 |
| `SYSTEM_ADMIN` 修改**本人** `role_code` | 403（不可自我提权） |
| `GENERAL_MANAGER` / `RESTAURANT_MANAGER` 给**本人**授予范围 | 403（自授权禁令） |
| `SYSTEM_ADMIN` 禁用/删除**最后一个**启用的 `SYSTEM_ADMIN` | 409（不可锁死自己） |
| 尝试修改或删除审计日志 | 403（无该入口，审计只可追加） |

单元测试沿用项目既有的纯 Mockito 模式（`@ExtendWith(MockitoExtension.class)`，
用 `ReflectionTestUtils` 注入 `baseMapper`），不连真实数据库。

## 11. 明确不做

- **不引入 Spring Security ACL 表**：本系统的范围模型是"档口/餐厅"两级，
  用 ACL 的表达能力远超需求，且运维成本高。
- **不使用 OAuth2 scope 表达数据范围**：scope 语义是"客户端被授予的权限"，
  与"用户能管哪些档口"不是一回事。
- **不在 JWT 中携带数据范围**：理由见 5.4。
- **不用 MyBatis 拦截器做行级 SQL 改写**：隐式改写 SQL 会让"为什么查不到数据"极难排查；
  显式的服务层校验可读、可测、可审计。
- **不为 `GENERAL_MANAGER` 开放业务写操作**：订单、菜单、档口一律不行；
  它唯一的写操作是**范围授予**（3.5 的信任根职责）。
- **不保留 `ADMIN` 角色别名**：见 5.2。
- **不让 `SYSTEM_ADMIN` 重新获得数据范围授予权**：该能力的归属已由 3.3/3.5 定死；
  若将来确需由技术角色管理，必须重新回答"谁经营谁授权"的问责问题，不能顺手放开。

## 12. 已确认的前提

- `orders.window_id`、`products.window_id`、`product_categories.window_id` 均为 `NOT NULL`，
  档口维度完整覆盖业务数据。
- `users.role_code VARCHAR(20)` 可容纳全部角色编码。
- JWT 的 `roles` 声明已能正确映射为 `ROLE_` 前缀权限，`@PreAuthorize` 可直接使用角色名。
- 服务间内部调用已有两种成熟模式：透传用户 JWT（product 报价）与
  `X-Internal-Token` 共享密钥（payment→order、user 范围查询）。
