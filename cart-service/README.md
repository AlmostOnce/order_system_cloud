# cart-service 购物车服务

> 基于 Redis Hash 的顾客购物车，负责按窗口组织条目、实时核价和下单后的安全清理。默认端口 `9103`，不建业务库。

## 职责与边界

- 按用户隔离保存购物车条目，条目关联窗口、菜品、数量、勾选状态、名称快照、规格选择与版本号。
- 读取时按窗口批量调用商品服务核价；失效或下架商品保留展示，但不计入结算。
- 支持添加、设置数量、修改勾选、删除单项、清空窗口、修改规格选择。
- 下单成功后按订单服务返回的原始快照执行条件清理，只删除未发生变化的条目。

**边界：** 购物车不预占库存、不锁定价格；下单时订单服务仍会重新核价并扣减库存。购物车不拥有商品事实，价格与可售状态以商品服务为准。

## 存储模型

- 每个用户一个 Redis Key：`cart:user:{userId}`（前缀可配置）。
- Hash field 由窗口 ID 与菜品 ID（含规格选择键）组合，value 为 JSON：数量、勾选、名称快照、规格、`revision`。
- 整个用户购物车的 TTL 作用于 Key，而不是逐个字段；默认 30 天，读取不续期，实际修改时按脚本规则续期。
- 默认限制：每个用户最多 100 类条目、单项数量最多 99；上限与商品批量核价最多 100 个 ID 对齐。

## Lua 脚本

| 脚本 | 作用 |
|---|---|
| [`cart-mutate.lua`](src/main/resources/redis/cart-mutate.lua) | 在 Redis 内完成存在性、容量、数量校验与写入，并生成新 `revision` |
| [`cart-cleanup.lua`](src/main/resources/redis/cart-cleanup.lua) | 先校验全部候选条目，再删除数量、勾选、版本均未变化的已购买项 |

Lua 保证脚本执行期间不被其他客户端命令插入，但脚本中途报错不会回滚已经成功的写入；因此清理脚本采用“先全部校验、后统一删除”。业务 ID 在 Lua 中按字符串处理，避免大整数经浮点数丢精度。

## 写入与清理流程

1. 业务层校验用户、窗口、菜品、规格和数量。
2. `CartRedisRepository` 执行购物车专用 Lua，完成容量检查与写入。
3. 每次修改生成新的 `revision`，用于判断下单后用户是否又修改过购物车。
4. 下单成功后，`POST /cart/orders/{orderId}/cleanup` 先向订单服务核对本人订单及完整购买明细。
5. 比较快照中的版本、数量与勾选状态，只删除未变化条目；已变化条目保留，避免误删用户新意图。
6. 清理失败不回滚订单，应重试清理而不是重新下单。

## 主要接口

| 方法与路径 | 功能 |
|---|---|
| `GET /cart` | 按窗口读取购物车与最新核价结果 |
| `POST /cart/windows/{windowId}/items` | 添加商品 |
| `PUT /cart/windows/{windowId}/items/{productId}` | 设置目标数量 |
| `PUT /cart/windows/{windowId}/items/{productId}/selections/{selectionKey}` | 修改规格选择 |
| `PATCH /cart/windows/{windowId}/items/{productId}/checked` | 设置勾选状态 |
| `PATCH .../selections/{selectionKey}/checked` | 按规格项设置勾选 |
| `DELETE /cart/windows/{windowId}/items/{productId}` | 删除单项 |
| `DELETE .../selections/{selectionKey}` | 删除某个规格项 |
| `DELETE /cart/windows/{windowId}` | 清空该窗口 |
| `POST /cart/orders/{orderId}/cleanup` | 根据下单快照安全清理 |
| `GET /cart/ping` | 健康探测 |

添加是累加操作，不保证请求幂等；结果不确定时应先查询再决定是否重试。设置数量是设置目标值，删除是幂等操作。

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`CartController.java`](src/main/java/com/hbue/ordering/cart/controller/CartController.java) | 购物车 HTTP 接口 |
| [`CartServiceImpl.java`](src/main/java/com/hbue/ordering/cart/service/impl/CartServiceImpl.java) | 读取核价与写入编排 |
| [`CartCleanupServiceImpl.java`](src/main/java/com/hbue/ordering/cart/service/impl/CartCleanupServiceImpl.java) | 下单后清理 |
| [`CartRedisRepository.java`](src/main/java/com/hbue/ordering/cart/repository/CartRedisRepository.java) | Redis Hash 与 Lua 执行 |
| [`CartProperties.java`](src/main/java/com/hbue/ordering/cart/config/CartProperties.java) | 容量、数量、TTL、Key 前缀配置 |
| [`CartFeignConfiguration.java`](src/main/java/com/hbue/ordering/cart/config/CartFeignConfiguration.java) | 调用商品/订单服务的客户端配置 |

## 配置与排查

- 配置前缀 `cart.*`：`max-items`、`max-quantity`、`time-to-live`、`key-prefix`；非法值在启动时直接失败。
- 价格显示异常：先检查商品服务报价接口与购物车核价结果，不要把故障误报为商品下架。
- 下单后购物车未清理：检查清理接口是否被调用、订单快照是否完整、条目是否因版本变化被保留。
- 详细接口规则见 [`docs/CART_API.md`](../docs/CART_API.md)。
