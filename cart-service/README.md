# cart-service 购物车服务

> 顾客购物车的增删改查与下单后安全清理。默认端口 `9103`，无数据库——Redis Hash 是唯一主存储；所有写入先经商品服务核价，Lua 脚本保证容量与并发原子性。不生产、不消费任何 MQ 消息。

## 模块定位与职责边界

- 只负责购物车行的存取与清理；价格与规格键不是本服务的事实来源。
- 添加前必须调商品服务批量核价（`ProductServiceClient.quote`），Redis 只保存核验后的名称/编码/规格文字快照；不保存可结算价格、不预留库存。
- 身份边界：用户 ID 一律取自已验签 JWT 的 subject，请求体与路径不接受 userId。
- 清理边界：只读订单核实购买事实并删除对应购物车行，不创建、不回滚订单，不依赖当前价格；清理端点由前端在下单成功后持原快照调用，后端服务不调用它。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server、Validation、Actuator |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer + Sentinel（Feign 降级） |
| 数据 | Redis（唯一主存储，无数据库驱动） |
| 共享模块 | common-web、common-redis、order-api、product-api |

- 端口 `9103`；Redis `127.0.0.1:9096`（db 0）；Feign connect 2000 / read 3000，Sentinel 降级启用。
- JWT：issuer `http://localhost:9094`、外置 RSA 公钥、黑名单前缀 `auth:access-token:blacklist:`。
- 服务间共享令牌 `internal.scope.token`；Feign 透传用户 Bearer Token，不模拟用户身份。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `CartController` | 顾客购物车 HTTP 入口，身份只取 JWT |
| `CartCleanupController` | 下单后清理入口 |
| `CartServiceImpl` | 编排商品核价与 Redis 存储；失效规格逐行隔离 |
| `CartCleanupServiceImpl` | 核对订单购买事实 + Lua 原子清理 |
| `CartRedisRepository` | Redis Hash 读写与两个 Lua 脚本执行，返回码转换 |
| `CartProperties` | 上限配置绑定，启动时校验合法性 |
| `CartSelectionKey` | 规格键规范化（base / 升序正整数 ≤10 段，≤210 字符） |
| `CartItemDO` | Hash 行的 JSON 模型（ID 按字符串处理，避免 JSON 库丢失 BIGINT 精度） |
| `CartSecurityConfig` / `CartSecurityErrorHandler` | 无状态 JWT + 黑名单 fail-closed + 统一 401/403 |

## 数据模型（Redis + Lua）

- 键：`cart:user:{userId}`（前缀可配），每用户一个 Hash；Field 为 `windowId:productId`（base 行）或 `windowId:productId:selectionKey`（规格行）；Value 为含数量、勾选、revision（每次增改换新 UUID）与名称/规格文字快照的 JSON。
- TTL：仅写入续期（默认 30 天），普通读取不续期；最后一项删除后空 Hash 自动清理。
- Lua 脚本（`src/main/resources/redis/`）：
  - `cart-mutate.lua`：单脚本完成 add/quantity/checked/remove/clearWindow 的校验与写入。返回码：不存在→404、容量超限→400、数量超限→400、数据损坏→503。容量检查与插入同脚本执行，并发加购也无法越过总行数；窗口清理先全量验证再集中删除，损坏行导致整窗失败而不部分清理。
  - `cart-cleanup.lua`：下单快照比较删除（≤100 条）。只有 revision、数量、勾选与快照完全一致的行才删除；缺失或已变化的行安全跳过；不刷新 TTL。

## 核心业务流程

### 加购与读写

1. 加购：校验规格选项（≤10、升序去重）→ 单行核价（规格失效直接拒绝写入）→ Lua 原子校验容量与数量后写入。
2. 读购物车：HGETALL 后逐行校验 Field 与内容一致性（错位视为数据损坏返回 503）→ 按窗口批量核价；批量报价失败退化为逐行隔离失效行。
3. 失效行以加入时快照展示、不参与金额合计，不阻塞其余行。

### 结账清理

1. 前端持原始快照调用清理接口（≤100 项、无重复）。
2. 持用户 Token 调订单服务核实订单：订单号、窗口、明细必须与快照完全一致，否则 409。
3. Lua 原子比较删除：仅删除用户未改动过的行；被用户改过的行因 revision 已变而安全跳过，可幂等重试。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 并发加购突破容量上限 | `cart-mutate.lua` 单脚本完成「校验 → 容量检查 → 写入」：容量检查与插入在同一原子脚本内，并发加购也无法越过总行数 | 超限返回 400（容量/数量），脚本内判定后写入 |
| 并发修改同一行 | Lua 脚本对整 Hash 的操作原子执行，行级 revision（UUID）在每次增改时刷新 | 后写覆盖先写（购物车语义允许）；revision 变化供清理比较 |
| 空窗口并发清理 | 清理脚本先全量验证再集中删除：损坏行导致整窗失败，不做部分清理 | 返回 503，避免误删用户数据 |
| 清理与用户改购物车竞态 | 下单清理 Lua 逐行比较 revision、数量、勾选与原始快照：只有用户未改动的行才删除，被改动的行安全跳过 | 跳过行不计入删除数，接口可幂等重试 |
| 快照比较失败重放 | 清理接口幂等：重复调用对已删除行安全跳过，返回删除/跳过计数 | 无需客户端去重 |
| 读取时的数据损坏 | HGETALL 后逐行校验 Field 与内容一致性，错位视为损坏返回 503 | 不静默跳过坏行，交由运维介入 |

## 接口清单

全部要求 CUSTOMER 角色：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cart` | 购物车（按窗口分组、实时价、失效原因、勾选合计） |
| POST | `/cart/windows/{windowId}/items` | 加购 |
| PUT | `/cart/windows/{w}/items/{p}` | 设置 base 行数量 |
| PUT | `/cart/windows/{w}/items/{p}/selections/{selectionKey}` | 设置规格行数量 |
| PATCH | `/cart/windows/{w}/items/{p}/checked` | base 行勾选 |
| PATCH | `/cart/windows/{w}/items/{p}/selections/{k}/checked` | 规格行勾选 |
| DELETE | `/cart/windows/{w}/items/{p}` | 删 base 行（幂等） |
| DELETE | `/cart/windows/{w}/items/{p}/selections/{k}` | 删规格行 |
| DELETE | `/cart/windows/{w}` | 清空一个窗口 |
| POST | `/cart/orders/{orderId}/cleanup` | 下单后按快照清理，返回删除/跳过计数 |

## 消息、异步与缓存设计

- 无任何 MQ 生产与消费；购物车数据本身就是缓存型存储，丢失后由用户重新加购，不做重建。
- 失效判断依赖实时核价而非事件订阅，不维护商品变更缓存。

## 安全设计

- 无状态 JWT + Redis 黑名单：黑名单返回非明确 false 一律拒绝（fail-closed）。
- 商品服务故障、降级或契约破坏统一 503，绝不伪装成商品下架、绝不使用前端价格兜底；Redis 故障同样 503。
- `/cart/**` 以外一律 `denyAll`；内部令牌未配置时拒绝。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `cart.max-items` | 100 | 每人购物车行数上限（与核价契约 100 项对齐） |
| `cart.max-quantity` | 99 | 单行数量上限 |
| `cart.time-to-live` | 30d | Hash TTL（写入续期） |
| `cart.key-prefix` | `cart:user:` | 键前缀 |
| `CART_SERVICE_PORT` | 9103 | 服务端口 |
| `internal.scope.token` | — | 服务间共享令牌 |

## 异常与失败行为

- 商品核价不可用：503，不写入；核价契约破坏：503。
- 订单核实失败：401/403/404 保留原码，网络错误 503。
- 数据损坏（Field 与内容错位）：503，由清理脚本整窗拒绝以避免误删。

## 关键代码入口

`CartRedisRepository` 与两个 Lua 脚本（原子性核心）→ `CartServiceImpl`（核价编排与失效隔离）→ `CartCleanupServiceImpl`（快照清理协议）。

## 接口语义与配套约定

- POST 加购不承诺请求幂等：结果未知时不要自动重试，可先查询确认或用 PUT 设定目标数量；同窗口同菜品同规格重复 POST 会累加数量。
- 添加不可售菜品返回 404；勾选接口不传 checked 返回 400，false 是有效值。
- 统计口径：totalQuantity 含失效与未勾选项，checkedQuantity/checkedAmount 只统计勾选且可售的项；空购物车返回空窗口数组与零值；多窗口合计仅用于展示，实际订单逐窗口创建。
- 清理调用顺序：下单前保存 productId/selectionKey/quantity/revision 快照，下单成功后用原快照调用清理（不重查替换 revision）；下单超时先以原幂等键重放订单确认成功后再清理。
- removedCount 按规格明细行计数（非按份数）；重试后已删条目计入 skippedCount，两次返回的计数可以不同。
- 配置环境变量：CART_MAX_ITEMS/CART_MAX_QUANTITY/CART_TTL/CART_KEY_PREFIX；修改配置后已有键不立即重设 TTL，下次修改才生效。
- 网关路由 `/api/cart/**`，服务直连路径 `/cart/**`。
- Redis 持久化依赖 AOF 与 Docker 数据卷；极端故障接受部分或全部丢失，不承诺零丢失。
- 旧 base 条目在菜品后来启用必选规格时：不自动代选，标记不可结算并提示重新选择。

## 已知限制与未验证项

- Redis 故障期间购物车完全不可用，无本地兜底。
- 无 MQ、无事件投影，商品下架信息只在读取时通过核价发现。

## 关联文档

- 根 README「跨模块业务链路」：加购 → 下单 → 清理链路。
- `product-service/README.md`：报价契约与核价接口。
