# 购物车模块实施计划

更新时间：2026-10-17。按用户本次安排，阶段 B 先开发购物车，再继续库存。

## 已确认方案

- 独立服务 `cart-service`，包名 `com.hbue.ordering.cart`；默认端口 9103，可用 `CART_SERVICE_PORT` 覆盖，旧端口段设备可设置为 8088。
- Gateway 路由 `/api/cart/**`，直连服务路径 `/cart/**`。JWT 资源服务器校验 RSA 签名、签发方、有效期、jti 和 Redis 黑名单；购物车操作要求 CUSTOMER 角色。
- 用户 ID 取自已验证 JWT 的 sub，不接收前端指定的购物车归属用户。
- Redis 是购物车唯一业务存储，不建设 cart_db 或 MySQL 备份；同账号跨设备访问通过共享 Redis 实现。使用 AOF 与 Docker 数据卷恢复普通重启后的数据，接受极端故障下购物车部分或全部丢失，不承诺零丢失。
- 仅已登录且具有 CUSTOMER 角色的用户可使用购物车，不开发游客购物车或登录合并。后续前端通过路由守卫拦截未登录访问，后端继续独立校验 JWT，不能仅依赖前端保护。
- 按用户存储、按窗口展示及结算；同一窗口同一菜品合并，一次订单只包含一个窗口。
- Redis Hash Key 为 `cart:user:{userId}`，Field 为 `windowId:productId`；明细保存数量、勾选状态与修改版本。默认修改续期 30 天，读取不续期；上限配置化。
- Lua 原子完成数量与条目上限校验及更新，不以 Java 读取后覆盖的方式处理并发累加。
- 商品服务提供当前可售状态与价格，购物车展示金额只供预览；订单创建时重新核价，购物车不占库存。
- 只被前端调用时不建 cart-api；实际需要商品远程调用时再引入已有 product-api、OpenFeign 与对应降级配置。
- 新增业务沿用 Controller → Service 接口 → Impl；请求 DTO、展示 VO 和内部状态模型分包，代码使用中文 Javadoc 与关键步骤 `//` 注释。

## 实施步骤

1. **服务骨架（2026-10-17 已完成）**：父工程注册、Nacos 服务发现、Redis、JWT、统一响应、`GET /cart/ping`、Swagger 与网关配置。全量 Maven 测试和真实服务注册、健康检查、网关文档转发已通过；临时实例已停止，继续由 IDEA 启动。
2. **Redis 基础操作（2026-10-17 已实现）**：六个业务接口、Service/Impl、DTO/VO 与 Redis DO 已落地；Lua 原子处理容量、数量和 TTL，ID 字符串保存以避免大主键精度丢失。
3. **商品集成（2026-10-17 已实现）**：添加时核验窗口和菜品，GET 按窗口批量核价；失效项保留且金额为 null，不计入可结算合计；Feign 透传顾客 JWT、配置超时和 Sentinel 降级。详见 [购物车接口](CART_API.md)。
4. **订单衔接（2026-10-17 已实现后端清理接口）**：继续使用 `POST /orders` 与 Idempotency-Key；客户端下单前保存商品 ID、数量和 revision，成功后显式调用 `POST /cart/orders/{orderId}/cleanup`。购物车通过 order-api 读取当前顾客自己的订单，核对窗口和完整购买明细，再用 Lua 比较原版本、数量和勾选状态。并发修改条目保留，失败可用同一订单和原快照重试；不自动创建订单、不直接清空整个购物车，前端串联仍需调用方接入。
5. **运行保障**：保留 Redis AOF 与 /data 命名卷，不将持久化等同于独立灾备；不新增 MySQL 双存储、游客购物车或登录合并。

## 首批业务接口（第二步实现）

| 方法 | 服务路径 | 职责 |
|---|---|---|
| GET | `/cart` | 查询分组后的购物车 |
| POST | `/cart/windows/{windowId}/items` | 添加菜品，已有则累加 |
| PUT | `/cart/windows/{windowId}/items/{productId}` | 设置目标数量 |
| PATCH | `/cart/windows/{windowId}/items/{productId}/checked` | 修改勾选状态 |
| DELETE | `/cart/windows/{windowId}/items/{productId}` | 删除单项 |
| DELETE | `/cart/windows/{windowId}` | 清空一个窗口 |
| POST | `/cart/orders/{orderId}/cleanup` | 核实已有订单后，按原始提交版本安全清理 |

## 骨架启动与路由生效

1. 在 IDEA 重新加载根 Maven 工程，运行 `com.hbue.ordering.cart.CartServiceApplication`；无需新增 MySQL 库或中间件容器。
2. 启动时沿用本机的 Nacos、Redis、JWT 公钥环境变量。应用自身不加载 `.env`，IDEA 需要配置对应环境变量。
3. 将仓库 `docs/nacos/gateway-service.yaml` 中新增的 cart-service-docs、cart-service 路由合并到 Nacos 的同名配置。仅修改仓库文件不会自动发布到 Nacos，合并时保留在线已有路由。
4. Gateway 重新启动后加载新增 Swagger 分组；服务可先直连验证，网关路由发布后再验证网关入口。
5. 携带有效 CUSTOMER Bearer Token，直连 `http://127.0.0.1:9103/cart/ping` 或经网关访问 `http://127.0.0.1:9090/api/cart/ping`。
6. 网关文档地址 `/api/cart/v3/api-docs`；旧静态 Postman 导出尚不包含购物车，可从该地址实时导出。

本机执行记录：2026-10-17 已增量发布并回读验证两条购物车路由；在线配置原有路由全部保留。换设备或换 Nacos 命名空间时仍需执行发布步骤。

## 验收边界

- 骨架、核心增删改查、商品核价和订单创建后的安全清理接口已实现；调用方需按 CART_API.md 串联下单和清理，不能把提供后端接口等同于前端已完成接入。MySQL 备份与游客合并已按用户确认移出范围。
- 需要验证缺少/无效令牌返回统一 401、非顾客角色返回统一 403、顾客请求成功，以及黑名单与 Redis 故障拒绝认证。
- 发布、注册和真实 JWT 的端到端验证以实际运行结果为准，不能把单元测试通过当作 Nacos 发布成功。
