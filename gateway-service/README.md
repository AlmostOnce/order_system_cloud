# gateway-service 网关服务技术设计

> 本文件描述 `gateway-service` 的实际实现，不是规划稿。所有结论来自本模块源码、配置与仓库内 Nacos 配置样例；
> 标注“源码未体现”的内容表示在本模块源码中不存在对应实现。

## 0. 文档依据

| 类别 | 文件 |
|---|---|
| 启动入口 | [GatewayServiceApplication.java](src/main/java/com/hbue/ordering/gateway/GatewayServiceApplication.java) |
| 安全与 CORS | [GatewaySecurityConfig.java](src/main/java/com/hbue/ordering/gateway/config/GatewaySecurityConfig.java) |
| 限流 Bean | [GatewayRateLimitConfig.java](src/main/java/com/hbue/ordering/gateway/config/GatewayRateLimitConfig.java) |
| 限流过滤器 | [LoginRateLimitGlobalFilter.java](src/main/java/com/hbue/ordering/gateway/filter/LoginRateLimitGlobalFilter.java) |
| 401 处理 | [GatewayAuthenticationEntryPoint.java](src/main/java/com/hbue/ordering/gateway/security/GatewayAuthenticationEntryPoint.java) |
| 403 处理 | [GatewayAccessDeniedHandler.java](src/main/java/com/hbue/ordering/gateway/security/GatewayAccessDeniedHandler.java) |
| 单元测试 | [LoginRateLimitGlobalFilterTest.java](src/test/java/com/hbue/ordering/gateway/filter/LoginRateLimitGlobalFilterTest.java)、[GatewayCorsConfigTest.java](src/test/java/com/hbue/ordering/gateway/config/GatewayCorsConfigTest.java) |
| 本模块配置 | [application.yml](src/main/resources/application.yml) |
| 依赖声明 | [pom.xml](pom.xml) |
| 路由配置样例 | [docs/nacos/gateway-service.yaml](../docs/nacos/gateway-service.yaml) |
| 被引用的公共类 | [ReactiveRedisOperationsService.java](../common-redis/src/main/java/com/hbue/ordering/common/redis/ReactiveRedisOperationsService.java)、[CommonErrorCode.java](../common-core/src/main/java/com/hbue/ordering/common/core/error/CommonErrorCode.java)、[ApiResponse.java](../common-response/src/main/java/com/hbue/ordering/common/response/ApiResponse.java) |

说明：本仓 `application.yml` 中不含任何 `spring.cloud.gateway...routes` 片段，路由 100% 来源于 Nacos 配置中心；
仓库内 [docs/nacos/gateway-service.yaml](../docs/nacos/gateway-service.yaml) 是可版本管理的配置样例，不等于运行中 Nacos 的实际内容。

## 1. 模块定位与职责边界

### 1.1 定位

`gateway-service` 是系统唯一的对外 HTTP/WebSocket 入口，基于 Spring Cloud Gateway 的 WebFlux 响应式栈运行，
服务名 `gateway-service`，默认端口 `9090`。它不做业务计算，只做入口级治理：
路由转发、JWT 入口校验、注销黑名单检查、登录接口限流、统一 401/403 输出、CORS、接口文档聚合。

### 1.2 做什么

| 能力 | 实现位置 | 说明 |
|---|---|---|
| 按路径把 `/api/**` 分发到业务服务 | Nacos 路由配置 + `lb://` | 服务实例由 Nacos 注册发现，Spring Cloud LoadBalancer 选择 |
| 校验 Access Token | `GatewaySecurityConfig.jwtDecoder` | RSA 公钥验签、issuer、时间窗口、`jti`、Redis 黑名单 |
| 匿名路径白名单 | `GatewaySecurityConfig.securityWebFilterChain` | 登录、注册、验证码、刷新、找回密码、WebSocket 握手等 |
| 显式拒绝两个用户写入入口 | 同上 | `POST /api/users`、`POST /api/users/authenticate` 使用 `denyAll()` |
| 登录接口 IP 限流 | `LoginRateLimitGlobalFilter` | Redis 令牌桶，仅 `POST /api/auth/login` |
| 统一未认证/无权限响应 | `GatewayAuthenticationEntryPoint`、`GatewayAccessDeniedHandler` | 返回 `ApiResponse`，不返回默认 HTML 错误页 |
| 浏览器跨域处理 | `GatewaySecurityConfig.corsConfigurationSource` | 仅对 `/api/**` 注册 CORS 规则 |
| Swagger UI 聚合 | `application.yml` 的 `springdoc.swagger-ui.urls` | 一个页面切换 13 个服务文档地址 |

### 1.3 不做什么

| 不做的事 | 依据 |
|---|---|
| 不保存业务数据、不建业务库 | 架构服务表标注 gateway-service 存储为“无业务库”，见 [ARCHITECTURE.md](../docs/ARCHITECTURE.md) |
| 不判断角色、数据范围、资源归属 | `GatewaySecurityConfig` 类注释明确“不判定订单、档口等业务数据范围，细粒度授权由业务服务完成” |
| 不聚合业务接口、不做 BFF 编排 | 源码只存在一个自定义 `GlobalFilter`（登录限流），无聚合或改写响应体逻辑 |
| 不缓存业务响应 | 源码无缓存过滤器、无 `ResponseBody` 改写 |
| 不实现重试、熔断、降级 | `application.yml` 未配置 `spring.cloud.gateway...httpclient`、无 Resilience4j/Sentinel 相关依赖 |
| 不写身份透传 Header | 源码未新增 `X-User-Id` 等 Header，`Authorization` 原样透传给下游 |
| 不承载 `/internal/**`、`/mock/**` | Nacos 样例只为 `/api/**` 建路由，内部接口按约定直连服务端口 |

### 1.4 为什么网关只做粗粒度校验

1. 网关没有业务库连接，无法在一次请求内加载订单归属、档口范围、角色数据范围等授权上下文。
2. 细粒度授权依赖资源当前状态（例如订单状态、券是否已领取），必须在业务服务的事务边界内判断，放在网关会引入过期判断。
3. 网关是全局单点，每次请求只做无状态验签加一次 Redis `EXISTS`，把业务查询前移会放大延迟与故障面。
4. 每个业务服务自身仍是资源服务器（配置同一公钥与同一黑名单前缀），网关被绕过直连端口时鉴权依然生效，形成纵深防御。

## 2. 技术栈与运行参数

### 2.1 技术栈

| 项 | 值 | 依据 |
|---|---|---|
| JDK | 21 | 根 [pom.xml](../pom.xml) 的 `java.version` |
| Spring Boot | 3.5.6 | 根 pom 的 `spring-boot.version` |
| Spring Cloud | 2025.0.0 | 根 pom 的 `spring-cloud.version` |
| Spring Cloud Alibaba | 2025.0.0.0 | 根 pom 的 `spring-cloud-alibaba.version` |
| Spring Cloud Gateway Server | 4.3.0 | 依赖 `spring-cloud-starter-gateway` 由上述 BOM 解析 |
| 安全 | Spring Security WebFlux + OAuth2 Resource Server + `spring-security-oauth2-jose` | [pom.xml](pom.xml) |
| 限流 | `spring-cloud-gateway-server` 的 `RedisRateLimiter` + 响应式 Redis | `GatewayRateLimitConfig`、`spring-boot-starter-data-redis-reactive` |
| 文档 | `springdoc-openapi-starter-webflux-ui` | [pom.xml](pom.xml) |
| 运行模型 | 纯 WebFlux，无 Servlet | 依赖中不引入 `spring-boot-starter-web` |

### 2.2 运行参数

| 参数 | 默认值 | 来源 |
|---|---|---|
| HTTP 端口 | `9090` | `server.port` |
| 服务名（Nacos 服务名） | `gateway-service` | `spring.application.name` |
| 注册中心 | `127.0.0.1:9097`，启用 | `spring.cloud.nacos.discovery.server-addr`、`enabled: true` |
| 配置中心 | `127.0.0.1:9097` | `spring.cloud.nacos.config.server-addr` |
| Nacos 账号 | `nacos` / `root` | `spring.cloud.nacos.username`、`password` |
| Nacos gRPC 长连接 | `10097` | 由 Nacos 客户端按注册中心端口 +1000 推导，本仓库未显式声明该端口 |
| 配置文件导入 | `optional:nacos:gateway-service.yaml` | `spring.config.import` |
| Redis | `127.0.0.1:9096`，库 `0`，连接/命令超时各 `2s` | `spring.data.redis.*` |
| Redis 密码 | `order_redis_dev_2026` | `spring.data.redis.password` 默认值 |
| Spring Profile | 未定义任何 profile，也无 `application-*.yml` | 模块资源目录仅有 `application.yml` |

`optional:` 前缀的含义：Nacos 中不存在 `gateway-service.yaml` 时启动不会失败，但此时没有任何路由，
所有 `/api/**` 请求返回 404。生产与联调环境必须确认该配置已发布。

启动入口在 `main` 中先写入 `JM.LOG.PATH`（默认 `~/logs/order-system/gateway-service`，已存在则不覆盖），
用于隔离 Nacos 客户端的本地日志文件，避免多服务在同一台 Windows 机器上滚动同一文件冲突。

### 2.3 关键环境变量

| 变量 | 默认值 | 作用 |
|---|---|---|
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `9096` | Redis 端口 |
| `REDIS_PASSWORD` | `order_redis_dev_2026` | Redis 密码 |
| `NACOS_SERVER_ADDR` | `127.0.0.1:9097` | 注册中心与配置中心地址 |
| `NACOS_USERNAME` | `nacos` | Nacos 账号 |
| `NACOS_PASSWORD` | `root` | Nacos 密码 |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | 见第 8 节默认来源列表 | 覆盖 CORS 白名单 |
| `SWAGGER_ENABLED` | `true` | 同时控制 `springdoc.api-docs.enabled` 与 `springdoc.swagger-ui.enabled` |

## 3. 代码结构与分层

```text
gateway-service
├─ src/main/java/com/hbue/ordering/gateway
│  ├─ GatewayServiceApplication.java          启动入口
│  ├─ config/GatewaySecurityConfig.java       JWT 解码、安全链、CORS、401/403 装配
│  ├─ config/GatewayRateLimitConfig.java      登录限流器 Bean 与参数校验
│  ├─ filter/LoginRateLimitGlobalFilter.java  登录接口 IP 限流全局过滤器
│  └─ security/GatewayAuthenticationEntryPoint.java  401 响应
│     security/GatewayAccessDeniedHandler.java       403 响应
├─ src/main/resources/application.yml
└─ src/test/java/...                          限流与 CORS 单测
```

模块没有 Controller、Service、Repository、Mapper 分层；全部行为集中在“安全配置 + 过滤器 + 异常处理器”，
这是网关类服务的正常形态：入口逻辑由框架扩展点承载，业务分层由下游服务承担。

### 3.1 逐类说明

| 类 | 职责 | 输入 | 输出 | 依赖 |
|---|---|---|---|---|
| `GatewayServiceApplication` | 启动 Spring Boot 与 `@EnableDiscoveryClient`；显式 `@Import` Redis 响应式组件 | 启动参数 | 运行中的应用上下文 | `ReactiveRedisOperationsService` |
| `GatewaySecurityConfig` | 构建 `SecurityWebFilterChain`；提供 `CorsConfigurationSource` 与 `ReactiveJwtDecoder` Bean | 安全处理器、公钥资源、issuer、黑名单前缀、允许来源 | 安全链、CORS 源、JWT 解码器 | `NimbusReactiveJwtDecoder`、`ReactiveRedisOperationsService` |
| `GatewayRateLimitConfig` | 创建 `RedisRateLimiter`（Bean 名 `loginRedisRateLimiter`），启动时校验参数 | 三个 `int` 配置 | `RedisRateLimiter` | Gateway 的 `RedisRateLimiter` |
| `LoginRateLimitGlobalFilter` | 对精确登录路径做 IP 维度令牌桶限流，写入 429/503 | `ServerWebExchange`、`GatewayFilterChain` | 继续链路或直接写响应 | `RedisRateLimiter`、`ObjectMapper`、`ApiResponse` |
| `GatewayAuthenticationEntryPoint` | 未认证统一 401 JSON | `ServerWebExchange`、`AuthenticationException` | 401 + `ApiResponse` | `ObjectMapper` |
| `GatewayAccessDeniedHandler` | 无权限统一 403 JSON | `ServerWebExchange`、`AccessDeniedException` | 403 + `ApiResponse` | `ObjectMapper` |

### 3.2 启动类要点

`@Import(ReactiveRedisOperationsService.class)` 是必要动作：组件扫描根为 `com.hbue.ordering.gateway`，
`com.hbue.ordering.common.redis` 不在扫描范围内。该类虽然带 `@Component`，仍需通过 `@Import` 显式注册，
否则 `GatewaySecurityConfig` 构造 JWT 解码器时缺少 `ReactiveRedisOperationsService` 依赖。

### 3.3 安全配置要点

`GatewaySecurityConfig` 同时声明三个 Bean，职责必须区分：

1. `securityWebFilterChain(ServerHttpSecurity, CorsConfigurationSource)`：关闭 CSRF、表单登录、HTTP Basic；
   注册 CORS；装配 401/403 处理器；声明 URL 级访问规则；启用 OAuth2 Resource Server 的 JWT 支持。
2. `corsConfigurationSource(List<String> allowedOrigins)`：唯一跨域规则来源，只注册到 `/api/**`。
3. `jwtDecoder(Resource, String, ReactiveRedisOperationsService, String)`：读取 X.509 RSA 公钥，构建
   `NimbusReactiveJwtDecoder`，设置 `JwtValidators.createDefaultWithIssuer(issuer)`，再包一层黑名单校验。

公钥读取使用 try-with-resources；读取失败抛出 `IllegalStateException("读取 JWT RSA 公钥失败")`，
让应用启动失败。设计意图是绝不以无法验签的状态对外提供路由服务。公钥格式错误时
`RsaKeyConverters.x509()` 产生的运行时异常不被吞掉，同样导致启动失败。

`jwtDecoder` 返回的是 `ReactiveJwtDecoder` 函数式实现：`token -> decodeAndCheckBlacklist(...)`。
黑名单判断挂在解码之后，因此签名错误、issuer 不符、过期令牌不会触发 Redis 查询。

### 3.4 限流配置要点

`GatewayRateLimitConfig.loginRedisRateLimiter` 在构造前校验：
`replenishRate >= 1`、`burstCapacity >= 1`、`requestedTokens >= 1`、`requestedTokens <= burstCapacity`，
任一不满足即抛 `IllegalArgumentException` 阻止启动。该 Bean 是容器中唯一的 `RedisRateLimiter`：
Spring Cloud Gateway 自动配置中的 `redisRateLimiter` 标注了 `@ConditionalOnMissingBean`，
本项目已声明同类型 Bean，故自动配置版本不会创建，配置值不会被框架默认值覆盖。

### 3.5 过滤器要点

`LoginRateLimitGlobalFilter` 同时实现 `GlobalFilter` 与 `Ordered`：

- `isLoginRequest`：方法与原始路径双条件，`POST` 且 `getRawPath()` 严格等于 `/api/auth/login`；
  带尾斜杠、带查询串不影响 `getRawPath()`，但其他路径（注册、刷新、找回密码）不进入限流。
- `resolveClientIp`：只读 `ServerHttpRequest.getRemoteAddress()`，优先 `getAddress().getHostAddress()`，
  未解析时退回 `getHostString()`；完全不用 `X-Forwarded-For` / `Forwarded`。
- `Mono.defer(...).materialize()`：把 `isAllowed` 的异常转成信号，统一进入 fail-closed 分支，
  避免异常穿透到框架默认错误页。
- `writeFailure`：设置状态码与 `application/json;charset=UTF-8`，用 `ApiResponse.failure(code, message)`
  序列化后写出；序列化失败返回 `Mono.error`，不静默吞掉。

## 4. 请求处理链路

### 4.1 完整链路（按执行顺序）

| 阶段 | 执行者 | 顺序依据 | 行为 |
|---|---|---|---|
| 1 | Netty HTTP 服务器（WebFlux） | 框架 | 接收连接，生成 `ServerWebExchange` |
| 2 | Spring Security `WebFilterChainProxy` | Spring Security 固定注册顺序 `-100` | 进入安全过滤链 |
| 3 | `CorsWebFilter` | 安全链内部（`http.cors(...)`） | 对 `/api/**` 做来源校验，处理 OPTIONS 预检 |
| 4 | JWT 认证过滤器 + 自定义 `jwtDecoder` | `oauth2ResourceServer().jwt()` | 解析 Bearer Token、验签、issuer、时间、`jti`、黑名单 |
| 5 | 授权过滤器 | `authorizeExchange` | 按第 6 节规则判定放行、`denyAll` 或要求已认证 |
| 6 | 异常转换 | `exceptionHandling` | 未认证 → 401；无权限 → 403 |
| 7 | `RoutePredicateHandlerMapping` | 默认 order `1`（`spring.cloud.gateway.server.webflux.handler-mapping.order`） | 取第一个匹配路由；无匹配返回框架 404 |
| 8 | `FilteringWebHandler` 组装链路 | 框架 | 合并全局过滤器与路由过滤器，按 `Ordered` 排序 |
| 9 | `LoginRateLimitGlobalFilter` | `Ordered.HIGHEST_PRECEDENCE + 100`（即 `-2147483548`） | 仅登录请求查 Redis 令牌桶，超限 429、依赖故障 503 |
| 10 | 路由过滤器 | Nacos 路由定义 | 文档路由 `RewritePath`；业务路由 `StripPrefix=1` |
| 11 | 负载均衡过滤器 | 框架 | `lb://`、`lb:ws://` 解析为具体实例；无实例抛 `NotFoundException` |
| 12 | `NettyRoutingFilter` / WebSocket 路由 | 框架 | HTTP 转发或 WebSocket 升级转发 |
| 13 | 响应回写 | 框架 | 下游响应原样返回客户端 |

两个顺序结论需要强调：

1. 安全链（`-100`）先于路由匹配（`1`），因此 401/403 在路由选择之前发生；未通过认证的请求不会进入限流过滤器。
2. `LoginRateLimitGlobalFilter` 是 `GlobalFilter`，只在路由命中后由 `FilteringWebHandler` 执行；
   Nacos 未发布路由时，`POST /api/auth/login` 直接 404，不消耗限流令牌。

### 4.2 401 响应

触发条件：未携带 `Authorization`、Token 签名错误、issuer 不符、已过期/未生效、缺少 `jti`、
`jti` 命中黑名单、Redis 无法确认黑名单状态。

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json;charset=UTF-8

{"code":"COMMON_401","message":"未登录或登录已过期","data":null}
```

处理类：`GatewayAuthenticationEntryPoint`。错误码常量 `CommonErrorCode.UNAUTHORIZED`。
该处理器不写 `WWW-Authenticate` 响应头，源码中无对应设置。

### 4.3 403 响应

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json;charset=UTF-8

{"code":"COMMON_403","message":"没有访问权限","data":null}
```

处理类：`GatewayAccessDeniedHandler`。当前安全规则下唯一的 403 来源是
`POST /api/users`、`POST /api/users/authenticate` 的 `denyAll()`：未认证请求由 401 处理器输出，
已认证请求由 403 处理器输出。网关没有基于角色的规则，因此不存在“角色不足”的 403 分支。

## 5. 路由设计

### 5.1 配置来源

- 路由命名空间：`spring.cloud.gateway.server.webflux.routes`（Gateway 4.3.0 的属性前缀）。
- 加载方式：`spring.config.import: optional:nacos:gateway-service.yaml`，即 Nacos 配置中心 data-id
  `gateway-service.yaml`。仓库内样例文件位于 [docs/nacos/gateway-service.yaml](../docs/nacos/gateway-service.yaml)。
- 未配置 `spring.cloud.gateway.discovery.locator.enabled`，不存在按服务名自动生成的路由。
- 未配置 `globalcors`、`default-filters`、`httpclient` 超时、`redis-route-definition-repository`。
- 匹配规则遵循 Gateway 语义：按配置顺序取第一个匹配的路由；因此文档路由必须排在业务路由之前。

### 5.2 文档路由（14 条，均为精确路径 + `RewritePath`）

| 路由 id | 外部路径 | 目标 | 过滤后下游路径 |
|---|---|---|---|
| `auth-service-docs` | `/api/auth/v3/api-docs` | `lb://auth-service` | `/v3/api-docs` |
| `user-service-docs` | `/api/users/v3/api-docs` | `lb://user-service` | `/v3/api-docs` |
| `order-service-docs` | `/api/orders/v3/api-docs` | `lb://order-service` | `/v3/api-docs` |
| `product-service-docs` | `/api/products/v3/api-docs` | `lb://product-service` | `/v3/api-docs` |
| `payment-service-docs` | `/api/payments/v3/api-docs` | `lb://payment-service` | `/v3/api-docs` |
| `file-service-docs` | `/api/files/v3/api-docs` | `lb://file-service` | `/v3/api-docs` |
| `cart-service-docs` | `/api/cart/v3/api-docs` | `lb://cart-service` | `/v3/api-docs` |
| `comment-service-docs` | `/api/comments/v3/api-docs` | `lb://comment-service` | `/v3/api-docs` |
| `search-service-docs` | `/api/search/v3/api-docs` | `lb://search-service` | `/v3/api-docs` |
| `statistics-service-docs` | `/api/statistics/v3/api-docs` | `lb://statistics-service` | `/v3/api-docs` |
| `recommendation-service-docs` | `/api/recommendations/v3/api-docs` | `lb://recommendation-service` | `/v3/api-docs` |
| `stock-service-docs` | `/api/stocks/v3/api-docs` | `lb://stock-service` | `/v3/api-docs` |
| `coupon-service-docs` | `/api/coupons/v3/api-docs` | `lb://coupon-service` | `/v3/api-docs` |
| `support-service-docs` | `/api/support/v3/api-docs` | `lb://support-service` | `/v3/api-docs` |

样例配置注释说明了为什么用 `RewritePath` 而不是 `StripPrefix=1`：文档路径需要在剥离 `/api` 后
再改写为 `/v3/api-docs`，只用 `StripPrefix=1` 会得到不存在的 `/auth/v3/api-docs`。

### 5.3 业务路由（15 条，均为 `StripPrefix=1`）

| 路由 id | 断言路径 | 目标 | 下游路径示例 |
|---|---|---|---|
| `search-service` | `/api/search`、`/api/search/**` | `lb://search-service` | `/search/...` |
| `statistics-service` | `/api/statistics`、`/api/statistics/**` | `lb://statistics-service` | `/statistics/...` |
| `recommendation-service` | `/api/recommendations`、`/api/recommendations/**` | `lb://recommendation-service` | `/recommendations/...` |
| `comment-service` | `/api/comments`、`/api/comments/**` | `lb://comment-service` | `/comments/...` |
| `notification-service-websocket` | `/api/notifications/ws` | `lb:ws://notification-service` | `/notifications/ws` |
| `notification-service` | `/api/notifications`、`/api/notifications/**`、`/api/chat`、`/api/chat/**` | `lb://notification-service` | `/notifications/...`、`/chat/...` |
| `cart-service` | `/api/cart`、`/api/cart/**` | `lb://cart-service` | `/cart/...` |
| `support-service` | `/api/support`、`/api/support/**` | `lb://support-service` | `/support/...` |
| `coupon-service` | `/api/coupons`、`/api/coupons/**` | `lb://coupon-service` | `/coupons/...` |
| `order-service` | `/api/orders/**` | `lb://order-service` | `/orders/...` |
| `user-service` | `/api/users/**` | `lb://user-service` | `/users/...` |
| `product-service` | `/api/products/**` | `lb://product-service` | `/products/...` |
| `auth-service` | `/api/auth/**` | `lb://auth-service` | `/auth/...` |
| `payment-service` | `/api/payments/**` | `lb://payment-service` | `/payments/...` |
| `file-service` | `/api/files/**` | `lb://file-service` | `/files/...` |

观察结果：

1. `stock-service` 只有文档路由，没有 `/api/stocks/**` 业务路由。样例注释说明其对外只有
   `/internal/stocks/**`，`/internal/**` 按约定不进网关，由 `order-service` 直连并校验 `X-Internal-Token`。
2. `notification-service-websocket` 使用 `lb:ws://`，其 `Path` 只有精确的 `/api/notifications/ws`；
   其余通知与聊天接口由 `notification-service` 的普通 HTTP 路由承载。
3. 断言写法不统一：`order/user/product/auth/payment/file` 只写 `/**`，其余同时写了无尾缀与 `/**` 两种形式。
   两种写法都存在于当前配置中，修改路由时保持原样以免改变匹配范围。

### 5.4 与 `application.yml` 的差异

| 对比项 | `docs/nacos/gateway-service.yaml` | `src/main/resources/application.yml` |
|---|---|---|
| 路由定义 | 29 条（14 文档 + 15 业务） | 0 条 |
| Swagger 下拉分组 | 由 Nacos 文档路由决定 | 13 项，缺少 `search-service` |
| `search` 文档路由 | 有 `/api/search/v3/api-docs` | 未列入 `springdoc.swagger-ui.urls` |
| `stock` 业务路由 | 无 | 无（只有文档入口） |
| `springdoc.gateway-base-url` / `gateway-path` | 未出现 | 未出现；这两个键出现在各业务服务配置中，不是本模块配置 |

因此存在一个可直接复现的差异：`http://localhost:9090/api/search/v3/api-docs` 有路由可达，
但网关 Swagger UI 下拉框中没有 `search-service` 选项。需要查看搜索文档时直接访问该地址。

## 6. 认证与鉴权

### 6.1 JWT 校验步骤

1. 从 `Authorization: Bearer <token>` 取出令牌；没有该头则直接 401。
2. `NimbusReactiveJwtDecoder.withPublicKey(RSAPublicKey)` 构建解码器，默认签名算法为 `RS256`。
3. 读取 `security.jwt.public-key-location` 指向的 X.509 PEM 公钥文件；该文件在启动时读取一次并驻留内存。
4. `JwtValidators.createDefaultWithIssuer(issuer)` 校验签名、`iss`、`exp`、`nbf`。
5. 检查 `jti` 声明：为空或空白即构造 `JwtValidationException`（OAuth2 错误码 `invalid_token`）。
6. 用 `ReactiveRedisOperationsService.hasKey("auth:access-token:blacklist:" + jti)` 做非阻塞 `EXISTS` 查询。
7. 只有 Redis 明确返回 `FALSE` 才放行；返回 `TRUE`、返回空序列、抛异常一律拒绝。
8. 通过后写入 `SecurityContext`，身份为“已认证”，不附加角色/范围判断。

### 6.2 访问规则白名单

规则按声明顺序生效，`anyExchange().authenticated()` 兜底：

| 规则 | 方法 | 放行原因 |
|---|---|---|
| `POST /api/users`、`POST /api/users/authenticate` | POST | `denyAll()`，网关层不开放这两个入口，任何身份都被拒绝 |
| `/api/files/*/content` | GET | 图片内容匿名可读，与文件服务策略一致；写操作与元数据仍需登录 |
| `/api/auth/captcha`、`/api/auth/password-reset/code`、`/api/auth/password-reset/verify`、`/api/auth/password-reset/confirm` | 任意 | 登录前与找回密码流程所需 |
| `/api/auth/login` | 任意 | 登录入口 |
| `/api/users/me/deletion/cancel` | POST | 账号冷静期内无正常访问令牌，只凭 10 分钟一次性撤回令牌 |
| `/api/auth/register`、`/api/auth/registration/code`、`/api/auth/registration/verify`、`/api/auth/email-enrollment/code`、`/api/auth/email-enrollment/confirm` | 任意 | 注册与邮箱登记流程所需 |
| `/api/auth/refresh` | 任意 | 刷新令牌换取新 Access Token |
| `/api/notifications/ws` | GET | WebSocket 握手阶段由通知服务校验短时一次性票据 |
| `/actuator/health` | 任意 | 健康检查 |
| `/swagger-ui/**`、`/swagger-ui.html`、`/v3/api-docs/**` | 任意 | 网关自身 Swagger 入口 |
| `/api/*/v3/api-docs` | 任意 | 各服务文档经网关聚合访问 |
| 其他 | 任意 | 必须携带有效 JWT |

注意：白名单只影响网关是否放行，不代表下游接口匿名可用；下游服务仍会独立校验身份。

### 6.3 注销黑名单

| 项 | 内容 |
|---|---|
| Key 结构 | `auth:access-token:blacklist:{jti}` |
| 前缀来源 | `security.jwt.blacklist-key-prefix`，默认 `auth:access-token:blacklist:` |
| 存储值 | `"1"`，由认证服务写入，本模块只做存在性判断 |
| TTL | 写入时设置为 Access Token 原定过期时间；网关不设置、不刷新 TTL |
| 查询方式 | `ReactiveStringRedisTemplate.hasKey`，非阻塞 |
| 失败策略 | fail-closed：Redis 异常、空结果、返回非 `FALSE` 都按令牌无效处理，返回 401 |

黑名单写入实现在 [AccessTokenBlacklistService.java](../auth-service/src/main/java/com/hbue/ordering/auth/service/AccessTokenBlacklistService.java)，
关键约束：`jti` 或过期时间缺失时拒绝写入；Token 已过期则直接跳过写入。

### 6.4 身份透传

源码未向请求添加任何身份 Header，也没有改写 `Authorization`。下游服务自行解析同一个 Bearer Token
（各服务配置了相同的公钥与黑名单前缀），网关只负责入口放行。若后续需要透传用户身份，必须先由本模块
新增过滤器并在文档中登记，不能假定当前已存在。

## 7. 限流设计

### 7.1 实现与生效范围

| 项 | 内容 |
|---|---|
| 实现 | Spring Cloud Gateway `RedisRateLimiter`（Redis + Lua 原子脚本） |
| 注入方式 | `GatewayRateLimitConfig.loginRedisRateLimiter`，构造注入 `LoginRateLimitGlobalFilter` |
| 生效路径 | 仅 `POST` 且原始路径精确等于 `/api/auth/login` |
| 不生效路径 | `/api/auth/register`、`/api/auth/refresh`、找回密码、注销、WebSocket 握手等 |
| 限流维度 | 客户端 IP（TCP 连接的远端地址） |
| 分布式一致性 | 令牌桶状态在 Redis，多网关实例共享同一额度 |

### 7.2 Redis Key 结构

Gateway 4.3.0 的 `RedisRateLimiter` 使用固定 Key 模板（`request_rate_limiter.{<id>.<key>}.<字段>`），
本模块传入 `id = "auth-login"`、`key = 客户端 IP`，因此实际 Key 为：

```text
request_rate_limiter.{auth-login.198.51.100.8}.tokens
request_rate_limiter.{auth-login.198.51.100.8}.timestamp
```

两个 Key 都由 Lua 脚本以 `SETEX` 写入，TTL = `floor(burstCapacity / replenishRate * 2)`；
默认参数下为 `floor(50 / 1 * 2) = 100` 秒。桶不存在时初始令牌数等于 `burstCapacity`。

### 7.3 参数与算例

| 配置项 | 默认值 | 作用 |
|---|---|---|
| `gateway.rate-limit.login.replenish-rate` | `1` | 每秒补充令牌数 |
| `gateway.rate-limit.login.burst-capacity` | `50` | 令牌桶容量，同时也是初始令牌数 |
| `gateway.rate-limit.login.requested-tokens` | `10` | 每次登录消耗令牌数 |

默认参数下的行为：一个 IP 首次请求时桶内有 50 个令牌，可连续通过 5 次登录；之后每 1 秒恢复 1 个令牌，
需要 10 秒才能再次通过 1 次登录。脚本放行条件为“当前令牌数 >= 本次消耗数”，余额不足时请求被拒绝，
桶内余额不会被扣成负数。

### 7.4 拒绝与放行响应

- 放行：复制限流响应头后继续 `chain.filter(exchange)`。
- 拒绝：HTTP `429`，响应体 `{"code":"COMMON_429","message":"登录请求过于频繁，请稍后再试","data":null}`。
- 响应头：`X-RateLimit-Remaining`、`X-RateLimit-Replenish-Rate`、`X-RateLimit-Burst-Capacity`、
  `X-RateLimit-Requested-Tokens`，由 `RedisRateLimiter` 默认开启（`includeHeaders = true`）生成，
  过滤器原样复制到网关响应。单测只断言了 `X-RateLimit-Replenish-Rate`。

### 7.5 故障与异常行为

| 场景 | 结果 |
|---|---|
| Redis 抛异常（连接失败、超时） | 503，`COMMON_503`，消息“登录服务暂不可用，请稍后重试”；请求不进入下游 |
| 限流器返回空结果（无判定值） | 503，同上；记录 warn 日志 |
| 无法识别客户端 IP（远端地址为空） | 503，`COMMON_503`，消息“暂时无法识别客户端地址，请稍后重试” |
| 响应 JSON 序列化失败 | `Mono.error`，交由 WebFlux 默认错误处理，不返回半截响应 |

日志只记录异常与策略信息，不记录登录请求体、密码或令牌。

### 7.6 部署形态对限流维度的影响

限流 Key 取 TCP 远端地址且显式忽略 `X-Forwarded-For`。直连 9090 时该地址就是客户端真实 IP；
经过 Nginx 或其他反向代理后，Gateway 看到的远端地址是代理地址，所有客户端会共享同一个限流 Key，
默认额度退化为全站每 100 秒约 5 次登录。若要保持按客户端限流，需要调整部署（例如代理层直接限流），
或在可信代理前提下修改 IP 解析策略，二者都必须同步修改源码与文档。

### 7.7 配置校验

启动期校验，任一不满足即抛 `IllegalArgumentException`，应用启动失败：

1. `replenish-rate >= 1`；
2. `burst-capacity >= 1`；
3. `requested-tokens >= 1`；
4. `requested-tokens <= burst-capacity`。

## 8. CORS、WebSocket 与协议升级

### 8.1 CORS

| 项 | 值 |
|---|---|
| 配置键 | `gateway.cors.allowed-origins`（可用 `GATEWAY_CORS_ALLOWED_ORIGINS` 覆盖） |
| 默认来源 | `http://localhost:8088`、`http://127.0.0.1:8088`、`http://localhost:5173`、`http://127.0.0.1:5173`、`http://192.168.0.14:8088`、`http://localhost` |
| 允许方法 | `GET`、`POST`、`PUT`、`PATCH`、`DELETE`、`OPTIONS` |
| 允许请求头 | `Authorization`、`Content-Type`、`Idempotency-Key` |
| 是否允许携带 Cookie | `false`（使用 Bearer Token，不依赖跨域 Cookie） |
| 预检缓存 | `3600` 秒 |
| 作用范围 | 仅 `/api/**` |

行为要点：

1. 预检 `OPTIONS` 在认证之前由 CORS 过滤器处理，不会进入业务链；单测验证预检不调用下游。
2. 非白名单来源的预检返回 `403`（单测断言），响应体由框架跨域组件生成，不是 `ApiResponse`。
3. 认证失败的响应仍带 `Access-Control-Allow-Origin`，浏览器能读取 401 响应体，单测对此有断言。
4. 不带 `Origin` 的请求（curl、服务间调用）不参与跨域校验。

默认值包含无端口的 `http://localhost`，原因是 Android 原生 Socket 实测携带该来源；该条目同样说明
来源白名单是精确匹配，部署到真实域名时必须替换，不能使用通配符。

### 8.2 WebSocket

- 通知服务握手路由为 `notification-service-websocket`，目标 `lb:ws://notification-service`，
  断言精确路径 `/api/notifications/ws`，`StripPrefix=1` 后下游为 `/notifications/ws`。
- 安全链对 `GET /api/notifications/ws` 放行，握手阶段由通知服务校验短时一次性票据，长期 JWT 不放入 URL。
- `lb:ws://` 由 Gateway 的 WebSocket 路由过滤器完成协议升级；源码未自定义升级过滤器、未配置握手超时。
- 握手请求带 `Origin` 时走第 8.1 节同一来源白名单，因为路径位于 `/api/**` 之下。

## 9. 配置项清单

| 配置 key | 默认值 | 作用 |
|---|---|---|
| `server.port` | `9090` | HTTP 监听端口 |
| `spring.application.name` | `gateway-service` | 服务名，决定 Nacos 注册名 |
| `spring.data.redis.host` | `${REDIS_HOST:127.0.0.1}` | Redis 地址 |
| `spring.data.redis.port` | `${REDIS_PORT:9096}` | Redis 端口 |
| `spring.data.redis.password` | `${REDIS_PASSWORD:order_redis_dev_2026}` | Redis 密码 |
| `spring.data.redis.database` | `0` | 黑名单与限流 Key 所在库 |
| `spring.data.redis.connect-timeout` | `2s` | 连接超时 |
| `spring.data.redis.timeout` | `2s` | 命令超时 |
| `spring.config.import` | `optional:nacos:gateway-service.yaml` | 从 Nacos 加载路由等配置 |
| `spring.cloud.nacos.username` | `${NACOS_USERNAME:nacos}` | Nacos 账号 |
| `spring.cloud.nacos.password` | `${NACOS_PASSWORD:root}` | Nacos 密码 |
| `spring.cloud.nacos.discovery.server-addr` | `${NACOS_SERVER_ADDR:127.0.0.1:9097}` | 注册中心地址 |
| `spring.cloud.nacos.discovery.enabled` | `true` | 启用服务注册与发现 |
| `spring.cloud.nacos.config.server-addr` | `${NACOS_SERVER_ADDR:127.0.0.1:9097}` | 配置中心地址 |
| `gateway.cors.allowed-origins` | 第 8.1 节 6 个来源 | 跨域来源白名单 |
| `gateway.rate-limit.login.replenish-rate` | `1` | 每秒补充令牌数 |
| `gateway.rate-limit.login.burst-capacity` | `50` | 令牌桶容量 |
| `gateway.rate-limit.login.requested-tokens` | `10` | 单次登录消耗令牌数 |
| `security.jwt.issuer` | `http://localhost:9094` | 校验 `iss` 声明 |
| `security.jwt.public-key-location` | `file:/home/user/.config/order-system/keys/jwt-public.pem` | RSA 公钥路径 |
| `security.jwt.blacklist-key-prefix` | `auth:access-token:blacklist:` | 黑名单 Key 前缀 |
| `springdoc.api-docs.enabled` | `${SWAGGER_ENABLED:true}` | 网关自身 OpenAPI 输出开关 |
| `springdoc.swagger-ui.enabled` | `${SWAGGER_ENABLED:true}` | Swagger UI 开关 |
| `springdoc.swagger-ui.urls[]` | 13 项 | 聚合下拉列表 |
| `spring.cloud.gateway.server.webflux.routes` | Nacos 提供 | 全部路由定义 |

补充说明：

- `security.jwt.issuer` 与 `security.jwt.public-key-location` 在本仓库中都写死为固定值，未使用环境变量占位符。
  默认公钥路径是 Linux 绝对路径，Windows 开发机必须通过 Nacos 配置或启动参数覆盖，否则启动失败。
- 未配置 `management.endpoints.web.exposure.include`，按 Spring Boot 默认仅暴露 `health`；
  安全链只显式放行 `/actuator/health`。
- 未配置 `spring.cloud.gateway.httpclient.*`，连接超时与响应超时使用框架默认值。

## 10. 异常与失败行为

| 场景 | HTTP 状态 | 响应体 | 依据 |
|---|---|---|---|
| 无 Token / Token 无效 / 已过期 | `401` | `ApiResponse`，`COMMON_401` | `GatewayAuthenticationEntryPoint` |
| `jti` 缺失 | `401` | `COMMON_401`（内部错误描述 `访问令牌缺少 jti 声明`） | `invalidToken(...)` |
| `jti` 在黑名单 | `401` | `COMMON_401`（内部描述 `访问令牌已注销或状态无法确认`） | `decodeAndCheckBlacklist` |
| Redis 无法确认黑名单 | `401` | `COMMON_401`（包装为 `JwtException`） | `onErrorMap` |
| 命中 `denyAll`（已认证） | `403` | `ApiResponse`，`COMMON_403` | `GatewayAccessDeniedHandler` |
| 登录超过限流额度 | `429` | `COMMON_429` + `X-RateLimit-*` | `LoginRateLimitGlobalFilter` |
| 登录限流依赖 Redis 故障 | `503` | `COMMON_503`，消息“登录服务暂不可用，请稍后重试” | 同上，fail-closed |
| 登录请求远端地址缺失 | `503` | `COMMON_503`，消息“暂时无法识别客户端地址，请稍后重试” | 同上 |
| 公钥文件不可读 | 应用启动失败 | 日志 `读取 JWT RSA 公钥失败` | `GatewaySecurityConfig.jwtDecoder` |
| 公钥格式错误 | 应用启动失败 | `RsaKeyConverters.x509()` 抛出的异常向上传播 | 同上，未捕获 |
| 限流参数非法 | 应用启动失败 | `IllegalArgumentException` | `GatewayRateLimitConfig` |
| 无匹配路由 | `404` | 框架默认错误体，不是 `ApiResponse` | Gateway 默认处理，源码未定制 |
| 服务发现无可用实例 | `503` | 框架默认错误体 | `NotFoundException`（继承 `ResponseStatusException`，状态 `SERVICE_UNAVAILABLE`） |
| 下游连接失败/超时 | 框架异常映射 | 框架默认错误体，不是 `ApiResponse` | 未配置重试与熔断，源码未定制 |
| 非白名单来源跨域 | `403` | 框架跨域组件的空响应体 | `CorsWebFilter` |
| Swagger 关闭 | `404` | 框架默认 | `SWAGGER_ENABLED=false` |

结论：`ApiResponse` 只覆盖认证、鉴权与登录限流三类由本项目代码写出的响应；路由与网络层失败仍使用
框架默认错误结构。若要求全站统一错误体，需要新增全局异常处理，当前源码未实现。

## 11. 关键代码入口表

| 文件 | 职责 | 关键方法 |
|---|---|---|
| [GatewayServiceApplication.java](src/main/java/com/hbue/ordering/gateway/GatewayServiceApplication.java) | 启动、服务发现、显式导入 Redis 组件 | `main` |
| [GatewaySecurityConfig.java](src/main/java/com/hbue/ordering/gateway/config/GatewaySecurityConfig.java) | 安全链、CORS、JWT 解码与黑名单 | `securityWebFilterChain`、`corsConfigurationSource`、`jwtDecoder`、`decodeAndCheckBlacklist` |
| [GatewayRateLimitConfig.java](src/main/java/com/hbue/ordering/gateway/config/GatewayRateLimitConfig.java) | 限流器 Bean 与参数校验 | `loginRedisRateLimiter` |
| [LoginRateLimitGlobalFilter.java](src/main/java/com/hbue/ordering/gateway/filter/LoginRateLimitGlobalFilter.java) | 登录 IP 限流 | `filter`、`getOrder`、`isLoginRequest`、`resolveClientIp`、`writeFailure` |
| [GatewayAuthenticationEntryPoint.java](src/main/java/com/hbue/ordering/gateway/security/GatewayAuthenticationEntryPoint.java) | 401 输出 | `commence` |
| [GatewayAccessDeniedHandler.java](src/main/java/com/hbue/ordering/gateway/security/GatewayAccessDeniedHandler.java) | 403 输出 | `handle` |
| [application.yml](src/main/resources/application.yml) | 端口、Redis、Nacos、CORS、限流、JWT、Swagger | — |
| [LoginRateLimitGlobalFilterTest.java](src/test/java/com/hbue/ordering/gateway/filter/LoginRateLimitGlobalFilterTest.java) | 429、放行、503、非登录路径、伪造转发头 | 5 个测试方法 |
| [GatewayCorsConfigTest.java](src/test/java/com/hbue/ordering/gateway/config/GatewayCorsConfigTest.java) | 预检、拒绝来源、认证失败保留跨域头、原生 Socket 来源 | 4 个测试方法 |

## 12. 已知限制

1. 限流只覆盖登录接口，其他接口（注册、刷新、找回密码、业务查询）没有网关级限流。
2. 限流维度是 TCP 远端地址；经过代理后所有客户端共享同一 Key，按 IP 限流的效果取决于部署拓扑。
3. 黑名单校验是每个已认证请求一次 Redis 查询；Redis 不可用时全部需要认证的请求返回 401（fail-closed）。
4. 公钥只在启动时读取一次，JWT 密钥轮换必须重启网关。
5. 默认公钥路径为 Linux 绝对路径，issuer 也写死为 `http://localhost:9094`，跨环境部署必须覆盖。
6. 网关不校验 `aud`、`scope`、角色；任何有效 Token 都能通过网关到达任意业务路由，实际权限完全依赖下游服务。
7. 无身份透传 Header，下游只能重复解析 JWT。
8. 无重试、熔断、连接池与超时调优配置，下游异常直接暴露为框架错误页。
9. `optional:nacos` 会让配置缺失时静默启动，形成“服务在线但无路由、全部 404”的故障形态。
10. 403 的唯一来源是两条 `denyAll` 规则，网关没有角色级拒绝能力。
11. Swagger 聚合列表缺少 `search-service`，与 Nacos 文档路由不一致。
12. 网关自身端点（`/swagger-ui`、`/v3/api-docs`、`/actuator/health`）不在 Nacos 路由表中，由网关进程直接处理；
    这些路径的访问不受业务服务状态影响。

## 13. 未验证项

以下内容无法仅靠静态阅读本模块源码确认，未做运行验证：

1. 运行中 Nacos 的 `gateway-service.yaml` 是否与 [仓库样例](../docs/nacos/gateway-service.yaml) 完全一致，
   包括 namespace、group 与发布版本。
2. 各业务服务 Controller 的实际路径是否与 `StripPrefix=1` 之后的路径一一对应。
3. Nacos gRPC 端口 `10097` 的实际可达性；该端口未在配置中声明，由客户端按 9097+1000 推导。
4. Redis `127.0.0.1:9096` 与密码默认值在当前环境是否可用；网关启动与限流、黑名单查询均依赖该连接。
5. 多网关实例部署时 Redis 令牌桶与黑名单的共享效果；代码层面成立，未做多实例压测。
6. `429` 与 `503` 时 `X-RateLimit-Remaining` 的具体数值；单测只断言了 `X-RateLimit-Replenish-Rate`。
7. 非白名单来源的实际请求（非预检）响应体内容；单测只断言预检返回 403。
8. 下游不可用时框架默认错误体的具体字段；本项目未定制该结构。
9. `docs/API_DOCS.md` 提到的“点击 Try it out 请求发往 9090”所依赖的 `springdoc.gateway-base-url`、
   `gateway-path` 在本模块配置中不存在，实际生效值取决于各业务服务配置。

## 14. 关联文档

| 文档 | 与本模块的关系 |
|---|---|
| [ARCHITECTURE.md](../docs/ARCHITECTURE.md) | 服务总表、端口分配、网关职责定位 |
| [API_DOCS.md](../docs/API_DOCS.md) | Swagger 聚合入口、文档路由约定、`/internal/**` 不进网关的原因 |
| [DOCKER_DEV_ENV.md](../docs/DOCKER_DEV_ENV.md) | 网关容器端口 9090、Compose `app` profile 启动方式 |
| [NOTIFICATION_SERVICE.md](../docs/NOTIFICATION_SERVICE.md) | WebSocket 一次性票据与握手流程 |
| [notification-service/README.md](../notification-service/README.md) | `/api/notifications/ws` 下游实现 |
| [auth-service/README.md](../auth-service/README.md) | Access Token 签发与注销黑名单写入 |
| [docs/nacos/gateway-service.yaml](../docs/nacos/gateway-service.yaml) | 路由配置样例与配置中心发布来源 |
| [README.md](../README.md) | 项目总览与服务启动说明 |
