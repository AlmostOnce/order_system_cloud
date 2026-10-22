# order-system-cloud 项目进度

## 当前状态

更新时间：2026-10-20

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

### 2026-10-12 本次完成

#### 开发环境迁移（Windows 原生 + WSL2）

- 本机端口整体迁移至 9090 段避让并行项目：gateway `9090`、order `9091`、user `9092`、product `9093`、auth `9094`、MySQL `9095`、Redis `9096`、Nacos API `9097`、Nacos 控制台 `9098`、Sentinel `9099/9100`、payment `9101`。
- Docker 由 Docker Desktop 迁移为 Ubuntu-24.04（WSL2）内的 docker.io 引擎；数据卷完整保留，systemd 开机自启，容器 `restart: unless-stopped` 自动拉起。
- Nacos 升级为 v3.0.3（v1 API 已移除）：管理员 `nacos/root`；网关路由配置 `gateway-service.yaml` 与 Sentinel 降级规则已重新发布并回读验证。
- 工具链统一为 JDK 21（系统默认）；根 pom 的 lombok 升至 1.18.48 并配置 `<proc>full</proc>`，兼容 JDK 21/25 双版本编译。
- JWT RSA 密钥对生成于 `E:\home\user\.config\order-system\keys\`；`.env` 补齐 user_db 与 payment 相关变量。

#### 支付模块（payment-service）

- 新增 `payment-service` Maven 子模块（端口 `9101`），接入 Nacos 注册、JWT 资源服务器（CUSTOMER 角色 + Redis 黑名单）、MyBatis-Plus 与独立数据库 `payment_db`；数据库账号 `payment_service` 仅授权本库。
- 新建 `payments` 表迁移：金额为下单快照；`active_flag` 虚拟生成列 + `UNIQUE(order_id, active_flag)` 保证单订单仅一笔进行中支付单；`notify_count`/`last_notify_at` 记录回调轨迹。
- 顾客侧接口（CUSTOMER）：`POST /api/payments`（同订单存在进行中支付单时幂等返回）、`GET /api/payments/{paymentId}`、`GET /api/payments/order/{orderId}`；非本人订单按 404 处理防泄露。
- 支付渠道采用策略模式：`PaymentChannel` 接口 + `MOCK` 模拟渠道实现；回调入口 `POST /internal/payments/notify` 使用 HMAC-SHA256 验签（规范化串 + ±300 秒时间戳窗口 + 常量时间比较）。
- 回调幂等双重防护：支付侧 `WHERE status='CREATED'` 条件更新（重复通知幂等返回，终态通知忽略），订单侧复用乐观锁条件更新；仅条件更新的赢家调用订单推进，Feign 推进失败仅记日志、支付单保持 SUCCESS 不回滚。
- 服务间内部调用采用两种鉴权模式并存：商品核价透传用户 JWT（"代表用户"），支付→订单使用 `X-Internal-Token` 共享密钥（"系统事件"，无用户上下文）。
- order-service 新增 `OrderInternalController`（`GET /internal/orders/{orderId}`、`PATCH /internal/orders/{orderId}/paid`）与 `markOrderPaid`：重复标记按幂等成功处理，乐观锁竞争失败后回读兜底。
- dev profile 提供模拟收银台 `/mock/channel/checkout/{paymentNo}` 与确认支付 `/mock/channel/pay/{paymentNo}`（可指定 `outcome`、`paidAmount` 演练失败与金额不符场景）；渠道异步回调由虚拟线程发起，3 次固定退避重试。
- 网关新增 `/api/payments/** → lb://payment-service` 路由（已发布 Nacos 并验证）；`INTERNAL_ORDER_TOKEN`、`PAYMENT_CHANNEL_SECRET` 等密钥经 `.env` 注入。
- 全项目 `mvn test` 通过，共 128 项测试（payment 35 + order 33 + 其余模块）。

#### 端到端验证结果（真实链路）

- 完整链路验证通过：注册 → 登录 → 下单（待支付 18.50）→ 创建支付单（MOCK 渠道）→ 模拟确认支付 → 渠道带签名回调 → 验签 → 支付单 SUCCESS → Feign 推进订单为"已支付"。
- 回调重放幂等验证：重放同一成功回调返回成功且不产生二次推进，`notify_count` 由 1 增至 2；篡改签名重放返回 401"回调验签失败"。
- 验证过程中修复三个问题：`spring-cloud-openfeign-core` 不携带 feign-slf4j（改用 `spring-cloud-starter-openfeign`）；Feign 默认 HttpURLConnection 不支持 PATCH（引入 `feign-okhttp` 并启用）；`OrderInternalVO.from(null)` 空指针（先判空再转换，随下次重启生效）。
- 数据落库核对：两笔支付均 SUCCESS、金额与订单一致、`notify_count` 符合预期；两订单状态"已支付"、version 正常递增。

#### 当前设备本地端口恢复（未提交）

- 为匹配当前设备已运行的 Docker 容器，将本地工作区配置恢复到旧端口：MySQL `3307`、Redis `6379`、Nacos 控制台/API/gRPC `8082/8850/9850/9851`；Gateway/order/user/auth/product 分别为 `8080/8081/8083/8084/8086`，Sentinel Dashboard/通信端口为 `8858/8719`。
- 新增的 `payment-service` 没有旧端口记录，继续使用 `9101`；数据库、Redis、Nacos 和 JWT issuer 地址已对齐到当前设备旧端口。
- 当前设备的 MySQL 原先缺少 `payment_db` 和 `payment_service` 账号；已执行现有幂等迁移，创建 `payment_db.payments` 表，并授予 `payment_service` 账号仅访问 `payment_db` 的权限。已验证该账号可登录并读取 `payments` 表。
- 本段配置修改仅在当前工作区，尚未提交或推送；不要将其合并到另一台仍需使用 9090 段端口的设备。
- 尚未重启 Docker 容器或应用；Docker 命名数据卷未删除。

#### 权限角色规划（已确认，第一步已实现）

- 目标角色沿用原点餐系统业务划分：`CUSTOMER`（顾客）、`STALL_MANAGER`（档口负责人/店主）、`RESTAURANT_MANAGER`（餐厅经理）、`GENERAL_MANAGER`（总经理）、`SYSTEM_ADMIN`（系统管理员）。
- `SYSTEM_ADMIN` 统一作为平台管理员编码；商品目录文档不再使用宽泛的 `ADMIN`。
- 角色说明可执行的操作类别；具体授权还必须限定 `window_id`、`restaurant_id` 等数据范围。
- 当前代码和本机用户数据仅实际使用 `CUSTOMER`；本次待开发的第一步是用显式环境变量在 `user-service` 安全引导首个 `SYSTEM_ADMIN`，普通注册继续固定为 `CUSTOMER`。
- 当前 `users.role_code` 是单值字符串字段。多角色兼任及档口/餐厅范围关联暂不提前实现，待相关业务接口需要时再设计关联表和服务端授权。
- **已实现（2026-10-13，第一步 SYSTEM_ADMIN 引导）**：新增 `user-service` 的 `AdminBootstrapRunner`（`ApplicationRunner`），启动时读取显式环境变量 `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD`；仅当用户名不存在时创建 `role_code=SYSTEM_ADMIN` 账号（BCrypt 加密），**已存在则跳过且绝不覆盖角色与密码**；未配置或密码长度不足 8 位时跳过并记日志，普通注册仍固定 `CUSTOMER`。`.env` / `.env.example` 已补充该变量；新增 `AdminBootstrapRunnerTest`、`UserServiceImplBootstrapTest` 单测。后续管理员登录后 JWT `roles` claim 自然携带 `SYSTEM_ADMIN`（资源服务器映射 `ROLE_SYSTEM_ADMIN`）。
- **引导实测（2026-10-13）**：全量 `mvn test` 通过（新增 8 项）；真实启动验证：未配置环境变量→跳过；首次配置→`user_db.users` 落库 `admin/SYSTEM_ADMIN`（user_id=4）；同账号换密码重启→"已存在，本次引导未做任何修改"；经网关真实登录 `admin` 返回 JWT `"roles":["SYSTEM_ADMIN"]`。注：user-service 的 boot 插件未绑定 repackage，`mvn install` 产物不可 `java -jar` 直接运行（IDEA 主类启动不受影响）。

### 2026-10-13 本次完成

#### 角色权限体系第二阶段（manager）

- 落地双维度授权：角色门禁（`@PreAuthorize` 粗粒度）+ 数据范围校验（Service 层细粒度）。角色统一为 `CUSTOMER` / `STALL_MANAGER` / `RESTAURANT_MANAGER` / `GENERAL_MANAGER` / `SYSTEM_ADMIN`，代码中原有的宽泛 `ADMIN` 引用全部移除。
- 数据范围模型：`user_data_scopes(scope_type, scope_ref)`，`scope_type` 取 `WINDOW` / `RESTAURANT` / `PLATFORM`；**不进 JWT**，user-service 是唯一事实来源，Redis 缓存键 `authz:user-scope:{userId}`（TTL 300s）+ 变更主动失效。
- `product-service`：新增餐厅模型与内部窗口/餐厅查询接口；档口与菜单管理改为按数据范围校验，角色门禁由 `ADMIN` 改为 `hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')`。
- `order-service`：新增 `DefaultDataScopeService`；履约推进接口改名 `advanceOrderStatus`、路径改为 `/{orderId}/status`，角色改 `hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')`；越权订单按**私有数据返回 404**（不泄露存在性）。
- `user-service`：新增数据范围授予接口 `GET/PUT /users/{userId}/scopes`、`DELETE /users/{userId}/scopes/{scopeId}`（归**餐厅经理〔限本餐厅〕+ 总经理〔全平台〕**，`SYSTEM_ADMIN` 不再拥有数据范围分配权），以及角色分配接口 `PUT /users/{userId}/role`（仅 `SYSTEM_ADMIN`）。
- 授予规则采用"**授予者只能授出自己已具备的范围**"：由操作者范围快照判定，因此只有本身持有 `PLATFORM` 的总经理才能继续授予 `PLATFORM`，餐厅经理不行；无需读取角色码。
- 硬约束落地：禁止修改本人角色编码（403，防自我提权）；`SYSTEM_ADMIN` 不得推进订单状态与改商品目录（**改角色与数据范围校验同时上线**，避免"拿一个洞换另一个洞"）。
- 失败语义统一：未认证 401 / 角色不具备 403 / 私有数据越权 404 / 公开资源越权 403 / 状态机冲突 409 / 范围引用不存在 400 / product-service 不可用 503。内部调用统一 `X-Internal-Token` 共享密钥（`INTERNAL_SCOPE_TOKEN`）。
- 管理操作审计：记录 `actorUserId` / `roleCode` / `action` / `targetType` / `targetId` / `before` / `after` / `result`（当前落结构化日志，通道 `AUDIT`）。
- 新增测试：`UserScopeAdministrationServiceImplTest`（9 项）、`UserServiceImplAssignRoleTest`（4 项）；同时更新 product/order/user 受影响的既有测试（构造器参数、角色断言、方法改名等）。
- 验证：`mvn -B -pl user-service -am test` → 27 项全通过；`mvn -B test` → **13 个模块全部 BUILD SUCCESS**，无失败无错误。
- 修复过程中的一个坑：`assignRole` 原用 `LambdaUpdateWrapper`，单元测试中因缺少 MyBatis 上下文报 `can not find lambda cache for this entity`，已改用字符串列名的 `UpdateWrapper`（与项目其他 Service 一致，也符合"少用 Lambda"规范）。

#### 数据库缺失项补齐（已建服务范围）

- 新增清单文档 `docs/database/SERVICE_DATABASE_INVENTORY.md`：服务 × 数据库 × 表 × 账号 × 迁移脚本 × 内部接口与共享密钥，并区分"已建"与"待建"。
- 核对发现两条**已写好脚本但本机未执行**的迁移（属已建服务的运行时依赖），已在本机 MySQL 执行：`user_db/V2`（`user_data_scopes` 表）、`product_db/V2`（`restaurants` 表 + `dining_windows.restaurant_id`，并插入默认餐厅 `R0001 默认餐厅`）。
- 新增并执行 `user_db/V3__add_pay_password_and_balance_records.sql`：`users.pay_password_hash`（余额支付**独立于登录密码**的支付密码）与 `balance_records` 余额流水表（唯一键 `(user_id, biz_type, biz_ref, change_type)` 保证记账幂等）；支付密码失败计数与锁定改用 Redis 带 TTL 计数器，不落库。
- 核对后本机 MySQL 现状：**4 库 10 表**（order_db: orders/order_items；user_db: users/user_data_scopes/balance_records；product_db: dining_windows/product_categories/products/restaurants；payment_db: payments）。
- 记录的遗留隐患：`user_db` 缺 `CREATE DATABASE` 脚本；`user_service` / `product_service` 账号只存在于 `backup/db-accounts-fix.sh` 未入迁移目录；`product_db/V2` 的 `ALTER` 不幂等；`order-service` 数据源地址与密码写死、未走环境变量（**待统一**）。
- 仍未开始：阶段 B/C 未建服务的库（`stock_db`、`comment_db`+MongoDB、`file_db`、`cart_db`、ES 索引、可选 `notification_db`）与 `order_db.undo_log` 补表；余额充值/支付的业务代码也尚未实现。

#### 餐厅（食堂）管理接口与档口字段补全

- 对照旧单体项目 `order-sys-back` 的 `canteen` / `windows` 设计补全结构：`product_db/V4` 给 `restaurants` 补 `location` / `phone` / `image_url` / `notice` / `sort_order`（不含 `business_hours`），`product_db/V5` 给 `dining_windows` 补 `image_url` / `is_open`。`is_open`（营业口径临时开关）与 `status`（管理口径启用/停用）语义分离。
- 新增餐厅管理接口（角色门禁 `RESTAURANT_MANAGER` / `GENERAL_MANAGER`）：`GET/POST /products/admin/restaurants`、`PUT/DELETE /products/admin/restaurants/{id}`。列表按 `sort_order ASC, restaurant_id ASC` 返回。
- `sort_order` 与餐厅编码属**平台口径**：只有持全平台范围的总经理可新建、停用、调整；餐厅经理只能改本餐厅的名称/位置/电话/门面图/公告，动编码或排序返回 403。餐厅下仍有启用档口时停用餐厅返回 409。
- 新增测试：`RestaurantAdministrationServiceImplTest`（8 项）+ `RestaurantAdministrationControllerTest`（1 项）。

#### 端到端验收（Phase 1 第 7、8 步）与两处修复

- 真机跑通设计文档 §10 验收表（WSL docker 中间件 + 5 个服务实跑）：`STALL_MANAGER` 推进本档口订单 200、推进他人档口订单 **404**、调范围授予接口 403；`SYSTEM_ADMIN` 改角色 200、推进订单 403、调范围授予接口 403；`GENERAL_MANAGER` 授予档口范围 200 且缓存失效；餐厅接口 GM 新建 200 / RM 改本餐厅电话 200 / RM 改 `sort_order` 403。
- **缺陷 1（已修）**：角色不具备时，方法级 `@PreAuthorize` 抛出的 `AuthorizationDeniedException` 未被 `GlobalExceptionHandler` 覆盖，被兜底成 **500**（应为 403）。已在 `common-web` 增加 `@ExceptionHandler(AccessDeniedException.class)` → `403 COMMON_403` 并补单元测试；复验三条由 500 变为 403。
- **缺陷 2（已修）**：`.env` 仍是旧的两名变量 `ADMIN_BOOTSTRAP_USERNAME/PASSWORD`，与代码及设计要求的七个 `USER_*` 变量不一致且总开关未开，导致**初始总经理引导从未执行**（信任根缺失）。已更新 `.env` 为新变量名并开启开关，同时修正 `SERVICE_DATABASE_INVENTORY.md` 中的旧变量名。
- **引导判重已收紧（本次）**：原先用 `username OR phone` 的合并查询判重，手机号被他人占用时会误判成"目标账号已存在"并静默跳过，导致信任根建不出来却无人察觉。现拆成 `selectByUsername` + `selectByPhone` 两次精确查询：用户名命中且已是目标角色 → 幂等跳过；用户名命中但角色不同 → 409（禁止借引导提权）；**手机号被其他账号占用 → 409 显式报错**（宁可启动失败也不让信任根静默缺失）。`UserServiceImplBootstrapTest` 增至 8 项（新增"手机号被他人占用"用例）。
- **验收临时数据已清理（本次）**：删除临时账号 `gm01` 及其 `PLATFORM` 范围、验收插入的订单 `order_id=4`、验收新建的餐厅 `R0002`，并还原 `R0001` 的 `phone`/`location`。清理后重启 `user-service`，引导**成功创建** `general_manager`（user_id=8）及其 `PLATFORM` 范围，登录验证通过（JWT `roles=["GENERAL_MANAGER"]`）。保留 `stall01`（WINDOW:1）、`rm01`（RESTAURANT:1）作为 Phase 1 第 8 步要求的可登录测试账号。
- **环境提醒**：本次发现 IDEA 内运行的是过期代码（进程 09:0x 启动、`target/classes` 12:06 才编译，对新端点一律 500）——改完代码必须在 IDEA 内重启服务；根 pom 未继承 `spring-boot-starter-parent`，`mvn install` 产物是瘦 jar，命令行启动需再执行 `spring-boot:repackage`。

### 2026-10-14 本次完成

#### 账号启用/禁用接口（Phase 1 收尾项）

- `PATCH /users/{userId}/status`（`UserStatusAdministrationController`），仅 `SYSTEM_ADMIN` 可调用；禁止禁用自身，禁止禁用最后一个处于启用状态的 `SYSTEM_ADMIN`（409）。
- 单测：`UserServiceImplUpdateStatusTest` 6 项通过。

#### 余额支付（余额渠道接入 payment-service）

- **账务归属**：余额扣款与流水记账全部留在 `user-service` 同库同事务，`payment-service` 只做支付编排，通过内部接口调用，不跨服务开分布式事务。
- **三重幂等记账**：`balance_records` 唯一键 `(user_id, biz_type, biz_ref, change_type)` 保证同一业务单据只记一次；扣款流程为「先插流水占位 → `balance >= amount` 条件扣款 → 回填扣后余额」，并配合 `@Transactional(noRollbackFor = DuplicateKeyException.class)` 把唯一键冲突当作幂等重放而非失败。并发下只有赢家真正扣款，其余请求回读既有流水返回 `idempotent=true`。
- **支付密码独立于登录密码**：首次设置必须校验登录密码、且新支付密码不得与登录密码相同；修改必须校验原支付密码。校验失败采用 Redis 计数器 `user:pay-password:fail:{userId}`（TTL 15 分钟，满 5 次写 `user:pay-password:lock:{userId}` 锁定 15 分钟），校验成功清空计数；Redis 不可用时 **fail-closed 返回 503**，绝不放过校验。
- **对外接口**：`GET /users/me/balance`、`PUT /users/pay-password`，仅 `CUSTOMER` 角色，`userId` 一律取自 JWT subject。
- **内部接口**：`POST /internal/users/{userId}/balance/deduct`，先校验 `INTERNAL_SCOPE_TOKEN` 再执行业务；`payment-service` 侧不为该客户端配置 fallbackFactory，以保证「余额不足 / 未设密码 / 密码错误 / 已锁定」等业务拒绝不被吞成 503。
- **payment-service 渠道改造为路由**：新增 `PaymentChannelRegistry`（按 `channelCode` 路由，未知渠道 400）与 `BalancePaymentChannel`（`BALANCE`，只生成交易号、不参与状态机）；`PaymentCreateRequest` 增加 `channelCode`（缺省 `MOCK`）与 `payPassword`。余额渠道为**同步结算**：创建支付单后立即扣款，扣款成功再条件置成功并推进订单，不产生异步回调；其他渠道仍返回收银台凭证等回调。
- **`handleNotify` 顺序调整**：由「先验签后查单」改为「先查单、再按 `paymentDO.channelCode` 解析渠道验签」——不同渠道密钥算法不同，必须由支付单上记录的渠道决定验签实现。查不到支付单返回 404，验签失败返回 401。
- **内部令牌按路径区分**：`PaymentFeignConfig` 仅对 `/internal/orders/**` 追加 `INTERNAL_ORDER_TOKEN`；余额扣款请求的令牌由客户端以方法参数显式传 `INTERNAL_SCOPE_TOKEN`。此前拦截器对所有 Feign 请求统一加同一个头，会产生同名头重复写入、下游取到错误令牌的隐患。
- **下游失败的语义映射**：`FeignException` 按 HTTP 状态映射为本地业务异常——400 参数/凭据、404 不存在、409 冲突（余额不足、未设支付密码）、429 限流锁定（密码锁定），其余（含连接失败与 5xx）统一 503 服务不可用；提示文案从下游统一响应体的 `message` 解析，解析失败退回错误码默认文案。
- **`common-web` 补充 429 映射**：`GlobalExceptionHandler` 原先未覆盖 `TOO_MANY_REQUESTS`，会落到 500，导致密码锁定语义丢失；现已映射为 HTTP 429。
- **测试**：新增 `PayPasswordServiceImplTest`（13 项：首次设置/修改校验、失败计数与 TTL、锁定优先、Redis 故障 fail-closed）、`UserBalanceServiceImplTest`（8 项：正常扣款、既有流水幂等重放、唯一键冲突按重放处理、余额不足回滚、入参校验）；`PaymentServiceImplTest` 扩到 21 项（新增渠道路由、余额同步结算、余额不足与锁定映射，并按新签名与「先查单后验签」调整）。全量 `mvn -B -pl user-service,payment-service,user-api,common-redis,common-web -am test` 通过：user-service 55 项、payment-service 39 项，`BUILD SUCCESS`。
- **本次未做（按约定留后）**：余额充值、余额退款接口；余额渠道的关闭支付单对账。

#### 接口文档（OpenAPI / Swagger）集成

- **选型**：springdoc-openapi `2.9.1`（Boot 3.x 线），版本由根 `pom.xml` 的 `springdoc-openapi-bom` 统一管理；5 个业务服务用 `springdoc-openapi-starter-webmvc-ui`，Gateway 用 `springdoc-openapi-starter-webflux-ui`，`common-web` 以 `provided` 引入（不向下游传递）。
- **统一入口**：网关聚合页 `http://localhost:9090/swagger-ui/index.html`，下拉框可切换 auth / user / order / product / payment 五个分组；各服务也可直连查看，如 `http://localhost:9092/swagger-ui/index.html`。
- **网关文档路由**：在 Nacos 配置 `gateway-service.yaml`（仓库镜像 `docs/nacos/gateway-service.yaml`）中为每个服务新增 `-docs` 路由，**前插**于对应 `/api/xxx/**` 路由之前（Gateway 首个匹配生效），并使用 `RewritePath=/api/xxx/v3/api-docs, /v3/api-docs`（不可用 `StripPrefix=1`，否则落到不存在的路径）。
- **server 前缀修正**：经网关重写路径后各服务自动生成的 `servers` 地址会偏，故 `OpenApiDocumentationConfig` 显式设置 Server（`springdoc.gateway-base-url` + `springdoc.gateway-path`）。**注意**：网关路由 `StripPrefix=1` 只剥离 `/api` 一段，故 `gateway-path` 五个服务统一为 `/api`（曾误设为 `/api/users`、`/api/orders` 等，会拼出 `/api/users/users/...` 的空地址），对外地址 = `http://localhost:9090/api` + 服务自身路径。
- **共享文档配置**：`common-web` 新增 `com.hbue.ordering.common.web.docs.OpenApiDocumentationConfig`，统一提供标题/描述/版本、`bearerAuth` 安全方案与全局安全要求（UI 右上角出现 Authorize 按钮），随各服务组件扫描生效。
- **鉴权白名单**：5 个业务服务的 SecurityConfig 与 `GatewaySecurityConfig` 追加放行 `/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html`，网关另放行 `/api/*/v3/api-docs`；`/internal/**`、`/mock/**` **不新增网关路由**，维持"内部接口不出网关"边界。
- **文档标注**：18 个 Controller 全部加 `@Tag`（中文分组名），内部接口统一标为 `内部接口（服务间调用）`。
- **开关**：各服务（含网关）`springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` 取自环境变量 `SWAGGER_ENABLED`，默认 `true`；设为 `false` 即可关闭，无需改代码。
- **验证**：全量 `mvn -B -pl gateway-service,order-service,user-service,auth-service,product-service,payment-service -am test` 通过（user-service 55、payment-service 39、order 34、auth 25、product 26、gateway 5、common-web 4、common-redis 9），`BUILD SUCCESS`。
- **导入 Postman**：已生成可直接导入的文件（`docs/postman/`）——合并版 `order-system-cloud.gateway.openapi.json`（33 条对外接口，baseUrl=`http://localhost:9090`）+ 5 个单服务文件（含 `/internal/**`、`/mock/**`，供直连调试）。也可实时导出 `http://localhost:9090/api/{svc}/v3/api-docs`。用法详见 `docs/API_DOCS.md`。

#### 缓存防护（穿透/击穿/雪崩）与 Sentinel 降级

- **Redisson 下沉 common-redis**：`redisson-spring-boot-starter` + `redisson-spring-data-35`（排除 `-41`，Redisson 4.7.0）在 `common-redis` 中以 `optional=true` 声明，配合 `RedissonInfrastructureConfig` 的 `@ConditionalOnClass(RedissonClient.class)` 条件装配——未显式引入 Redisson 的服务（尤其 WebFlux 网关）不受影响。`common-redis` 新增可复用底座 `DistributedLockService`（抢不到锁返回 `null` 而非抛异常，由调用方降级）与 `RedisBloomFilterService`（未初始化或故障时 **fail-open 放行**）。
- **三防落点（product-service 菜单热点读路径）**：**穿透** = 空值缓存（60s）+ Redisson `RBloomFilter`（窗口/菜品两个过滤器，误判率 1%，启动时由 `ProductBloomFilterInitializer` 全量灌入，写操作提交后补写、不删位）；**击穿** = `RLock` 互斥 + 双重检查（未抢到锁降级直查库、不写缓存）；**雪崩** = 基础 TTL 300s + 0~120s 随机抖动。`getWindowMenu` 执行顺序为 布隆 → 空值标记 → 缓存 → 锁 + 双重检查 → 回源写缓存。
- **核价不缓存**：`ProductQuoteServiceImpl.quote` 价格与售罄必须实时，只用布隆过滤器拦掉一定不存在的菜品 ID；候选集合为空时提前返回，**绝不用空集合拼装 `in` 条件**（否则生成非法 SQL）。
- **缓存失效改为事务提交后执行**：9 处写操作原在 `@Transactional` 方法体内、提交前删缓存，存在「T1 更新库 → 删缓存 → T2 未命中读旧值 → 回写 → T1 提交」导致旧值驻留 300~420 秒的竞态。现统一经 `afterCommit` 助手注册到 `TransactionSynchronizationManager`，事务回滚时因不触发回调而自然跳过。
- **Sentinel 降级接入 product-service**：引入 sentinel 三件套依赖（`spring-cloud-starter-alibaba-sentinel`、`spring-cloud-alibaba-sentinel-datasource`、`sentinel-datasource-nacos`）；`application.yml` 新增 Sentinel 配置（Dashboard `127.0.0.1:9099`，本服务通信端口 `9102` 以避开 order-service 的 `9100`），降级规则从 Nacos `product-service-degrade-rules`（`SENTINEL_GROUP`）动态拉取。
- **规则用 URL 模式、零业务侵入**：资源名为服务自身映射路径（不含 `/api`，因网关 `StripPrefix=1` 已剥离），针对 `/products/windows/{windowId}/menu` 与 `/products/windows` 两条热点读接口配置慢调用比例降级（慢调用阈值 500ms、统计窗口 10s、最小请求数 20、慢调用占比 ≥50%、熔断时长 10s）；符合 `JAVA_CODING_STANDARDS` 的「规则放服务边界」原则，未在业务类加 `@SentinelResource`。
- **统一降级响应**：新增 `SentinelBlockExceptionHandler`，把 `BlockException` 由 Sentinel 默认的 429 纯文本转换为项目统一响应体 `ApiResponse` + HTTP 503（`COMMON_503`）。
- **配置清单落库**：新增 `docs/nacos/product-service-degrade-rules`，`backup/nacos-init.sh` 增加发布与回读步骤（已发布到本机 Nacos 并回读一致）。
- **验证**：`mvn -B -pl product-service test` 通过 32 项测试（新增事务提交后失效的回调回归用例）；临时实例启动后 `curl http://127.0.0.1:9102/getRules?type=degrade` 返回两条规则，证明从 Nacos 拉取并生效。
- **运行期端到端验证**：以 `CUSTOMER` 账号预热 `product:windows:active` / `product:menu:window:1` 后，用具备 `WINDOW:1`+`RESTAURANT:1` 范围的档口账号 `PUT /products/admin/windows/1`，响应 200 返回后立即 `EXISTS` 两键为 `0`（证明失效已推迟到事务提交后执行），再次读取后两键回填为 `2`；降级侧临时下发易触发规则后连续请求，第 3 次起返回 `COMMON_503`（HTTP 503），还原规则后恢复正常。
- **已知约束**：Redis 故障时布隆过滤器 fail-open 放行，最坏退化为一次数据库查询，不会误拦真实数据。
- **待确认设计点**：`updateWindow` 同时校验窗口范围与目标餐厅范围，因此仅持有 `WINDOW` 范围（未持有 `RESTAURANT` 范围）的档口负责人无法更新本档口；若希望档口负责人可维护本档口基础信息（不改挂餐厅），需放宽为「餐厅范围仅在 `restaurantId` 变更时校验」。

#### Java 中文注释补充

- 已审阅各模块生产源码，为 150 个 Java 文件补充或细化中文类/方法 Javadoc 和关键方法内 `//` 注释；其余文件已有足够准确的注释，因此保持不动。
- 注释重点说明分层职责、接口参数与结果，以及认证授权、订单幂等与状态竞争、余额扣款、支付回调、缓存/布隆过滤器、远程调用失败等关键业务原因和边界；避免逐行复述代码。
- `docs/JAVA_CODING_STANDARDS.md` 已补充更明确的注释深度要求，后续代码按此执行。
- 验证：全项目 `mvn test` 通过，13 个 Reactor 项目均为 `SUCCESS`；`git diff --check` 通过。确认 Java 可执行语句无差异。本次不包含 Docker 端口文件中原有的未提交改动，也未创建提交。

#### 登录失败计数与账号临时锁定

- `auth-service` 新增 `LoginAttemptService` 契约及 Redis 实现；失败次数按账号摘要存储，不把原始用户名暴露在 Redis Key 中，未知用户名也走相同计数路径，避免用响应差异探测账号是否存在。
- 默认策略为 15 分钟固定失败窗口内累计 5 次；达到阈值后锁定 15 分钟。策略可通过 `AUTH_LOGIN_MAX_FAILURES`、`AUTH_LOGIN_FAILURE_WINDOW`、`AUTH_LOGIN_LOCK_DURATION` 环境变量调整。
- `common-redis` 新增 Lua 原子自增并首次设置 TTL 的基础操作，避免 `INCR` 与 `PEXPIRE` 分成两条命令时进程中断而留下无过期计数。
- 登录在调用 `user-service` 前检查锁定状态；错误凭据计数，达到阈值返回 `COMMON_429`；凭据校验成功后清除失败计数与锁定 Key。Redis 无法确认或更新保护状态时 fail-closed，返回 `COMMON_503`。
- 新增认证服务链路和 Redis 保护服务测试，覆盖阈值、锁定拦截、成功清理、摘要 Key 与 Redis 故障场景；全项目 `mvn test` 通过，13 个 Reactor 项目均为 `SUCCESS`。运行期 Postman 验收仍待进行。

### 2026-10-17 本次完成

#### 购物车服务（cart-service）

- **骨架与安全**：新增 `cart-service`（包名 `com.hbue.ordering.cart`，端口 9103，可用 `CART_SERVICE_PORT` 覆盖）并注册到父工程；接入 Nacos 服务发现、Redis、无状态 JWT 资源服务器、RSA 公钥、roles 角色转换与共享黑名单校验；购物车端点只允许 `CUSTOMER`，安全过滤链统一返回 401/403 JSON。新增 `GET /cart/ping` 与 Swagger 文档，更新仓库 Nacos 路由模板与网关 Swagger 分组。
- **核心业务**：新增七个业务接口——查询、添加累加、设置数量、勾选、删除单项、清空窗口，以及订单成功后的 `POST /cart/orders/{orderId}/cleanup`（经网关为 `/api/cart/orders/{orderId}/cleanup`）；身份仅取 JWT `sub`，数量与路径参数均有校验，接口示例见 `docs/CART_API.md`。分层为 Controller → `CartService` → `CartServiceImpl` → `CartRedisRepository`，请求 DTO、展示 VO 与 Redis `CartItemDO` 分包管理。
- **存储口径（2026-10-17 用户确认）**：**Redis 为唯一业务存储**，`cart:user:<userId>` Hash，Field 为 `windowId:productId`；每用户最多 100 类菜品、每项最多 99 份、最后修改后保留 30 天（读取不续期）；开启 AOF + 数据卷；**取消 MySQL 备份库（`cart_db`）与游客合并，仅登录顾客使用**，覆盖此前双存储规划。
- **并发与一致性**：Lua 脚本把容量检查、数量累加/修改、状态更新与 TTL 续期放在同一原子操作内；大主键以 JSON 字符串保存，避免超过 2^53 后 Lua 浮点精度丢失。每次修改生成新 UUID `revision`，供按快照清理使用；`cart-cleanup.lua` 原子比较原始版本、数量与勾选状态，并发修改、删除重建、其它用户/窗口与不存在项均不误删，坏数据不部分删除。
- **跨服务调用**：复用 `product-api` + OpenFeign + LoadBalancer + Sentinel 降级工厂，透传当前 `Authorization`；添加时校验可售状态，GET 每窗口批量核价并以 `BigDecimal` 聚合计勾选可售金额，失效项保留、价格为 `null`，商品故障返回 503，删除与本地修改不依赖商品服务。新增 `order-api` 只读购买事实契约（`OrderCartSnapshotVO` / `OrderPurchasedItemVO` / `OrderServiceClient` + 降级工厂），清理前按订单 ID、用户、窗口与完整商品数量集合核实。
- **未建立跨服务事务**：清理与下单为两个独立请求；订单失败不调用清理，清理失败不回滚订单，使用原订单 ID 与原快照重试，禁止刷新版本后再次清理或更换幂等键重复下单。
- **其它修复**：补齐 `common-web` 的参数类型异常映射（路径 ID 非数字时返回统一 400 而非 500）；修正订单 Feign 超时配置未匹配 `contextId` 的问题（改为 `cartOrderServiceClient` 配置键后连接 2 秒 / 读取 3 秒并按真实装配测试验证 Token 透传）。
- **验证**：开启 `CART_REDIS_IT` 后全项目 `mvn -B test` 通过，购物车模块 59 项测试全部通过（其中 16 项真实 Redis 用例，无跳过）；订单生产方真实详情 JSON 与 `order-api` 的兼容测试通过；临时端口实例装配验证后已停止，未覆盖 IDEA 运行实例。
- **使用前提示**：需在 IDEA 重新加载 Maven 并重启 `cart-service`；`/api/cart/**` 路由已就绪，前端需显式串联下单与清理。

#### 商品演示数据编码修复（2026-10-17）

- 已确认本机 MySQL 命令行连接默认使用 latin1，旧演示数据的中文被错误转码存储；表本身仍是 utf8mb4。
- 修复前已备份 `product_db` 到本机 `/home/user/.local/state/order-system/db-backups/product_db-before-encoding-repair-20261017.sql`（备份不进仓库）。
- 演示脚本增加 `SET NAMES utf8mb4`，窗口改为按唯一编码更新，支持重复导入时恢复名称与位置；新增 `docs/database/seeds/product_catalog_encoding_repair.sql`，按演示商品编码定位原分类并恢复中文，保留分类 ID。
- 已使用 utf8mb4 连接，在同一事务中执行分类修复与演示数据重导，未删除表或清空数据库；导入后逐条核对：1 个窗口、3 个分类、5 个商品，中文名称与描述正常，原有主键与分类关联不变。

### 2026-10-19 本次完成

#### 库存服务（stock-service）与 Seata AT 分布式事务

- **补记（2026-10-16）**：`file-service`（端口 9102、独立 `file_db.file_objects`）已落地——`FileStorage` 存储抽象 + 本地实现（落盘路径按内容摘要生成，天然去重）、魔数校验真实类型、内容接口匿名开放供图片直引；因占用 9102，`product-service` 的 Sentinel 通信端口让位改为 `9110`；网关新增 `file-service` / `file-service-docs` 路由。
- **新增 `stock-service`（端口 9104）与契约模块 `stock-api`**：库存从"无校验"变为真实扣减，消除"当前可无限下单"的缺陷。`stock-api` 提供 `StockDeductRequest` / `StockRestoreRequest` / `StockVO` 与 `StockServiceClient` + 降级工厂；`order-service` 与 `stock-service` 同时依赖，保证契约一致。
- **独立库 `stock_db`**：`stock`（唯一事实来源，`uk_window_product` 唯一键、`version` 乐观锁）、`stock_flow`（流水，`uk_biz_flow(biz_type, biz_ref, stock_id)` 保证同一单据只记一次）、`undo_log`（AT 回滚日志）；同时补 `order_db.undo_log`。两个迁移脚本均幂等，已在本机 MySQL 执行，账号 `stock_service` 仅授权 `stock_db`。
- **库存三重防线**：① Redis Lua 原子预扣（脚本原子判断 + 扣减，Redis 不可用时**降级直查/直改 DB**，Redis 只是加速快照、带 TTL 到期自动回源）；② DB 行锁 + `version` 乐观锁条件更新（`available_qty >= ? AND version = ?`），并发下不会扣成负数；③ `stock_flow` 唯一键幂等，重复扣减/重复回补只记一次账。任一环节失败均抛出明确业务错误码。
- **Seata AT 落地**：Seata Server `apache/seata-server:2.5.0` 加入 `docker-compose.yml`，`file` 存储模式，端口 `9109:9109`（控制台 `9108:7091`），以 `seata-server` 之名注册到现有 Nacos（`SEATA_GROUP` 组、cluster `default`）；服务端事务分组 `order-system-tx-group`，客户端 `vgroup-mapping` 指向 `default`。**注意（2026-10-20 修正）**：TC 注册到 Nacos 的地址是容器内网 IP，Windows 宿主机不可达，因此本机开发时客户端改为直连 `127.0.0.1:9109`，详见下方"2026-10-20 / Seata 客户端连接方式修正"。注意：本机运行的是 **2.5.0**（镜像 tag `2.5.0`，包名已迁至 `org.apache.seata`），注解为 `org.apache.seata.spring.annotation.GlobalTransactional`，不是教程里常见的 `io.seata.*`。
- **order-service 接入**：`createOrder` 标 `@GlobalTransactional(name = "create-order-deduct-stock", rollbackFor = Exception.class)`；订单与明细落库后调用 `StockServiceClient.deduct`，库存不足或库存服务不可用时抛异常 → 全局回滚，订单、明细、库存、`undo_log` 一并回滚。
- **内部接口错误语义**：`POST /internal/stocks/deduct`、`POST /internal/stocks/restore`、`GET /internal/stocks/{windowId}/{productId}` 校验 `X-Internal-Token`；**业务失败返回 HTTP 200 + 失败业务码**——若按 4xx 返回，调用方 `FeignException` 会走 Sentinel 降级工厂，把"库存不足"替换成笼统的"服务不可用"，用户看不到真实原因。
- **定时对账兜底**：`StockReconcileTask` 默认启动 60 秒后首次执行、之后每 10 分钟一次（`stock.reconcile.*` 可调/可关）：Redis 快照与 `stock.available_qty` 不一致时以 **DB 为准回写快照**；`total_qty + sum(change_qty)` 与 `available_qty` 不等时**只告警不改数**（流水异常属于需要人看的信号）。结束时打印"检查 X 条 / 修正快照 Y 条 / 流水不一致 Z 条"。
- **网关与文档**：Nacos `gateway-service.yaml` 只新增 `stock-service-docs` 路由（`/api/stocks/v3/api-docs` → `RewritePath` 到 `/v3/api-docs`），**不加 `/api/stocks/**` 业务路由**——该服务当前对外只有 `/internal/**`，而项目约定内部接口不出网关；网关 Swagger 聚合下拉框同步补上 `file-service` / `stock-service`。
- **测试**：`stock-service` 单测 10 项（并发扣减不超卖、重复扣减幂等、回补幂等、Redis 降级、快照读取异常等）；`order-service` 36 项（新增"库存不足 → 409"与"库存服务不可用 → 503"两个用例）。
- **全量验证（2026-10-19）**：`mvn -B -o test` → **BUILD SUCCESS**，16 个 Reactor 模块全部 `SUCCESS`，41 个测试类共 **236 项测试全部通过**（common-redis 10、gateway 5、common-web 4、order 36、user 55、auth 34、product 32、payment 39、file 11、stock 10）。过程中发现 `product-service` 的 `ProductMenuServiceImplTest` 已与实现脱节（实现早前新增了 `RestaurantMapper` 入参，测试仍按 6 参构造），因增量编译复用陈旧 class 才一直"通过"；已补 `@Mock RestaurantMapper` 与构造参数，修正后该模块 32 项全通过。
- **遗留（已知，未做）**：① **取消订单不回补库存**——`cancelOrder` 仍只改状态，`StockRestoreRequest` 与 `/internal/stocks/restore` 已备好但尚未接入；② `stock_db.stock` 目前是空表，需按档口菜品初始化库存记录（`stock.reconcile` 对无快照的行会跳过）；③ 下单扣库存的**端到端验证**（库存不足全局回滚、并发下单不超卖）需在 IDEA 重启 `order-service` 并启动 `stock-service` 后执行。

### 2026-10-20 本次完成

#### 购物车模块合并修复

- 另一台设备推送的购物车模块（`cart-service` + `order-api`）与本机库存模块合并后，Git 未报冲突标记，但**整段取了库存一侧**，静默丢失了购物车侧的改动。以共同祖先 `ab3785d` 为基点比对两侧改动后逐项修复。
- 根 `pom.xml` 丢失 `<module>order-api</module>` 与 `<module>cart-service</module>`，导致全量构建在 `order-service` 处报 `Could not find artifact com.hbue.ordering:order-api`；补回后 Reactor 恢复为 18 个模块。
- `docs/nacos/gateway-service.yaml` 中购物车路由覆盖了 `file-service-docs` 与 `/api/files/**` 两条路由，导致文件服务从网关消失；补回后 cart / file / stock 三套路由共存，并已重新发布到本机 Nacos 回读校验。
- 网关 `application.yml` 的 Swagger 聚合下拉框丢失 `cart-service`；三份文档（`ARCHITECTURE.md` / `PROJECT_PROGRESS.md` / `SERVICE_DATABASE_INVENTORY.md`）中购物车侧的登记内容被整段丢弃，已补回并订正服务数（9 个）、模块数（17 个）、购物车"仅 Redis"存储口径与 `cart_db` 取消登记。
- 验证：`mvn -B -o clean test-compile` 18 个模块全 `SUCCESS`；`mvn -B -o test` **BUILD SUCCESS**。

#### Seata 客户端连接方式修正（本机开发）

- **现象**：`stock-service` 在 IDEA 中启动到 `globalTransactionScanner` 时报 `can not connect to [172.19.0.3:9109]`，进程退出码 1，日志尾部只剩若干 Nacos `NotifyCenter` 的 `InterruptedException`（属**关闭阶段的次要噪音**，非根因）。
- **根因**：TC（Seata Server）跑在 WSL 的 Docker 内，注册到 Nacos 的地址是**容器内网 IP**（`172.19.0.3:9109`）；WSL2 默认 NAT 模式下 Windows 宿主机没有到容器网段的路由，该网段流量被本机代理软件的 TUN 接口接管（实测连接 `172.19.0.3:9109` 的本地地址为 `198.18.0.1`），表现为"TCP 看似可连、但 30 秒无响应"。
- **试过但无效**：`docker-compose.yml` 为 seata 容器加 `SEATA_IP=127.0.0.1`——本镜像（jib 构建，`apache/seata-server:2.5.0`）并不会把它转成注册地址，重建后 Nacos 中仍是 `172.19.0.3`，已移除该无效配置并留注释说明。
- **落地修复**：`order-service`（TM）与 `stock-service`（RM）的 Seata 客户端不再走注册中心发现，改为 `seata.registry.type=file` + `seata.service.grouplist.default=${SEATA_TC_GROUPLIST:127.0.0.1:9109}`，直连 Docker 映射到宿主机的 9109 端口。**容器化部署时需改回 `type: nacos`**（或在 compose 网络内使用容器可达地址），两个服务的 yml 已就地注明。
- **验证**：`stock-service` 启动成功（`will connect to 127.0.0.1:9109`、Tomcat 9104、`Started StockServiceApplication`），`GET /internal/stocks/1/1` 带 `X-Internal-Token` 返回 200、不带返回 401，`/actuator/health` 为 `UP`；`order-service` 同样启动成功（Tomcat 9091）并注册到 Nacos。验证后已停止两个手工启动的实例。

## 当前下一步

### 本次优先级调整（2026-10-22）

1. **当前进行：销量榜消息化**。订单确认支付与本地 outbox 同事务写入，通过 RocketMQ + Spring Cloud Stream 发布，消费端使用 Redis ZSet + 原子去重更新累计销量；历史初始化、Redis 丢失重建和定时对账保留。MQ 故障不能阻断支付；失败发布持续重试、失败消费交由 Broker 重试和死信处理。详见 [销量榜](SALES_RANKING.md)。
2. **下一步：库存事件与缓存一致性改造**。保留 order × stock 的 Seata AT 同步扣减/取消回补边界，不把核心扣库存直接改成不确定结果的异步消息。库存流水与库存变更事件在 stock_db 同库事务持久化，通过 RocketMQ 传播已提交变更，用于缓存失效/重建、库存预警和后续统计；消费按流水业务唯一键去重，失败重试、死信告警和库存对账兜底。必须验证 Seata 全局回滚时不发布已回滚事件、消息乱序不覆盖较新库存、Redis 不可用/恢复和重复回补场景。具体文档见 [库存接入下一步](STOCK_INTEGRATION.md#下一步库存事件与缓存一致性改造)。本次只登记计划，不更改库存核心流程。


0. ~~购物车服务（cart-service）~~ **已完成（2026-10-17）**：Redis 增删改查、商品实时核价与订单成功后的按提交快照清理接口已实现，详见"2026-10-17 本次完成"；**取消** MySQL 备份与游客合并。前端接入时需显式串联下单与清理。
1. ~~后续补管理员身份初始化/授权流程~~ **已完成（2026-10-13）**：`SYSTEM_ADMIN` 引导 + manager 数据范围权限体系已落地，详见"2026-10-13 本次完成"。~~账号启用/禁用接口与"最后一个启用的 `SYSTEM_ADMIN` 不可禁用"~~ **已完成（2026-10-14）**。~~登录失败计数与账号临时锁定~~ **已实现（2026-10-14），待运行期 Postman 验收**。Nginx 接入仍按后续阶段规划。
2. ~~余额支付（余额渠道接入 payment-service）~~ **已完成（2026-10-14）**；**模拟充值和账户中心接入代码已补齐（2026-10-22）**：支付密码校验后直接到账、同库记账、请求幂等、本人流水分页及资料接口；用户端增加余额、充值、充值/消费记录和支付密码页面。详见 [账户中心与模拟充值](WALLET_API.md)。本次只做源码静态检查，未运行 Maven、单元测试或重启联调；退款与余额渠道定时对账仍留后。
3. ~~缓存防护（穿透/击穿/雪崩 + 布隆过滤器）~~ **已完成（2026-10-14）**，覆盖商品菜单热点读路径并接入 Sentinel 降级，详见"2026-10-14 本次完成"；后续可把同一套防护推广到 user-service 等其它热点读路径。
4. 支付模块可选增强：渠道关闭支付单的定时对账、auto-confirm 开启后的自动化演练、FAILED/CLOSED 分支的界面化演示。
5. ~~引导账号判重收紧与验收临时数据清理~~ **已完成（2026-10-13）**：判重改为用户名/手机号分开精确查询、手机号被占显式报错；临时数据已清理，`general_manager` 引导创建成功并验证可登录。
6. ~~库存服务（stock-service + Seata AT）~~ **接入代码已补充（2026-10-22）**：取消待支付订单已接回补和全局事务；商品服务聚合实时库存，用户端显示剩余数量并限制加购；初始化脚本已提供，真实数量由用户填写。本次仅源码静态检查，未执行数据库脚本、测试或服务启动。仍待真实库存初始化及端到端验证（不足全局回滚、并发下单、取消回补），详见 [库存接入](STOCK_INTEGRATION.md)。

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
