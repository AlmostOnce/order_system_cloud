# 购物车接口与当前实现

更新时间：2026-10-17。所有业务接口要求 CUSTOMER Access Token；统一经 Gateway 的
`http://localhost:9090/api/cart` 访问，直连开发端口为 9103。

## 实现边界

- 已实现按用户隔离的 Redis 购物车、按窗口分组、数量与种类限制、勾选、删除，以及商品实时核验。
- 请求中不接收价格，用户 ID 仅从 JWT sub 读取；同一用户多个设备共享 Redis 状态。
- 新增默认勾选；同窗口同菜品重复 POST 会累加，POST 本身不承诺请求幂等。不要对结果未知的添加请求自动重试，可先查询确认数量，或用 PUT 设定明确目标值。
- 修改数量和勾选只更新对应字段，Lua 原子执行，避免并发请求互相覆盖。默认每项最多 99 份、每个用户最多 100 类菜品（所有窗口合计）。
- Redis 的 30 天 TTL 在发生修改后续期，GET 不续期；删除不存在的条目不续期，也不会创建空购物车。
- 查询时每个窗口调用一次商品批量核价。商品改价立即影响预览；停用窗口、下架菜品和失效分类对应的条目保留，`available=false`，单价与行金额为 null，不计入可结算合计。
- 商品服务故障返回 HTTP 503，不把整批菜品误标成下架，不退回旧价格。删除、清空、数量与勾选修改只依赖 Redis，允许用户清理失效项。
- 价格仅用于预览，不做库存预占。已提供下单后的安全清理接口，调用方需显式串联；并非创建订单后自动触发。Redis 是唯一业务存储，不建设 MySQL 备份，也不支持游客购物车及登录合并。

## 请求示例

Postman 中选择 Authorization → Bearer Token，填入顾客的 Access Token。
示例窗口 1、菜品 11 需替换成当前商品菜单中真实可售的 ID。

### 添加菜品

`POST /api/cart/windows/1/items`，Body → raw → JSON：

```json
{
  "productId": 11,
  "quantity": 2
}
```

成功返回 HTTP 200：

```json
{"code":"COMMON_000","message":"操作成功","data":null}
```

再次提交相同请求会增加到 4 份，累加后的数量仍受 Lua 中的原子上限检查保护。

### 查询购物车

`GET /api/cart`，成功的 `data` 结构示例：

```json
{
  "windows": [
    {
      "windowId": 1,
      "windowActive": true,
      "items": [
        {
          "productId": 11,
          "productName": "鸡肉饭",
          "productCode": "RICE-11",
          "quantity": 2,
          "checked": true,
          "revision": "每次修改都会变化的 UUID",
          "available": true,
          "unavailableReason": null,
          "unitPrice": 18.50,
          "lineAmount": 37.00
        }
      ],
      "checkedAmount": 37.00
    }
  ],
  "totalQuantity": 2,
  "checkedQuantity": 2,
  "checkedAmount": 37.00
}
```

`totalQuantity` 含失效和未勾选项；`checkedQuantity`、`checkedAmount` 仅统计勾选且可售项。
空购物车返回 `windows=[]` 及零数量、零金额。多个窗口的合计只用于展示，实际订单逐窗口创建。

### 修改数量

`PUT /api/cart/windows/1/items/11`：

```json
{"quantity":3}
```

这是设置目标值，多次提交仍为 3 份。目标条目不存在时返回 404，不会变为新增。
数量零或超过配置上限返回 400；需要删除时调用 DELETE。

### 修改勾选

`PATCH /api/cart/windows/1/items/11/checked`：

```json
{"checked":false}
```

false 为有效值；不传 checked 返回 400。不可售条目即使勾选也不计入结算金额。

### 删除与清空

- `DELETE /api/cart/windows/1/items/11`：只删除当前用户这一项。
- `DELETE /api/cart/windows/1`：只清空当前用户窗口 1，不影响窗口 10 或其它窗口。
- 两者均幂等，成功返回 HTTP 200 和 `data=null`。

## 错误约定

| HTTP | 业务码 | 场景 |
|---|---|---|
| 400 | COMMON_400 | 缺失或非法参数、容量超限、数量超限、路径 ID 不是数字 |
| 401 | COMMON_401 | 无效或注销 Token、非法 JWT 用户身份 |
| 403 | COMMON_403 | 缺少 CUSTOMER 角色 |
| 404 | COMMON_404 | 添加不可售菜品、修改不存在的购物车条目 |
| 409 | COMMON_409 | 清理快照的窗口、商品或数量与实际订单不一致 |
| 503 | COMMON_503 | Redis 故障、购物车数据损坏、商品服务故障或核价响应异常 |

Redis 若在 JWT 黑名单检查阶段已经不可用，会沿用资源服务器的失败关闭规则返回 401；
503 指通过身份校验后，在购物车业务存储或商品调用阶段发生的故障。

## 下单成功后清理（第七个业务接口）

`POST /api/cart/orders/{orderId}/cleanup`。要求同一顾客的有效 Bearer Token，
不接收 userId。购物车会通过 order-api 透传当前 Token 调用订单服务已有的
`GET /orders/{orderId}`，核对订单归属、窗口及完整购买明细；不存在或越权统一 404。
订单服务故障返回 503，身份失效沿用 401/403，均不会删除购物车。

调用顺序：

1. `GET /api/cart` 后，在下单前保存本次窗口内选中且可售商品的 productId、quantity、revision。
2. 使用原有 `POST /api/orders` 创建订单，提交同一窗口及同一批商品数量，携带 `Idempotency-Key`。
3. 收到成功的订单 ID 后，用第一步保存的快照调用清理接口，不重新查询并替换 revision。
4. 清理失败只重试清理，不重新下单；若下单本身超时、结果未知，先用原幂等键与相同订单请求重放，确认订单成功后再清理。

例如订单 123 购买了窗口 1 的商品 11 共 2 份，请求体如下；所有 ID 和 revision
必须替换为本次真实订单及下单前取得的值，不能直接照抄占位版本：

```json
{
  "windowId": 1,
  "items": [
    {"productId": 11, "quantity": 2, "revision": "下单前保存的原始UUID版本"}
  ]
}
```

成功响应的 data：

```json
{"orderId":123,"removedCount":1,"skippedCount":0}
```

- removedCount 按商品条目计数，不按份数计数。
- skippedCount 包含已不存在、版本变化、数量不符或取消勾选的项。跳过是安全保护，不代表订单失败。
- 同一请求重试后，已删除条目计入 skippedCount；操作效果幂等，但两次返回的计数可以不同。
- 修改数量后再改回、取消再勾选、删除后重新添加都会生成新 revision，因此旧请求不会误删新意图。
- Lua 先检查所有候选再删除，坏数据返回 503 时不发生部分删除；清理不延长剩余购物车 TTL。
- 清理成功只影响购物车，不改变订单状态、支付或库存；取消订单也不会自动把商品加回购物车。
- 当前没有前端接入、消息驱动或后台补偿；若调用方永远不发送清理请求，商品会留在购物车直到主动删除或过期。

## 代码分层

- `controller/CartController`：请求与身份解析，输出统一响应。
- `service/CartService`、`service/impl/CartServiceImpl`：参数规则、商品核验、分组及 BigDecimal 金额聚合。
- `repository/CartRedisRepository`、`resources/redis/cart-mutate.lua`：Hash 存取、并发限制、版本与 TTL。
- `model/CartItemDO`：Redis 持久化对象；`dto/request` 与 `vo` 分别管理请求、展示对象。
- `config/CartFeignConfiguration`：代表当前顾客调用商品服务时透传 Authorization。
- `controller/CartCleanupController` → `service/CartCleanupService` → `service/impl/CartCleanupServiceImpl`：单独编排订单事实校验及清理；复用 order-api，只读调用订单服务，不依赖其实现模块。
- `resources/redis/cart-cleanup.lua`：按提交快照原子比较与删除，不影响其它用户、窗口或后续修改。
- 不建设购物车 MySQL 表，因此不使用 MyBatis-Plus 的 IService/ServiceImpl；Redis 持久化由 AOF 与 Docker 数据卷承担，不承诺极端故障零数据丢失。

## 配置与测试

容量与 TTL 在 `cart-service/src/main/resources/application.yml` 的顶层 `cart` 下配置，
对应环境变量为 `CART_MAX_ITEMS`、`CART_MAX_QUANTITY`、`CART_TTL`、`CART_KEY_PREFIX`。
已有 Redis Key 不会因改配置立即重设 TTL，下次修改时使用新 TTL。

通常运行 `mvn -pl cart-service -am test`，不会要求本机 Redis 在线。
真实 Redis 集成测试需设置 `CART_REDIS_IT=true` 与 `CART_TEST_REDIS_HOST`、
`CART_TEST_REDIS_PORT`、`CART_TEST_REDIS_PASSWORD` 后运行相同命令；测试使用随机前缀，
仅清理其自身创建的两个用户 Key，不使用 FLUSHDB 或扫描业务 Key。

代码更新后在 IDEA 重新加载 Maven 并重启 cart-service，确保 product-service、order-service、
user-service（现有订单详情依赖）、Nacos、Redis 和 Gateway 在线。原有购物车路由无需再次修改，
Swagger 会随新 Controller 自动展示七个业务接口。

本轮验证：开启真实 Redis 集成测试后，全项目 15 个 Maven 模块通过；购物车 59 项测试
全部通过，含 16 项真实 Redis 用例（包括清理与加购并发）。另有订单生产方真实 JSON 契约测试，
以及 Feign 超时、路径、凭据透传和反序列化的实际装配测试。受影响模块 Maven install 成功。
本轮未重启 IDEA 实例，未创建真实订单或清理业务购物车；真实顾客经运行中网关的完整联调尚未执行。
