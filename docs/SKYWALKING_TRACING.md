# SkyWalking 全服务链路追踪

本地开发使用 SkyWalking OAP 11.1.0、BanyanDB 0.11.2、Horizon UI 1.1.0 和 Java Agent 9.8.0。观测后端由 `docker-compose.yml` 的 `skywalking` profile 单独启停；Java 服务通过 `-javaagent` 接入，不增加业务依赖。

## 组件与端口

| 用途 | 本机地址 | 说明 |
|---|---|---|
| Agent gRPC 上报 | `127.0.0.1:11800` | IDEA 中运行的 Java 服务连接此端口 |
| OAP 查询 API | `127.0.0.1:12800` | Horizon 查询指标、拓扑和 Trace |
| OAP 管理 API | `127.0.0.1:17128` | Horizon 管理功能；无内置认证，只绑定本机回环地址 |
| Horizon UI | `http://127.0.0.1:18080` | 本机浏览器登录入口 |

BanyanDB 仅在 Compose 网络内供 OAP 使用，数据保存在独立 Docker 命名卷。Horizon 用户名为 `admin`；本工作区生成的随机密码只保存在被忽略的根目录 `.env` 中，变量名为 `SKYWALKING_HORIZON_ADMIN_PASSWORD`。实际登录校验使用同一文件中的 Argon2id 哈希 `SKYWALKING_HORIZON_LOCAL_USERS`。不要提交 `.env`、明文密码或哈希。

## 1. 准备 Java Agent

Agent 发行包和可选插件位于被 `.gitignore` 忽略的 `.local/skywalking-agent/`。新工作区需要从 Apache 官方地址下载发行包，并先核对 SHA-512，再解压：

```powershell
$dir = ".local\skywalking-agent"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$archive = Join-Path $dir "apache-skywalking-java-agent-9.8.0.tgz"
$checksum = "$archive.sha512"
Invoke-WebRequest "https://downloads.apache.org/skywalking/java-agent/9.8.0/apache-skywalking-java-agent-9.8.0.tgz" -OutFile $archive
Invoke-WebRequest "https://downloads.apache.org/skywalking/java-agent/9.8.0/apache-skywalking-java-agent-9.8.0.tgz.sha512" -OutFile $checksum
$expected = [regex]::Match((Get-Content $checksum -Raw), "[0-9a-fA-F]{128}").Value.ToUpperInvariant()
$actual = (Get-FileHash $archive -Algorithm SHA512).Hash
if (-not $expected -or $actual -ne $expected) { throw "SkyWalking Agent SHA-512 校验失败" }
tar.exe -xzf $archive -C $dir
Move-Item (Join-Path $dir "skywalking-agent") (Join-Path $dir "agent")
```

复制与项目依赖对应的两个可选插件：

```powershell
$optional = Join-Path $dir "agent\optional-plugins"
$plugins = Join-Path $dir "agent\plugins"
Copy-Item (Join-Path $optional "apm-springmvc-annotation-6.x-plugin-9.8.0.jar") $plugins
Copy-Item (Join-Path $optional "apm-spring-cloud-gateway-4.x-plugin-9.8.0.jar") $plugins
```

不要复制 `apm-spring-webflux-6.x-plugin`：Gateway 使用专用的 Spring Cloud Gateway 4.x 插件。若 Agent 路径不存在，IDEA 的 `-javaagent` 参数会使 Java 进程无法启动。

## 2. 启动观测后端

在项目根目录运行。WSL 中项目位于 Windows `E:` 盘时，先执行 `cd /mnt/e/order_system_cloud`；如果当前目录不在项目根目录，可通过 `-f /mnt/e/order_system_cloud/docker-compose.yml --env-file /mnt/e/order_system_cloud/.env` 指定 Compose 文件和环境文件：

```powershell
docker compose --profile skywalking up -d skywalking-banyandb skywalking-oap skywalking-ui
docker compose --profile skywalking ps
```

打开 `http://127.0.0.1:18080`，使用 `admin` 和本机 `.env` 中对应的随机密码登录。`.env.example` 中的 `SKYWALKING_HORIZON_LOCAL_USERS=[]` 仅是空模板，空数组不能登录。其他工作区需要设置自己的本地密码及对应 Argon2id 用户哈希；生成方式见 [Horizon 本地用户说明](https://skywalking.apache.org/docs/skywalking-horizon-ui/latest/access-control/local-backend/)。

## 3. 启动 Java 服务

在本机当前工作区，选择平时使用的原有 IDEA 配置启动服务。15 个原有配置已在本机自动加入 SkyWalking Agent、各自的服务名、Collector 地址，以及从根目录 `.env` 填入的 Nacos 用户名和密码；这些凭据保存在被忽略的 `.idea/workspace.xml` 中。服务端口以当前各服务配置为准：

| 服务 | 端口 | 本机 IDEA 配置名 | SkyWalking 服务名 |
|---|---:|---|---|
| Gateway | 9090 | `GatewayServiceApplication` | `gateway-service` |
| 订单 | 9091 | `OrderServiceApplication` | `order-service` |
| 用户 | 9092 | `UserServiceApplication` | `user-service` |
| 商品 | 9093 | `ProductServiceApplication` | `product-service` |
| 认证 | 9094 | `AuthServiceApplication` | `auth-service` |
| 支付 | 9101 | `PaymentServiceApplication` | `payment-service` |
| 文件 | 9102 | `FileServiceApplication` | `file-service` |
| 购物车 | 9103 | `CartServiceApplication` | `cart-service` |
| 库存 | 9104 | `StockServiceApplication` | `stock-service` |
| 评论 | 9105 | `CommentServiceApplication` | `comment-service` |
| 搜索 | 9106 | `SearchServiceApplication` | `search-service` |
| 通知 | 9107 | `NotificationServiceApplication` | `notification-service` |
| 统计 | 9111 | `StatisticsServiceApplication` | `statistics-service` |
| 推荐 | 9112 | `RecommendationServiceApplication` | `recommendation-service` |
| 优惠券 | 9113 | `CouponServiceApplication` | `coupon-service` |

这些配置仍启动原来的 15 个业务服务，并没有创建业务服务副本。名称带 `SkyWalking -` 的 `.run` 配置是可共享的备用启动项，不会继承本机 `.idea/workspace.xml` 中的变量。当前只有 `SkyWalking - gateway-service` 配置通过 IDEA 的环境变量文件功能读取 `$PROJECT_DIR$/.env`；其余共享配置未加载 Nacos 凭据，本机启动这些服务时应使用原有 IDEA 配置。

本机凭据和 Agent 参数保存在被 Git 忽略的 `.idea/workspace.xml` 中，不会提交密码。若删除或重新生成该 IDEA 本机配置，需恢复本机的 Nacos 凭据和上述 Agent 设置；不要把 `.env` 中的凭据复制到可提交的 `.run` 文件。每个业务服务只启动一个配置，不能同时启动原配置和对应的 `SkyWalking - ...` 配置，否则会争用同一端口。

### 使用 Compose Gateway

Compose Gateway 通过只读挂载加载同一份本机 Agent，并把 Collector 指向 Compose 网络中的 `skywalking-oap:11800`。启动前先准备 Agent 和 `.env`，再运行：

```powershell
docker compose --profile app --profile skywalking up -d --build gateway skywalking-banyandb skywalking-oap skywalking-ui
```

该命令会同时启动 Gateway 所需的 Nacos、Redis 和 SkyWalking 组件。不要同时在 IDEA 和 Compose 启动 Gateway，因为两者默认都使用宿主机 `9090`。

停止观测组件但保留数据卷：

```powershell
docker compose --profile skywalking stop skywalking-ui skywalking-oap skywalking-banyandb
```

## 4. 验收与限制

启动后在 Horizon 的 Services 页面查看服务注册，并通过一个实际请求检查 Gateway、Feign 下游服务的 Trace ID 是否一致。错误请求应能在 Trace 中定位对应 Span；需要通过运行观察确认，配置存在不等于链路已验收。

Agent 发行包包含 RocketMQ 5.x 插件，但本项目使用 Spring Cloud Stream RocketMQ Binder。当前尚未运行生产到消费的上下文传播验收，因此不能保证异步消息两端自动关联；若 Trace ID 在消息边界断开，需要针对该 Binder 的上下文传递补充适配后再验收。首期不启用日志采集、持续性能剖析或业务指标。

## 官方资料

- [Java Agent 安装与配置](https://skywalking.apache.org/docs/skywalking-java/v9.8.0/en/setup/service-agent/java-agent/readme/)
- [Java Agent 可选插件](https://skywalking.apache.org/docs/skywalking-java/v9.8.0/en/setup/service-agent/java-agent/optional-plugins/)
- [OAP 11.1 BanyanDB 存储配置](https://skywalking.apache.org/docs/main/v11.1.0/en/setup/backend/backend-storage/)
- [BanyanDB Docker 安装](https://skywalking.apache.org/docs/skywalking-banyandb/v0.11.2/installation/docker/)
- [Horizon 容器环境变量](https://skywalking.apache.org/docs/skywalking-horizon-ui/v1.1.0/setup/container-image/)
- [Horizon 本地用户和 Argon2id](https://skywalking.apache.org/docs/skywalking-horizon-ui/latest/access-control/local-backend/)
