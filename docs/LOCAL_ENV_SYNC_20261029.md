# 2026-10-29 Linux 本机拉取后环境同步

## 依据与边界

- 本次拉取范围：`02c258c..ce09c15`，当前代码位于 `main`。另一设备的执行记录不等于本机已迁移。
- 依据：`PROJECT_PROGRESS.md`、评论/搜索/通知服务文档、图片迁移说明，以及本次新增 SQL、Compose 和 Nacos 路由。
- 本轮只同步基础设施、数据库和开发配置；不执行 Maven、Java 单元测试、业务造单、扣款或 Java 服务启动，不提交或推送。
- 不删除 Docker 数据卷，不清库，不重置已有账号密码；通知事件与取消通知 TCC 的功能开关维持源码默认关闭状态。

## 备份

- 同步前六个业务库完整备份：`/home/user/.codex/backups/order_system_cloud/sync-20261029-xKw2V7/business-before.sql.gz`；gzip 校验通过，含原 16 张表。
- 原 `.env`：同目录 `environment-before.env`；原 Nacos 网关路由：`gateway-service-before.yaml`。
- 目录权限 700；凭据与数据库备份权限 600。图片迁移另有逐行引用清单和用户/商品库备份，由迁移工具打印实际路径。
- 本机 `.env` 仍被 Git 忽略。没有把账号密码、邮箱授权码或备份内容写入本文。

## 已完成

1. 创建 `comment_db` 四张表（comments、comment_rating_stats、comment_rating_outbox、window_reviews）及 `notification_db.notifications`；新增应用账号并验证仅有对应库 SELECT/INSERT/UPDATE 权限。
2. 执行商品 V8/V9、缺失字段检查后的 V10，补齐评分投影、搜索 outbox 和 product_id；执行订单 V2/V3，补齐通知 outbox/TCC；执行库存 V2，补齐事件 outbox 和消费去重表。共 **8 个业务库、27 张表**，新增迁移 92 个基础字段及 product_id 均与数据库逐项核对通过。
3. 按图片工具执行用户头像、食堂、窗口、菜品 URL→fileKey 迁移；迁移前端口 9092/9093/9105 无业务监听。逐行核对及迁移后只读预检通过，旧 URL 列已移除，新列均为 CHAR(32)。本机原图片引用均为空，不伪造另一设备的头像记录。
4. 原数据数量保持：19 用户、12 条范围授权、3 食堂、9 窗口、21 分类、77 商品、77 库存。未生成订单、支付、库存流水或评论测试数据。
5. Nacos 的 `gateway-service.yaml` 由旧 6 条路由更新为仓库完整 21 条路由；同名路由无差异，没有丢失自定义路由。发布后回读与仓库文件逐字一致；未覆盖 Sentinel 规则。
6. 执行 MQ topic 初始化任务，退出码 0。5 个主题均存在：order-paid-sales-v1、product-rating-changed-v1、stock-changed-v1、product-search-refresh-v1、order-status-notification-v1；均为 4 个读/写队列。未进行消息业务链路验收。
7. Nginx 1.27-alpine 已下载并启动，宿主机 8088 映射容器 8080；`nginx -t` 通过。Linux 通过 host.docker.internal/host-gateway 访问宿主机 Gateway 9090。空 Web 挂载目录已归当前用户所有，后续编译产物可写入。
8. `.env` 补齐评论 MySQL/Mongo、通知 MySQL、Nginx 和邮箱配置项；生成独立 Mongo 管理员密码及邮箱摘要密钥。保留源码已有本机应用密码默认值以兼容 IDEA，其他已有凭据不变。默认 COMPOSE_PROFILES=mq,comment,search,edge，使后续 `docker compose up -d` 启用本轮基础设施。
   新增被 Git 忽略的 `docker-compose.override.yml`，使用 Compose v5.6 支持的 `!override` 将无鉴权的 Elasticsearch 9200 只绑定到 127.0.0.1；Mongo 27017 沿用仓库已有本机绑定，不更改其他设备的端口。
9. 三个原仅支持 Windows/WSL 的迁移工具增加 Linux Docker 分支及私有备份权限；图片静态审计同步已改名的 CommentContentDO。Python 语法、Druid/图片静态契约、Compose 配置和 Git 空白检查通过，不代表 Java 构建或端到端运行已通过。

## 最终核验及待配置项

- MongoDB 7.0 已完成下载和启动，应用认证、仅 comment_db 的 readWrite 角色、comments 集合及 pending_orphan_scan 索引核验通过；没有写入或清理业务测试文档。
- Mongo 图片字段预检、迁移及执行后预检通过，本机评论文档为 0 条；BSON Extended JSON 备份：`/home/user/.codex/backups/order_system_cloud/comment_images_20261029_235601.ejson`。
- Elasticsearch 8.19.4：官方镜像已下载并启动，容器为 healthy；根接口版本核对为 8.19.4，`/_cluster/health` 为 green、1 个节点、0 个主分片。端口仅绑定 127.0.0.1:9200。当前未启动搜索 Java 服务，因此没有业务索引或已完成菜品同步的结论。镜像压缩分层总下载约 687.6 MiB，Docker 报告的未压缩镜像大小为 2,075,766,088 字节。
- 用户端仓库仍停留在本机旧提交，且 `unpackage/dist/build/nginx-web` 没有发布产物；Nginx 容器运行不等于首页已经可访问。需同步相应用户端源码后用官方 uni-app x 编译流程生成该目录。本轮未自动拉取前端或用示例页面冒充发布文件。
- `.env` 的 MAIL_USERNAME/MAIL_PASSWORD 为空：需填写真实 QQ 邮箱和 SMTP 授权码。未发送邮件或宣称邮箱换绑实际收件已验证。
- Compose 的 `.env` 不会自动注入 IDEA。本轮已创建被 Git 忽略、权限 600 的 `user-service/email-local.properties`，写入与 `.env` 一致的摘要密钥；用户服务已有 `spring.config.import`，从根目录或模块目录运行时会自动读取。真实邮箱与授权码填写该文件的 MAIL_USERNAME/MAIL_PASSWORD，不能提交；Gateway 仍需现有 NACOS_PASSWORD。其他运行配置按 `.env` 的实际值覆盖源码默认值，不把整个环境文件复制进 Git。
- Java 服务保持由 IDEA 启动。本轮新增模块、Druid 依赖、ES 自动建索引、真实 MQ 消费、Seata TCC 回调及 HTTP/WebSocket 业务链路未启动验收。
- 启动商品服务时，在 IDEA 的“有效配置文件”中填写 `comment-mq`，默认包含的 `search-mq` 仍生效；该 profile 设置 `comment.messaging.enabled=true`，满足 `ProductRatingMessaging` 的条件装配并加载评分消息绑定。评论服务默认 comment-mq，库存默认 stock-mq，搜索/通知自动 include 各自 MQ 配置。不要在共享 `.env` 全局设置 SPRING_PROFILES_ACTIVE，以免其他服务加载不属于自己的 profile。
- 新镜像部署后根文件系统约使用 87%，剩余约 11 GB；ES 日志提示 85% 低磁盘水位告警。当前集群 green，但后续数据增长需关注分片分配与磁盘水位；本轮不擅自删除其他镜像、缓存或数据卷，不关闭 ES 磁盘保护。

基础设施最终状态：MySQL、Nacos、Redis、Seata、RocketMQ NameServer/Broker、MongoDB、Elasticsearch、Nginx 共 9 个容器运行；MySQL/Redis/ES 的 Compose 健康检查通过，MQ topic 初始化任务正常退出。Mongo 通过真实认证、权限、集合和索引读取核验，Nacos 控制台 HTTP 200，Nginx 配置检查通过。镜像下载和容器启动任务均已结束，不留后台下载任务。

## 后续启动

```bash
cd /home/user/GitProject/order_system_cloud
docker compose up -d
docker compose ps -a
```

如需回退，先停受影响的业务服务，依据备份和实际迁移阶段恢复；不要直接覆盖迁移后新产生的数据。不得通过删除数据卷来重新运行初始化。
