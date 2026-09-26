# Docker 本地开发环境设计

## 目标

为 `order_system_cloud` 建立可重复启动的本地 Docker 开发环境，包含 MySQL、Nacos 和 Gateway，并且不影响机器上另一个项目已经存在的容器。

## 方案

- 使用 Docker Compose 管理本项目的容器和网络。
- MySQL 使用 `mysql:8.0`，作为后续订单业务数据库，使用命名卷持久化。
- Nacos 使用官方 `nacos/nacos-server:v3.0.3`，当前使用单机模式和内置 Derby 存储，使用命名卷持久化。
- Gateway 使用项目内的 Java 21 多阶段 Dockerfile 构建。
- Gateway 放在 Compose 的 `app` profile 中，先启动基础设施，再单独启动 Gateway。
- 所有新服务设置 `restart: "no"`，避免 Docker 或系统重启后自动启动。
- 使用非默认宿主机端口，避开旧项目容器：MySQL `3307`、Nacos 控制台 `8082`、Nacos API `8850`、Gateway `8083`。
- Nacos 用户密码不写入 Git，使用被 `.gitignore` 忽略的本地 `.env` 文件传入 Gateway。

## 运行顺序

1. 复制 `.env.example` 为 `.env`。
2. 执行 `docker compose up -d mysql nacos`。
3. 打开 `http://127.0.0.1:8082`，初始化或修改 `nacos` 用户密码。
4. 将同一个密码写入 `.env` 的 `NACOS_PASSWORD`。
5. 执行 `docker compose --profile app up -d --build gateway`。
6. 在 Nacos 服务列表中检查 `gateway-service`。

## 数据安全边界

- 不删除旧项目容器，不复用旧项目容器的数据。
- `docker compose down` 不删除命名卷；只有显式增加 `-v` 才删除本项目数据卷。
- 当前 Nacos 使用 Derby 是为了学习阶段降低复杂度；后续正式部署再切换为 MySQL 数据源。
