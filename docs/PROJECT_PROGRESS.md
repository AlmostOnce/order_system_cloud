# order-system-cloud 项目进度

## 当前状态

更新时间：2026-09-28

### 已完成

- 确定使用 Spring Cloud Alibaba 体系。
- 服务注册与配置中心采用 Nacos，不采用 Eureka。
- 负载均衡采用 Spring Cloud LoadBalancer，不采用已经停止维护的 Ribbon。
- 熔断降级采用 Sentinel。
- 链路追踪和监控采用 SkyWalking。
- 创建父 Maven 工程 `order-system-cloud`。
- 创建第一个模块 `gateway-service`。
- Gateway 已能启动；访问根路径返回 404 是因为当前还没有业务路由，属于正常现象。
- 旧项目的 `ordering-nacos`、`order-sys-mysql-dev`、`order-sys-redis-dev` 已停止，并设置为不自动启动；没有删除其容器和数据。

### 本次完成

- 创建项目级 `docker-compose.yml`。
- 创建 Gateway 的 Java 21 多阶段 `Dockerfile`。
- 创建 `.env.example` 和本机 `.env`。
- 创建 `.dockerignore`。
- 创建 Docker 开发环境说明 `docs/DOCKER_DEV_ENV.md`。
- 本项目使用独立端口：MySQL `3307`、Nacos 控制台 `8082`、Nacos API `8850`；Gateway 端口以当前 `application.yml` 和启动日志为准，目前为 `8080`。
- MySQL 和 Nacos 使用命名卷持久化。
- Gateway 的 Nacos 地址、用户名和密码改为环境变量读取。
- 已通过 `docker compose config` 配置检查。
- 已通过 Maven Java 21 构建检查。
- 已构建镜像 `order-system-cloud-gateway:latest`。
- 已启动本项目 MySQL 和 Nacos；MySQL 健康检查通过，Nacos 控制台和 API 返回 HTTP 200。
- 已通过 `mvn -f gateway-service/pom.xml test`。
- 已确认 Docker 镜像 `order-system-cloud-gateway:latest` 存在。
- 已将本项目 Docker Compose 服务改为 `unless-stopped`，系统或 Docker 重启后自动启动，手动停止后保持停止。
- 用户已确认 IDEA 中的 Gateway 可以正常启动并连接本项目 Nacos；环境变量配置格式问题已解决。
- 用户已确认 Gateway 启动日志正常、Nacos 服务列表存在 `gateway-service`，并可访问 Gateway 地址。
- 已创建最小 `order-service` 模块，服务端口为 `8081`。
- `order-service` 已成功注册到本项目 Nacos。
- 已在 Nacos 发布 `gateway-service.yaml`，配置 `/api/orders/**` 路由到 `lb://order-service`。
- 已验证 `Gateway → Nacos → order-service` 链路，`/api/orders/ping` 能返回 `order-service is running`。
- 已创建订单列表查询接口 `GET /orders/list`，并通过 Gateway 访问 `GET /api/orders/list` 验证返回正常。
- 已建立项目 Java 编码规范：`docs/JAVA_CODING_STANDARDS.md`；后续 Java 代码按阿里巴巴 Java 开发规范摘要执行。
- 已补充强制注释规范：中文 `/** */` 文档注释用于类、接口、枚举和公共方法，方法内部逻辑使用 `//` 注释。
- 已补充 DO、DTO、VO、BO、PO、AO 的命名和分层约定；数据库实体统一使用 DO，接口禁止直接返回 DO。
- 已创建 `user-service` 模块，规划端口为 `8083`，包名为 `com.hbue.ordering.user`。
- `user-service` 已接入 Nacos 服务注册，并提供统一返回体的 `/users/ping` 接口。
- `order-service` 已引入 OpenFeign，并创建 `UserServiceClient`。
- 已完成 `order-service -> user-service` 的基础远程调用接口。
- 已在 Gateway 的 Nacos 配置中追加 `user-service` 路由 `/api/users/**`。

### 2026-09-20 本次完成

- 创建 `user-api` 公共契约模块，存放 `UserBasicVO` 和 `UserServiceClient`。
- `order-service` 已补充 OpenFeign 所需的 Spring Cloud LoadBalancer 依赖。
- 已将远程调用从 Controller 下沉到 `OrderService`，形成 Controller → Service → Feign 分层。
- `user-service` 已接入 MySQL、MyBatis-Plus，并创建独立数据库 `user_db`。
- 已创建新的 `users` 表，未复用旧项目表结构。
- 已创建 `UserDO`、`UserMapper`，用户查询已改为 Mapper SQL。
- 用户查询已增加正常状态和未逻辑删除条件。
- 已配置 Sentinel Dashboard，并为用户服务远程调用配置熔断规则。
- 已配置 Sentinel Nacos 数据源；启动时发现缺少 `sentinel-datasource-nacos`，已补充该依赖，但修复后的启动结果尚未确认。
- 已将“业务代码尽量少用 Lambda 和 Stream，优先普通循环和 QueryWrapper/Mapper SQL”的约定写入 `docs/JAVA_CODING_STANDARDS.md`。
- 已确定认证方案采用 OAuth2 思路下的 JWT Token，不采用原系统的 Redis Token 作为本项目新认证方案。
- 认证规划为：`auth-service` 负责登录和 JWT 签发，Gateway 与业务服务使用 Spring Security Resource Server 校验 JWT。
- JWT 签名密钥不得在应用启动时随机生成；正式方案使用外置 RSA 密钥，私钥通过安全配置或密钥管理系统注入，公钥通过 JWK 或配置提供给资源服务。

### 2026-09-22 本次完成

- 已将 Redis 加入根目录 `docker-compose.yml`，使用 `redis:7.4-alpine`，配置密码认证、AOF 持久化和健康检查。
- 已为 `auth-service` 引入 `spring-boot-starter-data-redis`。
- 已为 `auth-service` 增加 Redis 连接配置，支持 `REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD` 环境变量；本机 IDEA 启动默认连接 `127.0.0.1:6379`。
- 已完成 Redis 基础连接配置。
- 已创建认证服务内部 `RefreshTokenService`，登录成功后生成随机 Refresh Token，并以 Redis Hash 保存用户 ID、用户名和角色。
- Refresh Token 已配置 30 天自动过期；当前登录响应已同时返回访问 Token 和 Refresh Token。
- 已实现 `POST /auth/refresh`，支持通过 Redis 中的 Refresh Token 换取新的 JWT。
- Refresh Token 刷新采用令牌轮换策略：刷新成功后删除旧 Token，并返回新的 Access Token 和 Refresh Token。
- Gateway 已放行 `/api/auth/refresh`，认证服务已放行 `/auth/refresh`。
- 已实现 `POST /auth/logout`，注销时删除 Redis 中的 Refresh Token；Gateway 和认证服务均放行该接口。
- Refresh Token 刷新链路已由用户通过 Postman 验证。
- 已将统一响应对象从 `common-web` 拆分到独立的 `common-response` 模块，避免 Gateway 引入 Servlet Web 依赖。
- `common-web` 现在只负责 Spring MVC 的全局异常处理；`common-response` 只负责 `ApiResponse`。
- Gateway 已改为只依赖 `common-response` 和 WebFlux 相关组件；`auth-service`、`user-service`、`order-service` 继续使用 `user-service` 和 Servlet Web 架构。
- 已确认当前用户领域链路保持不变：`auth-service -> user-api -> user-service`，`order-service -> user-api -> user-service`。

### 2026-09-25 本次完成

- 新增 `common-redis` Maven 模块并加入父工程模块列表。
- 新增 Spring 管理的 `RedisOperationsService`，封装字符串读写、Hash 读写、TTL、Key 存在判断和删除。
- 公共组件使用 `StringRedisTemplate`，Redis 操作异常继续向调用方传播，不在基础层静默吞掉。
- 公共层只封装 Redis 基础操作；认证服务现已依赖 `common-redis`，其 Redis Hash 读写、TTL 设置和 Key 删除改为调用 `RedisOperationsService`，认证业务规则保持不变。
- 新增 `ReactiveRedisOperationsService`，供 Gateway 以非阻塞方式查询 Redis，避免在 WebFlux 请求线程中执行同步 Redis 操作。
- Access Token 增加随机 `jti` 唯一标识；注销接口改为必须携带有效 Bearer Token。
- 注销时校验 Refresh Token 是否属于当前 Access Token 对应用户；匹配后将当前 Access Token 的 `jti` 写入 Redis 黑名单，并删除对应 Refresh Token。
- 黑名单 Key 使用统一前缀 `auth:access-token:blacklist:`，TTL 为 Access Token 的剩余有效期；过期令牌不再留下黑名单数据。
- 认证服务、Gateway 和 `order-service` 的 JWT 校验均拒绝黑名单 Token；Redis 无法确认黑名单状态时按拒绝访问处理。
- Gateway 与 `order-service` 已加入 `common-redis` 依赖和 Redis 连接配置；Docker Compose 中的 Gateway 容器连接同 Compose 的 Redis 服务。
- 新增 JWT 唯一 ID、注销身份校验、Refresh Token 归属校验、黑名单 TTL 和响应式 Redis 操作相关测试；全项目 `mvn test` 通过，共 22 项测试通过。
- 其它业务模块后续按需依赖 `common-redis`，不强制所有服务引入 Redis。
- 根据注销请求日志修正 `common-web`：缺少请求体或 JSON 无法解析时返回 HTTP 400 和统一参数错误响应，不再被通用异常处理器误报为 HTTP 500。
- 为 `common-web` 增加缺失请求体的 MVC 回归测试，并配置 Surefire 3.5.4 确保 JUnit 5 测试实际执行；测试先复现 500，修复后通过。
- 修复后全项目 `mvn test` 通过，共实际执行 23 项测试。
- 用户已反馈注销请求验证完成；注销后复用原 Access Token 访问 Gateway 的订单 ping 接口返回 401，符合黑名单预期。
- Gateway 已加入响应式 Redis Starter，满足 Spring Cloud Gateway `RedisRateLimiter` 的运行依赖。
- Gateway 全局过滤器只限制精确匹配的 `POST /api/auth/login`，不影响注册、刷新、注销和其它业务接口。
- 登录限流按 TCP 连接远端 IP 分桶，不读取客户端可伪造的 `X-Forwarded-For`；多 Gateway 实例通过 Redis 共享令牌桶。
- 当前令牌桶参数为 `replenish-rate=1`、`burst-capacity=50`、`requested-tokens=10`：同一 IP 可短时连续请求 5 次，之后平均每 10 秒补充一次请求额度。
- 超限返回 HTTP 429 和统一响应码 `COMMON_429`；Redis 限流检查不可用时 fail-closed，返回 HTTP 503 和 `COMMON_503`，不会绕过限流继续登录。
- 已为限流器增加正常放行、超限响应、Redis 故障、非登录路径绕过和转发头伪造防护测试。
- 已执行根工程 `mvn test`，全部 28 项测试通过；后续登录失败映射修复后，当前全项目共 32 项测试通过。
- 已对运行中的本机 Gateway 做端到端验证：向 `127.0.0.1:8080/api/auth/login` 快速发送 6 次请求，约 0.3 秒内完成；前 5 次到达下游服务，第 6 次返回 HTTP 429，确认当前 Gateway 与 Redis 限流链路生效。
- 用户已使用 Postman Collection Runner 验证网关限流：必须通过 `http://127.0.0.1:8080/api/auth/login`；直接访问 `localhost:8084/auth/login` 会绕过 Gateway。Runner 第 6 次返回限流响应；手动操作时过快点击可能没有发出独立请求，应以 Postman History 中实际请求条数为准。
- 修复登录失败的跨服务错误映射：Feign 收到 user-service 的 HTTP 401 时，降级工厂保留为 `COMMON_401`；其它 Feign 调用故障返回 `COMMON_503`，认证服务将其映射为 HTTP 503，不再统一误报为 `COMMON_500`。
- 根据实际运行日志补齐两个集成缺口：`UserServiceApplication` 原先只扫描 `com.hbue.ordering.user`，导致 `common-web` 的全局异常处理器没有注册，用户服务将预期的 `COMMON_401` 业务异常变成 Spring 默认 HTTP 500；现在显式扫描用户服务和公共异常处理包。
- `auth-service` 已加入 Spring Cloud Alibaba Sentinel Feign 适配依赖，并启用 `feign.sentinel.enabled`，使 `UserServiceClientFallbackFactory` 在运行时生效：下游 401 映射为登录失败，其它 Feign 故障映射为服务不可用。
- 新增用户服务组件扫描回归测试；先确认旧扫描范围会失败，再调整扫描范围验证通过。为使用户服务 JUnit 5 测试被 Maven 执行，给该模块配置了 Surefire 3.5.4。
- 根工程 `mvn test` 当前 33 项测试全部通过；`git diff --check` 通过。
- 错误日志中另有一条 `GET` 请求不支持的记录，与登录 POST 的 500 是不同请求；登录接口仍需使用 POST。
- `auth-service` 已加入 Redisson Spring Boot Starter，并针对当前 Spring Boot 3.5 / Spring Data Redis 3.5 选择 `redisson-spring-data-35` 适配模块。
- 新增 `RefreshTokenLockService`：按 Refresh Token 的 SHA-256 摘要生成锁 Key，不在 Redis 锁 Key 中暴露 Token 原文；锁等待最长 5 秒，固定租约 30 秒，避免释放失败后看门狗无限续期造成该 Token 永久阻塞。
- Refresh Token 刷新现在先获取分布式锁，再查询 Redis 中的旧 Token，并在锁内完成新 Token 签发和旧 Token 消费。同一枚旧 Token 的请求会串行处理；不同 Token 使用不同锁，可并行刷新。
- 为防止长时间 JVM 暂停等极端情况导致锁租约失效，新增 Refresh Token 原子消费：以 Redis 单 Key `DEL` 的返回结果作为最终竞争判定，只有成功删除旧 Token 的请求才能返回新 Token；竞争失败的请求会尽力清理自己预创建的新 Token，并返回未授权。
- Redisson 锁使用 30 秒固定租约：正常刷新路径只有本地 JWT 处理和 Redis 操作，租约用于常规串行化并保证释放故障后锁最终回收；若异常停顿超过租约，请求可能重叠，但旧 Token 的原子消费仍保证只有一个请求成功轮换。
- 锁服务对空 Token 返回未授权；等待超时、线程中断或 Redis/Redisson 错误均 fail-closed，不在未持锁时继续轮换，并将中断状态恢复。
- 释放分布式锁时若 Redis 报错会记录错误日志，但不覆盖已经完成的刷新响应；固定 30 秒租约会回收未能主动释放的锁，单 Token 原子消费作为租约提前失效时的最终竞争保护。
- `common-web` 已将 `COMMON_503` 映射为 HTTP 503，供锁服务不可用和等待超时场景使用。
- 新增锁 Key 摘要、锁释放异常、等待超时、中断恢复、空 Token 拒绝、原子消费结果、并发消费失败清理、刷新流程锁顺序和 HTTP 503 映射测试。
- 根工程执行 `mvn -q test` 通过，Surefire 报告累计 42 项测试；`git diff --check` 通过。

### 2026-09-26 本次完成

- 已在真实 Redis 环境启动两个 `auth-service` 实例（8084、8085），并发提交同一枚有效 Refresh Token；实测一个请求返回 HTTP 200、另一个返回 HTTP 401，确认同一枚旧 Token 在多实例竞争下只成功轮换一次。
- 已通过 Gateway 的 `POST /api/auth/refresh` 验证 `Gateway → Nacos 服务发现 → auth-service` 刷新链路可用；Nacos 服务列表中注册了两个 `auth-service` 实例。
- `POST /orders` 已要求客户端传入 `Idempotency-Key`；键按用户隔离，重放相同请求返回已有订单，同键不同内容返回 HTTP 409。
- 订单创建请求的窗口 ID、金额和备注会生成 SHA-256 摘要；金额忽略无意义的小数位差异，防止同一个键被复用于不同订单内容。
- 新订单保存幂等键和请求摘要；数据库唯一索引 `(user_id, idempotency_key)` 负责处理并发重试，插入竞争失败时读取赢家订单，不依赖 Redisson 锁。
- 新增 `COMMON_409` 到 HTTP 409 的统一异常映射，以及订单幂等单元测试。
- 新增订单表初始建表和幂等字段 SQL 迁移说明；之后已在 Docker MySQL 开发库 `order_db` 上执行 `docs/database/migrations/V2__add_order_idempotency.sql`，此前“尚未应用”的事项已完成。
- 迁移后确认 `idempotency_key`、`request_hash` 两列及 `(user_id, idempotency_key)` 唯一索引均已创建；迁移前后 `orders` 行数均为 0。
- Docker daemon 及 MySQL 连通性已验证：MySQL 8.0.46 容器为 healthy，宿主机 `127.0.0.1:3307` 和 Compose 网络 `mysql:3306` 均可用应用账号执行 SQL。
- 已确认客户端幂等键约定：每个新的下单意图生成新键，即使订单内容完全相同也如此；同一次请求重试必须复用原键和原请求内容。相同键与相同内容返回原订单，相同键与不同内容返回 HTTP 409。键由客户端生成，后端不会自动发起重试。
- `mvn -pl order-service,common-web -am test` 在显式指定 Byte Buddy Agent 后通过，共执行 16 项测试；`git diff --check` 通过。

### 2026-09-28 本次完成

- 在 Docker MySQL 持久化库中创建 `product_db`，包含 `dining_windows`、`product_categories`、`products` 三张表；菜品和分类均归属单个窗口。
- 在 `order_db` 创建 `order_items`，保存下单时的菜品编码、名称、单价、数量和行金额快照，并在订单库内关联 `orders`。
- 新增商品目录和订单明细迁移文件及数据库说明；商品库与订单库分开迁移，商品库由具备建库权限的账号初始化。
- 根据实际 `orders.order_id` 为 `BIGINT UNSIGNED` 修正订单明细外键字段类型，并同步修正全新建库用的 V1 建表定义。
- 两份迁移均在 Docker MySQL 执行并重复执行成功；已从 `information_schema` 核对数据库、表、字段及外键。
- 新增 `product-service` Maven 子模块，配置 8086 端口、Nacos 注册、商品库数据源、MyBatis-Plus 和 MySQL 驱动。
- 新增窗口、分类、菜品三个 DO 及对应 Mapper；未创建 HTTP Controller 或业务 API。
- 为本地 MySQL 创建独立 `product_service` 账号，仅授予 `product_db` 权限；凭据保存在被 Git 忽略的本机 `.env`。
- 使用本机配置启动后，服务已成功注册到 Nacos；数据库账号已实际连接 `product_db` 并读取到三张表。
- `mvn -pl product-service -am -DskipTests package` 构建成功；没有新增业务测试。
- 新增 `product-api` 公共契约模块，提供商品批量核价请求/响应及 Feign 客户端。
- `product-service` 新增仅供后端调用的 `POST /internal/products/quote`：校验窗口启用状态、菜品所属窗口和在售状态，返回当前商品价格。
- 商品核价接口采用与其他业务服务一致的 RSA JWT、CUSTOMER 角色和 Redis Access Token 黑名单校验；订单服务的 Feign 拦截器透传 Authorization。
- `POST /orders` 改为接收 `windowId`、`items[{productId, quantity}]` 和可选备注，不再接受客户端价格或订单总额；最多 100 行且同一菜品 ID 不可重复。
- 订单服务通过商品服务核价，计算订单总额，并将商品编码、名称、单价、数量和行金额快照与订单头放在同一事务中保存；创建响应和单笔订单详情包含明细快照。
- 幂等摘要覆盖窗口、排序后的菜品 ID/数量和备注；菜品顺序变化不影响摘要，价格由服务端核算且不影响重放匹配。
- 新增订单下单/重放/409/并发唯一键和商品核价单元测试；为 `product-service` 配置 Surefire 3.5.4，确保 JUnit 5 测试实际执行。
- 构建时发现并修正 `common-web` 统一响应类引用的错误包名。
- `mvn -pl order-service,product-service -am test` 通过：订单服务 9 项测试、商品服务 3 项测试均实际执行并通过。
- 商品服务新增顾客菜单查询：可列出启用窗口，并按窗口返回启用分类、在售菜品和未分类在售菜品；停用窗口返回 404，停用分类及下架菜品不展示。
- 菜单读取接口要求 CUSTOMER JWT，新增 `GET /products/windows` 和 `GET /products/windows/{windowId}/menu`。
- 新增窗口菜单服务测试，覆盖分类分组、未分类商品、停用窗口和非法窗口 ID。
- `mvn -pl product-service -am test` 通过，商品服务共执行 6 项测试。
- 新增可重复执行的本地商品目录 seed SQL，含正常、未分类、下架和停用分类样例。
- 新增并发布 Nacos Gateway 商品路由 `/api/products/** → lb://product-service`，同时将完整路由配置保存到 `docs/nacos/gateway-service.yaml`。
- 已将演示目录数据写入本机 `product_db`，并通过真实 Gateway + JWT 验证窗口列表、菜单过滤及 `order-service → product-service → MySQL` 下单明细快照链路；示例订单总额为 37.00 元。
- 端到端验证创建的临时用户、订单和订单明细已清理，Access Token/Refresh Token 已注销；演示商品目录保留在本机商品库。
- 商品服务新增 ADMIN 专用目录管理接口，覆盖窗口、分类、菜品的查询、新增、全量更新、停用/下架；接口响应使用独立 VO，不直接暴露 DO。
- 窗口和分类停用、菜品下架均为软状态更新；数据库唯一键冲突映射为 HTTP 409，菜品分类归属窗口由服务端校验。
- 商品核价同步检查分类启用状态，避免停用分类下仍在售的菜品绕过菜单过滤创建订单。
- 新增目录管理业务测试、ADMIN 权限约定测试和停用分类核价测试；商品服务全模块 17 项测试通过。
- 新增商品目录管理接口和管理员角色要求文档。当前没有授予 ADMIN 角色的 HTTP 管理流程，部署前需由可信运维流程配置管理员身份。
- 已将本机 MySQL `product_service` 账号密码与被 Git 忽略的 `.env` 配置同步更新，并通过 MySQL TCP 登录执行 `SELECT 1` 验证成功；密码明文未写入项目文档。
- 订单头与订单明细的原子持久化逻辑已拆分为 `OrderPersistenceService` 接口和 `OrderPersistenceServiceImpl` 实现类；事务边界保留在实现方法，订单业务层继续依赖接口。
- 用户确认商品服务 IDEA 启动配置中的 MySQL 和 Redis 连接认证问题已解决；密码通过 `PRODUCT_DB_PASSWORD` 和 `REDIS_PASSWORD` 环境变量提供。
- 新增 `OrderStatus` 枚举并集中维护订单状态及合法迁移：待支付可支付或取消；已支付 → 制作中 → 待取货 → 已完成；已取消和已完成为终态。
- 新增顾客取消接口 `PATCH /api/orders/{orderId}/cancel`，仅允许 `CUSTOMER` 取消本人待支付订单；对其他用户订单返回 404，避免泄露订单存在性。
- 新增管理员履约接口 `PATCH /api/orders/admin/{orderId}/status`，仅允许 `ADMIN` 按顺序推进状态；请求体使用枚举名，例如 `{"status":"PREPARING"}`。管理员不能手工标记支付成功，支付状态留待后续支付回调处理。
- 状态更新通过数据库条件更新同时匹配旧状态和 `version`，成功后递增版本号并更新 `updated_at`；并发更新失败返回 HTTP 409。当前订单表已有 `version` 字段，无需新增数据库迁移。
- 新增订单状态迁移、顾客订单归属、管理员权限契约和并发冲突测试；订单服务 26 项测试通过，全项目 `mvn test` 构建通过。

## 当前下一步

1. 设计支付接入及回调幂等方案，再由支付成功事件将订单从待支付推进为已支付；不得由客户端或管理员直接设置支付成功状态。
2. 后续补管理员身份初始化/授权流程；登录失败计数与账号锁定、Nginx 接入仍按后续阶段规划。

### 缓存防护规划

- 项目统一缓存防护方案：

  ```text
  缓存穿透：空值缓存 + 布隆过滤器
  缓存击穿：Redisson 分布式锁
  缓存雪崩：随机 TTL + Sentinel 降级
  ```

- 其中 Sentinel 指阿里 Sentinel 限流与降级组件，不是 Redis Sentinel 高可用组件。
- 业务数据缓存层加入布隆过滤器，用于拦截不存在的商品、窗口、菜品等 ID，降低缓存穿透对数据库的压力。
- 布隆过滤器采用 Redis 共享方案，后续优先使用 Redisson 的 `RBloomFilter`，保证多实例服务之间使用同一份过滤数据。
- 服务启动时初始化并加载有效数据；新增数据时同步加入过滤器；删除数据时不直接依赖删除位，避免误删其他数据的标记。
- 布隆过滤器判断“不存在”时直接返回未找到；判断“可能存在”时仍必须继续查询 Redis 和数据库，不能把布隆过滤器当作最终数据源。
- 业务缓存同时配合空值缓存、随机 TTL、热点 Key 互斥锁和 Sentinel 降级，分别应对缓存穿透、雪崩、击穿和 Redis/数据库压力问题。
- Refresh Token 只使用 Redis Hash 和 TTL，不接入布隆过滤器。

### Refresh Token 并发刷新优化规划

1. **前端单飞互斥：** 前端接入后，如果多个业务接口同时因 Access Token 过期返回 401，拦截器只发起一次 Refresh Token 请求；其他请求等待这次刷新结果，再携带新 Access Token 各自重试一次，避免同一客户端并发提交同一枚旧 Refresh Token。
2. **后端 Redisson 分布式锁：** 多个并发刷新请求仍可能来自不同浏览器标签页、客户端重试或绕过前端的请求，并被负载均衡到不同 `auth-service` 实例。后端按同一枚 Refresh Token 的安全摘要生成锁 Key；获取锁后重新校验旧 Token，再完成旧 Token 失效和新 Token 签发，避免同一枚旧 Token 被多个实例重复轮换。
- 锁按 Refresh Token 区分，不使用全局锁：不同用户持有不同 Token 时使用不同锁、可以并行刷新；Redisson 锁用于解决同一旧 Token 的并发竞态，不是限制多个用户同时刷新。前端互斥只能减少正常客户端产生的重复请求，不能替代后端锁。
- 锁 Key 不直接包含原始 Refresh Token；同一用户按顺序使用新 Token 刷新属于正常轮换，不应被并发锁当作冲突长期阻止。

## 后续架构规划

- 后续部署阶段引入 Nginx，作为系统对外统一入口和上游负载均衡层。
- Nginx 负责 HTTPS 终止、域名入口、静态资源处理以及 Gateway 多实例之间的负载均衡。
- Nginx 后面部署多个 `gateway-service` 实例，由 Nginx 将外部请求分发到 Gateway 集群。
- Gateway 内部仍通过 Nacos 服务发现和 Spring Cloud LoadBalancer 调用各业务服务；Nginx 不替代 Nacos，也不负责微服务内部服务发现。
- Nginx 接入前，需要先完成 Gateway 无状态化、服务多实例部署、健康检查和统一日志配置。

目标流量链路：

```text
客户端
  ↓
Nginx
  ↓ 负载均衡
Gateway 集群
  ↓ Nacos 服务发现与 Spring Cloud LoadBalancer
业务服务集群
```

### 当前架构学习主线

```text
客户端
  ↓
gateway-service
  ↓
order-service ── OpenFeign ──> user-service ──> user_db
   ├── order_db
   └── OpenFeign ──> product-service ──> product_db
```

- Gateway 负责外部请求路由，不负责业务调用。
- 每个业务服务只访问自己的数据库，不跨服务直接查询其他服务的数据库。
- 服务间调用通过 Nacos 服务发现和 OpenFeign 完成。
- 当前新项目的数据库结构和实体类以本项目最新设计为准，不引用旧项目表结构。

## 重要约定

- 不删除项目数据卷；除非明确需要重置环境，否则不执行 `docker compose down -v`。
- `.env` 包含本机密码，不提交到 Git。
- Nacos 当前使用单机内置 Derby 存储，MySQL 先作为后续业务服务数据库。
- Sentinel 使用 Nacos 持久化规则时，`order-service` 必须同时引入 `spring-cloud-alibaba-sentinel-datasource` 和 `sentinel-datasource-nacos`；前者的 Nacos 适配器依赖为可选依赖，不能只添加前者。
- 本项目开发环境统一由根目录的 `docker-compose.yml` 管理；后续新增基础设施或业务容器时优先扩展这一份 Compose 文件。
- 用户明确约定：收到“完成了”“成功了”“可以了”等完成信号后，直接开始计划中的下一步。
