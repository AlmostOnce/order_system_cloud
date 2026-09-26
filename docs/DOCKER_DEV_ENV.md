# Docker 本地开发环境

## 服务和端口

| 服务 | 宿主机地址 | 说明 |
|---|---|---|
| MySQL | `127.0.0.1:3307` | 后续订单业务数据库 |
| Nacos 控制台 | `http://127.0.0.1:8082` | 网页登录和配置管理 |
| Nacos API | `127.0.0.1:8850` | IDEA 中运行的 Gateway 连接地址 |
| Gateway 容器 | `http://127.0.0.1:8083` | Docker 中运行的 Gateway |

本项目的端口与旧项目使用的 `3306`、`6379`、`8081`、`8848` 不同，因此不会占用旧项目端口。三个服务使用 `unless-stopped` 策略，系统或 Docker 重启后会自动启动；手动停止后不会自动拉起。

## 第一次启动

在项目根目录执行：

```bash
cd /home/user/GitProject/order_system_cloud
docker compose config
docker compose up -d mysql nacos
docker compose ps
docker compose logs -f nacos
```

看到 Nacos 启动成功日志后，按 `Ctrl+C` 退出日志查看，不会停止容器。

打开 Nacos：

```text
http://127.0.0.1:8082
```

使用用户 `nacos` 登录。如果页面要求首次初始化密码，就设置一个自己的密码。然后修改项目根目录 `.env`：

```dotenv
NACOS_USERNAME=nacos
NACOS_PASSWORD=这里填写刚设置的网页密码
```

## 启动 Gateway 容器

确认 `.env` 已保存新密码后执行：

```bash
cd /home/user/GitProject/order_system_cloud
docker compose --profile app up -d --build gateway
docker compose ps
docker compose logs -f gateway
```

Gateway 容器地址：

```text
http://127.0.0.1:8083
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
Port: 3307
Database: order_db
Username: order_user
Password: order_password_dev_2026
Root password: order_root_dev_2026
```

## IDEA 中运行 Gateway

如果不想运行 Gateway 容器，也可以直接运行 `Main.java`。此时要在 IDEA 的运行配置中设置环境变量：

```text
NACOS_SERVER_ADDR=127.0.0.1:8850
NACOS_USERNAME=nacos
NACOS_PASSWORD=你的Nacos网页密码
```

此时 Gateway 使用本机端口 `8080`，Nacos 使用本项目的 `8850` API 端口。
