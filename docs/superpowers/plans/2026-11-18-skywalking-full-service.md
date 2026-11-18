# 全量 SkyWalking 链路追踪实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在全部 15 个 Java 服务、可选 Docker Gateway 和本地开发环境中接入可运行的 SkyWalking 链路追踪。

**Architecture:** Compose 以独立 `skywalking` profile 运行 BanyanDB、OAP 和 Horizon UI；Java Agent 9.8.0 通过 `-javaagent` 接入本机 IDEA JVM 和可选 Gateway 容器。每个 JVM 使用独立服务名，Agent 二进制与本地 Horizon 管理密码只保存在被忽略的本机文件中。

**Tech Stack:** SkyWalking OAP 11.1.0-java21、BanyanDB 0.11.2、Horizon UI horizon-1.1.0、Java Agent 9.8.0、Docker Compose、IntelliJ IDEA shared run configurations。

**Spec:** [docs/superpowers/specs/2026-11-18-skywalking-full-service-design.md](../specs/2026-11-18-skywalking-full-service-design.md)

## Global Constraints

- 不运行、不建议运行或触碰 Maven、Maven Wrapper、Maven 生命周期、测试、打包、清理及相关服务启动/重启命令。
- 本次不新增或运行测试；只做文档、Compose、IDEA 配置的静态核对与 Compose 配置解析。
- 不修改业务 Java 源码、POM、API、数据库迁移或用户已有的 `.idea/workspace.xml` 运行配置。
- 本机 Agent 放在 `.local/skywalking-agent/` 并由 `.gitignore` 忽略；本机密码与哈希只写入根目录 `.env`，不得写入已跟踪文件或命令输出。
- 15 个 Java 服务名分别使用 `gateway-service`、`order-service`、`user-service`、`product-service`、`auth-service`、`payment-service`、`file-service`、`cart-service`、`stock-service`、`comment-service`、`search-service`、`notification-service`、`statistics-service`、`recommendation-service`、`coupon-service`。
- 本机 Agent 上报 OAP 使用 `127.0.0.1:11800`；容器内 Gateway 使用 Compose DNS `skywalking-oap:11800`。
- Horizon 只绑定宿主机回环地址；OAP 对宿主机发布的端口也只绑定 `127.0.0.1`。
- 组件版本使用设计文档中锁定的官方兼容版本，不使用滚动 `latest` 标签。
- 按用户此前要求将阶段改动分成小提交，提交信息使用中文；只提交本项目跟踪文件，不推送，也不提交本机 `.env` 或 Agent 文件。

## Review Focus

- IDEA 与容器使用不同的 OAP 地址；配置必须分别使用宿主机回环地址和 Compose DNS。
- Agent 名称必须与对应模块名一致，避免 15 个进程合并成同一个服务。
- Spring MVC 6 与 Spring Cloud Gateway 的可选插件必须从官方 Agent 包中按实际文件名选择，不能把整个 `optional-plugins` 目录盲目复制。
- Horizon 管理密码不能进 Git；若 `.env` 已含同名 SkyWalking 配置，必须保留现值并避免覆盖。
- SkyWalking 支持 RocketMQ 5.x 不等于已证明 Spring Cloud Stream Binder 的跨消息上下文传播；本次只记录静态依据，不声称已完成运行时验收。

---

### Task 1：增加 SkyWalking Compose 后端与本机登录配置

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `.env`（仅本机忽略文件；只追加新变量，不读取或覆盖其他密钥）

**Interfaces:**
- Produces: Compose 服务 `skywalking-banyandb`、`skywalking-oap`、`skywalking-ui`，统一使用 `skywalking` profile。
- Produces: Horizon 环境变量 `HORIZON_AUTH_LOCAL_USERS`，其值来自 `SKYWALKING_HORIZON_LOCAL_USERS`。
- Produces: UI 登录入口 `http://127.0.0.1:18080`；OAP gRPC、查询和管理端口分别为 `11800`、`12800`、`17128`。

- [ ] 为 BanyanDB 添加固定 `0.11.2` 镜像、`standalone` 命令和独立命名卷；数据根目录 `/tmp` 挂载到该卷，不发布存储端口。
- [ ] 为 OAP 添加 `11.1.0-java21` 镜像、BanyanDB 存储目标、回环端口映射、等待 BanyanDB 启动的依赖，以及使用 Bash TCP 探测的健康检查。
- [ ] 为 Horizon UI 添加 `horizon-1.1.0` 镜像，设置 OAP 查询/管理地址、`127.0.0.1:18080:8081` 映射、OAP 健康依赖和本机状态卷。
- [ ] 在 `.env.example` 增加空数组格式的 `SKYWALKING_HORIZON_LOCAL_USERS=[]` 及生成本机账号的说明，不写入真实密码或哈希。
- [ ] 检查根 `.env` 是否已有 `SKYWALKING_HORIZON_ADMIN_PASSWORD` 或 `SKYWALKING_HORIZON_LOCAL_USERS` 键名；若不存在，生成随机本机密码及其 Argon2id 哈希并追加，禁止输出或改写现有行。
- [ ] 静态核对 Compose 中仅对 `skywalking` profile 启用观测组件、UI 与 OAP 端口均绑定回环地址、Horizon 的账号值来自 `.env`。
- [ ] 提交：`feat: 添加 SkyWalking 本地观测后端`

### Task 2：准备并校验 Java Agent 与可选插件

**Files:**
- Modify: `.gitignore`
- Create locally, ignored: `.local/skywalking-agent/` 下的 Agent 发行包和解压内容

**Interfaces:**
- Produces: Agent JAR 路径 `.local/skywalking-agent/agent/skywalking-agent.jar`。
- Produces: Gateway 容器挂载目录 `.local/skywalking-agent/agent/`。
- Produces: IDEA JVM 参数 `-javaagent:$PROJECT_DIR$/.local/skywalking-agent/agent/skywalking-agent.jar`。

- [ ] 在 `.gitignore` 忽略 `.local/skywalking-agent/`。
- [ ] 从 Apache 官方下载 `apache-skywalking-java-agent-9.8.0.tgz` 及 `.sha512` 到该忽略目录。
- [ ] 使用 Apache 提供的 SHA-512 文件核验发行包，核验失败时停止解压并记录问题。
- [ ] 检查归档路径后解压，使 Agent JAR 位于约定路径；不把归档或 JAR 添加到 Git。
- [ ] 检查 Agent 9.8.0 的 `optional-plugins` 实际文件名，只将 Spring MVC 6 与 Spring Cloud Gateway 4.x 所需插件复制到 `plugins`；Gateway 使用时不额外复制 WebFlux 6 插件。
- [ ] 静态核对 Agent JAR 和所选可选插件均在本机忽略目录，且 `git status` 不列出二进制文件。
- [ ] 提交：`chore: 忽略本机 SkyWalking Agent 文件`

### Task 3：接入 15 个 IDEA 运行配置与可选 Gateway 容器

**Files:**
- Create: `.run/SkyWalking - <service-name>.run.xml`（15 个）
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: Task 1 的 `skywalking-oap` 地址和 Task 2 的本地 Agent JAR/挂载目录。
- Produces: 每份 shared run configuration 的 VM 参数、`SW_AGENT_NAME` 和 `SW_AGENT_COLLECTOR_BACKEND_SERVICES`。
- Preserves: `.idea/workspace.xml` 中用户现有私有运行配置与环境变量。

- [ ] 为 15 个主类分别建立 IDEA shared run configuration；使用对应模块和主类，不复制 `.idea/workspace.xml` 中的私有环境变量。
- [ ] 每份配置添加 `-javaagent:$PROJECT_DIR$/.local/skywalking-agent/agent/skywalking-agent.jar`、准确的 `SW_AGENT_NAME` 和 `SW_AGENT_COLLECTOR_BACKEND_SERVICES=127.0.0.1:11800`。
- [ ] 为现有可选 Gateway Compose 服务只读挂载 Agent 目录，并设置容器内 Agent JAR 的 `JAVA_OPTS`、服务名 `gateway-service` 和 `skywalking-oap:11800` Collector 地址。
- [ ] 静态解析 15 个 XML，确认配置数、模块、主类、唯一 Agent 服务名和宿主机 Collector 地址一一对应；确认 Gateway 容器变量使用 Compose DNS。
- [ ] 提交：`feat: 为全部 Java 服务配置 SkyWalking Agent`

### Task 4：写本地使用说明并校正服务清单

**Files:**
- Create: `docs/SKYWALKING_TRACING.md`
- Modify: `README.md`
- Modify: `docs/DOCKER_DEV_ENV.md`
- Modify: `docs/API_DOCS.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/COUPON_SERVICE.md`
- Modify: `docs/PROJECT_PROGRESS.md`

**Interfaces:**
- Consumes: Tasks 1–3 的服务名、端口、镜像版本、Agent 路径和本机账号变量。
- Produces: Windows/IDEA、Compose Gateway、Horizon 登录、可选插件、故障排查和静态/运行验收说明。

- [ ] 编写 `docs/SKYWALKING_TRACING.md`，说明先准备 Agent 与本机账号、再启动 `skywalking` profile、再用 shared run configurations 启动 Java 服务的顺序。
- [ ] 文档列出 OAP/UI 地址、15 个服务映射、Agent 版本与 SHA-512 下载核验、可选插件、密码只在 `.env` 的约束，以及 IDEA 自有环境变量场景的处理方式。
- [ ] 在 `README.md` 技术选型表加入 SkyWalking，服务表增加推荐服务 `9112` 并将优惠券服务改为 `9113`。
- [ ] 在 `docs/DOCKER_DEV_ENV.md` 加入 SkyWalking 回环端口和 `skywalking` profile 使用说明，不改写现有基础设施账号。
- [ ] 将 `docs/API_DOCS.md`、`docs/ARCHITECTURE.md`、`docs/COUPON_SERVICE.md` 中优惠券端口改为 `9113`；架构文档同时补推荐服务 `9112`。
- [ ] 在 `docs/PROJECT_PROGRESS.md` 记录配置已加入但容器启动、全服务注册、HTTP/MQ 端到端 Trace 尚未运行验收。
- [ ] 提交：`docs: 补充 SkyWalking 接入与端口说明`

### Task 5：静态验收并记录限制

**Files:**
- Verify: `docker-compose.yml`、`.run/`、`.gitignore`、`.env.example` 和本计划涉及的 Markdown。
- Modify: `docs/superpowers/plans/2026-11-18-skywalking-full-service.md`（勾选完成项）

- [ ] 运行 `docker compose config --quiet`，只验证 Compose 文件解析，不启动或重启容器。
- [ ] 使用 PowerShell XML 解析确认 15 份 IDEA 配置结构有效、服务名唯一、模块名和主类对应。
- [ ] 使用 `git diff --check` 加未跟踪文件空白核对；确认 `.env` 与 Agent 发行包仍被忽略，未进入 Git 改动清单。
- [ ] 搜索 README 与相关服务文档，确认优惠券端口为 `9113`、推荐服务端口为 `9112`。
- [ ] 不运行 Maven、测试或 Java 服务；在进度记录中保留这些未验证项及 RocketMQ 上下文传播风险。
- [ ] 提交：`docs: 记录 SkyWalking 静态验收范围`
