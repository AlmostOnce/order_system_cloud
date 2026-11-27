# gateway-service 网关服务

> 系统唯一对外入口：Spring Cloud Gateway 响应式网关（WebFlux）。默认端口 `9090`。承担登录 IP 限流、JWT 认证（含注销黑名单校验）、路径级放行、CORS、路由转发（含 WebSocket 升级）与接口文档聚合。不读取 JWT 私钥、不判定业务数据范围——细粒度授权由下游资源服务完成。

## 模块定位与职责边界

- 网关层只做「身份认证 + 路径级放行/拒绝」：JWT 有效性、issuer、注销黑名单。
- 角色权限、订单/档口等业务数据范围由下游服务校验；网关不保存任何业务状态。
- 路由规则全部来自 Nacos 配置（`gateway-service.yaml`），本地代码不含路由定义。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Cloud Gateway（WebFlux）、Spring Security + OAuth2 Resource Server（jose）、Actuator |
| 注册与配置 | Nacos Discovery + Nacos Config（`optional:nacos:gateway-service.yaml`） |
| 限流 | Spring Cloud Gateway RedisRateLimiter（Redis 令牌桶，reactive） |
| 文档 | springdoc-openapi webflux（聚合 14 个服务的 OpenAPI） |
| 共享模块 | common-response、common-redis |

- Redis：`127.0.0.1:9096`（db 0，2 秒超时），用于黑名单校验与令牌桶。
- JWT：RSA 公钥外置（读取失败拒绝启动）、issuer `http://localhost:9094`、黑名单前缀 `auth:access-token:blacklist:`。
- 无 profile 文件；`SWAGGER_ENABLED` 控制文档聚合。

## 代码结构与请求处理链

| 类 | 职责 |
|---|---|
| `LoginRateLimitGlobalFilter` | 全局过滤器（最高优先级 +100）：仅对登录接口做 IP 令牌桶限流 |
| `GatewaySecurityConfig` | WebFlux 安全链、CORS 配置源、含黑名单校验的 JWT 解码器 |
| `GatewayRateLimitConfig` | 令牌桶 Bean（参数非法启动即失败） |
| `GatewayAuthenticationEntryPoint` / `GatewayAccessDeniedHandler` | 统一 401/403 JSON 响应 |

请求链顺序：登录限流全局过滤器 → Spring Security（CORS 预检 → JWT 校验 → 放行/拒绝）→ Nacos 路由匹配（StripPrefix/RewritePath）→ 负载均衡转发。

## 路由设计（Nacos `gateway-service.yaml`）

**业务路由**（均 `StripPrefix=1`）：

| 前缀 | 目标服务 |
|---|---|
| `/api/auth/**` | auth-service |
| `/api/users/**` | user-service |
| `/api/orders/**` | order-service |
| `/api/products/**` | product-service |
| `/api/payments/**` | payment-service |
| `/api/files/**` | file-service |
| `/api/cart/**` | cart-service |
| `/api/notifications/**`、`/api/chat/**` | notification-service |
| `/api/search/**` | search-service |
| `/api/statistics/**` | statistics-service |
| `/api/recommendations/**` | recommendation-service |
| `/api/comments/**` | comment-service |
| `/api/coupons/**` | coupon-service |
| `/api/support/**` | support-service |

**文档路由**：14 条 `RewritePath` 路由（`/api/{svc}/v3/api-docs` → `/v3/api-docs`），必须排在业务路由之前（首个匹配生效）。库存服务只有文档路由、无业务路由——其对外仅 `/internal/stocks/**`，由订单服务直连并校验内部令牌，不进网关。

**WebSocket 路由**：`lb:ws://notification-service`，`/api/notifications/ws` → `/ws`。

## 认证与鉴权

- `NimbusReactiveJwtDecoder` RSA 公钥验签 + issuer 校验；解码后响应式查询 Redis 黑名单（jti）：无 jti 或命中黑名单一律 401（fail-closed）。
- 放行清单：

| 路径 | 说明 |
|---|---|
| `POST /api/auth/login`、`/auth/refresh`、`/auth/captcha` | 登录与令牌 |
| `POST /auth/register`、`/auth/registration/*`、`/auth/email-enrollment/*`、`/auth/password-reset/*` | 注册与凭据恢复 |
| `POST /api/users/me/deletion/cancel` | 注销撤回：冷静期内无正常令牌，凭 10 分钟一次性撤回令牌（校验在用户服务） |
| `GET /api/files/*/content` | 匿名图片内容 |
| `GET /api/notifications/ws` | WebSocket 握手（票据由通知服务校验） |
| `/actuator/health`、swagger | 健康与文档 |

- 显式拒绝：`POST /api/users`（注册必须经认证服务编排）、`POST /api/users/authenticate`（内部校验入口不对外）。
- 其余 `anyExchange().authenticated()`；CSRF/formLogin/httpBasic 关闭。

## 限流设计

- 仅精确匹配 `POST /api/auth/login`：Redis 令牌桶，键为 TCP 远端 IP（明确不信任 `X-Forwarded-For`）。
- 阈值：1 令牌/秒、桶容量 50、每次扣 10——短时最多连续 5 次，之后平均 10 秒 1 次；状态存 Redis，多网关实例共享额度。
- 被限流返回 429「登录请求过于频繁」并透传限流响应头；限流器 Redis 故障统一 503（fail-closed）。

## CORS 与 WebSocket

- CORS 仅注册到 `/api/**`：来源白名单可经环境变量覆盖（部署时替换为真实域名，不用通配符）；方法含 PATCH；允许头为 Authorization / Content-Type / Idempotency-Key；凭证模式关闭；预检缓存 1 小时。HTTP 与 WebSocket 共用同一来源校验。
- WebSocket：网关只负责升级转发与放行；鉴权完全由通知服务的 30 秒一次性票据完成。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `server.port` | 9090 | 网关端口 |
| `gateway.cors.allowed-origins` | 本地开发来源 | CORS 白名单 |
| `gateway.rate-limit.login.replenish-rate / burst-capacity / requested-tokens` | 1 / 50 / 10 | 登录限流 |
| `security.jwt.issuer / public-key-location / blacklist-key-prefix` | 见上文 | 验签与黑名单 |
| `spring.config.import` | `optional:nacos:gateway-service.yaml` | 路由来源 |

## 异常与失败行为

- 黑名单查询失败或无结果：包装为令牌异常返回 401，不降级放行。
- 限流器异常、无法识别客户端 IP：503。
- 公钥文件不可读、限流参数非法：启动失败（fail-fast）。
- 无熔断/降级组件；下游可用性由 Nacos 负载均衡与各服务自身的 fail-closed 设计保证。

## 关键代码入口

`GatewaySecurityConfig`（安全链、放行清单、黑名单解码器）→ `LoginRateLimitGlobalFilter`（限流键与 fail-closed）→ `docs/nacos/gateway-service.yaml`（路由权威配置）。

## 接口文档约定

- Swagger「Try it out」的请求固定发往网关（由各服务的 `springdoc.gateway-base-url` 与网关前缀决定），直连各服务打开的文档页同样回注网关地址。
- `SWAGGER_ENABLED=false` 时 `/v3/api-docs` 与 `/swagger-ui/**` 返回 404，开关同时作用于网关与各业务服务。

## 已知限制与未验证项

- 路由发布依赖 Nacos 运维脚本，本地仓库中的 yaml 仅为发布源。
- 限流仅覆盖登录入口，注册/刷新等匿名接口无独立限流。
- 多网关实例部署时的会话无关性已满足（无状态），但未做负载均衡验证。

## 关联文档

- `auth-service/README.md`：登录与令牌签发。
- `notification-service/README.md`：WebSocket 票据协议。
- 根 README「系统架构」：入口拓扑。
