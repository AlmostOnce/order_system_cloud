# Docker 本地开发环境

## 服务和端口

| 服务         | 宿主机地址              | 说明                                   |
|--------------|-------------------------|----------------------------------------|
| MySQL        | `127.0.0.1:9095`        | 业务数据库                             |
| Redis        | `127.0.0.1:9096`        | JWT 黑名单、限流                       |
| Nacos API    | `127.0.0.1:9097`        | IDEA 中运行的服务连接地址              |
| Nacos 控制台 | `http://127.0.0.1:9098` | 网页登录和配置管理                     |
| Gateway 容器 | `http://127.0.0.1:9090` | 仅启用 Compose 的 `app` profile 时使用 |
| SkyWalking Agent gRPC | `127.0.0.1:11800` | IDEA 中运行的 Java Agent 上报入口 |
| SkyWalking OAP 查询 / 管理 | `127.0.0.1:12800` / `127.0.0.1:17128` | Horizon 与本机调试使用 |
| Horizon UI | `http://127.0.0.1:18080` | 仅绑定本机回环地址，登录后查看 Trace |

## SkyWalking 本地链路追踪

SkyWalking 使用独立的 `skywalking` profile，不会随默认基础设施自动启动。Agent 文件保存在仓库忽略的 `.local/skywalking-agent/`；完整下载校验、插件选择、Horizon 本机登录和 15 个 IDEA 配置见[全服务链路追踪指南](SKYWALKING_TRACING.md)。

先准备 Agent 与根目录 `.env` 中的本机 Horizon 用户，再启动观测后端：

```powershell
docker compose --profile skywalking up -d skywalking-banyandb skywalking-oap skywalking-ui
```

现有 Compose Gateway 已加载本机 Agent，因此运行其 `app` profile 前必须先按指南准备 Agent；Gateway 使用 `skywalking-oap:11800`，本机 IDEA 服务使用 `127.0.0.1:11800`。

本机 IDEA 各服务默认端口见 README 的[可部署服务表](../README.md#可部署服务)。Gateway 的 IDEA 与 Compose 默认端口都是 `9090`，不要同时启动这两个进程。

## 第一次启动

在项目根目录执行：

```bash
cd /mnt/e/order_system_cloud
docker compose config
docker compose up -d mysql nacos
docker compose ps
docker compose logs -f nacos
```

看到 Nacos 启动成功日志后，按 `Ctrl+C` 退出日志查看，不会停止容器。

打开 Nacos：

```text
http://127.0.0.1:9098
```

使用用户 `nacos` 登录。如果页面要求首次初始化密码，就设置一个自己的密码。然后修改项目根目录 `.env`：

```dotenv
NACOS_USERNAME=nacos
NACOS_PASSWORD=这里填写刚设置的网页密码
```

## 启动 Gateway 容器

Gateway 容器现在通过本机 Agent 目录启动；执行前先完成上方 SkyWalking 准备步骤。OAP 未启动时 Gateway 仍可能运行，但 Agent 无法上报 Trace。

确认 `.env` 已保存新密码后执行：

```bash
cd /mnt/e/order_system_cloud
docker compose --profile app up -d --build gateway
docker compose ps
docker compose logs -f gateway
```

Gateway 容器地址：

```text
http://127.0.0.1:9090
```

当前没有业务路由，访问根路径返回 404 是正常的。重点检查 Nacos 服务列表中是否出现 `gateway-service`。

## 停止和启动

停止容器但保留数据：

```bash
docker compose --profile app stop
docker compose stop mysql nacos
```

如果希望恢复自动启动，执行：

```bash
docker compose up -d mysql nacos
docker compose --profile app up -d gateway
```

再次启动基础设施：

```bash
docker compose up -d mysql nacos
```

再次构建并启动 Gateway：

```bash
docker compose --profile app up -d --build gateway
```

停止并删除容器、网络但保留数据卷：

```bash
docker compose --profile app down
```

不要随便执行下面的命令，因为 `-v` 会删除本项目 MySQL 和 Nacos 数据：

```bash
docker compose down -v
```

## MySQL 连接信息

```text
Host: 127.0.0.1
Port: 9095
Database: order_db
Username: order_user
Password: order_password_dev_2026
Root password: order_root_dev_2026
```

## IDEA 中运行 Gateway

如果不想运行 Gateway 容器，也可以直接运行 `Main.java`。此时要在 IDEA 的运行配置中设置环境变量：

```text
NACOS_SERVER_ADDR=127.0.0.1:9097
NACOS_USERNAME=nacos
NACOS_PASSWORD=你的Nacos网页密码
```

此时 Gateway 使用本机端口 `9090`，Nacos 使用本项目的 `9097` API 端口。
