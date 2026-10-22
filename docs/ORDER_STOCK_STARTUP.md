# 订单、库存服务本机启动说明

适用场景：在新设备拉取代码，或新增库存服务后启动 order-service / stock-service。

## 1. 先启动完整基础环境

在项目根目录执行：

```bash
docker compose up -d mysql redis nacos seata
docker compose ps
```

首次运行会下载 Seata 镜像。`docker compose up -d` 返回只表示容器已启动，
不代表 Seata 已完成初始化；可通过以下命令观察：

```bash
docker compose logs --tail=80 seata
```

本机 Java 服务通过 `127.0.0.1:9109` 访问 Seata TC。
若日志中出现 `globalTransactionScanner` 创建失败，以及
`Connection refused: /127.0.0.1:9109`，先检查 Seata 容器与该端口，
不要通过关闭 `seata.enabled` 绕过下单跨服务事务。

## 2. 新设备首次执行数据库迁移

已有数据库先备份。下面两份脚本只创建缺失的库、账号或表，不清空已有业务记录。
注意账号密码：库存迁移脚本使用本地开发默认账号，若已在 IDEA 或 .env 中设置自定义
STOCK_DB_USERNAME / STOCK_DB_PASSWORD，必须先确认实际数据库账号与之匹配。

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot' < docs/database/migrations/stock_db/V1__create_stock_tables.sql
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot order_db' < docs/database/migrations/V4__add_undo_log.sql
```

执行后应存在：

- `stock_db.stock`、`stock_db.stock_flow`、`stock_db.undo_log`。
- `order_db.undo_log`，以及原有订单头、订单明细表。

Git 同步的是代码和迁移文件，不会把另一台设备的 Docker 容器、数据卷、数据库及账号同步过来。
不要因为进度文档记载“另一台设备已执行”就跳过当前设备的核对。

## 3. 启动 Java 服务

在 IDEA 重新加载 Maven 后运行 StockServiceApplication、OrderServiceApplication。
IDEA 不会自动读取项目 .env，仍需沿用各服务正确的 Nacos、Redis、数据库及公钥启动配置。
也可以先在根目录安装依赖，再分别在两个终端运行：

```bash
mvn -pl order-service,stock-service -am -DskipTests install
mvn -pl stock-service spring-boot:run
```

另一个终端：

```bash
mvn -pl order-service spring-boot:run
```

环境变量有自定义值时，需在各自终端或 IDEA 中设置，不要把密码提交到 Git。

验证入口：

```bash
curl -fsS http://127.0.0.1:9104/actuator/health
curl -fsS http://127.0.0.1:9091/actuator/health
```

应返回 `status: UP`，并在 Nacos 服务列表看到 order-service 与 stock-service。
启动成功不等于下单链路已完整验证；当前库存表若为空，需要按实际商品初始化库存后才能正常扣减。
