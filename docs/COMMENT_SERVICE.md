# 评论服务首期

## 范围与状态

2026-10-28 新增 comment-service（端口9105）和 comment-api 共享评分契约。首期提供已完成订单的菜品评价、本人/菜品分页、评价详情、评分汇总及商品侧异步评分投影。每笔订单的每道菜限评一次，评分1至5，正文1至1000字符，最多6张图片、5个最多20字符的标签。暂不含评论修改删除、商家回复、点赞、审核后台及前端入口。

初始实现阶段只修改源码、必要依赖声明和部署配置；Java测试源码已补，未构建或端到端联调。后续MongoDB部署结果见下方，MySQL迁移、Nacos路由发布和业务服务启停仍未执行，也未运行Maven。

## 接口

全部经过JWT认证，服务直连也验签、验证签发者和有效期、检查Redis注销黑名单，再解析UUID为内部用户ID。创建及本人列表仅CUSTOMER可用；不接收客户端指定的userId、windowId或作者姓名。

| 方法 | 网关路径 | 说明 |
|---|---|---|
| POST | /api/comments | 发布评价；必须携带稳定UUID格式的Idempotency-Key |
| GET | /api/comments/me?limit=20&cursor=... | 本人评价 |
| GET | /api/comments/products/{productId}?limit=20&cursor=... | 菜品评价 |
| GET | /api/comments/{commentId} | 评价详情 |
| GET | /api/comments/products/{productId}/rating | 评论服务权威评分汇总的短缓存 |
| GET | /api/products/{productId}/rating | 商品服务MQ异步评分投影 |

创建示例：

```json
{
  "orderId": 14,
  "productId": 3,
  "score": 5,
  "content": "分量合适，味道很好",
  "imageFileKeys": [],
  "tags": ["分量足"]
}
```

图片先使用现有文件上传接口，bizType=COMMENT_IMAGE，不指定bizRef；发布时提交fileKey，不接受任意图片URL。评论服务调用ManagedImageClient核验上传人、业务类型和文件格式后保存 imageFileKeys，查询时生成受控访问地址。

分页limit为1至50，cursor是上页返回的公开评论UUID；排序使用内部自增ID倒序，对外不暴露该ID。返回items、nextCursor、hasMore。公开详情不返回内部用户ID、订单ID或请求摘要。正文和标签是纯文本，未来界面必须按文本渲染，不按HTML解释。

## 授权、幂等和存储

发布通过Feign复用GET /orders/{orderId}，携带原始签名Bearer Token，由订单服务校验本人归属；评论服务检查订单ID、已完成状态、窗口及实际购买菜品。网络/身份/订单校验失败时拒绝创建，不使用当前商品目录伪造购买事实。

内容先以随机UUID写MongoDB comments集合，再通过独立事务组件提交MySQL comments、comment_rating_stats和comment_rating_outbox。数据库的(order_id,product_id)唯一键兜底并发重复评价，(user_id,request_key)唯一键兜底请求重试。同请求键同归一化内容返回原结果，不同内容返回409。

查询以MySQL已提交元数据为可见性依据，内容缺失返回明确失败。提交后Mongo committed标记失败不回滚已提交评价，由任务补标；SQL失败不立即删除文档，避免提交结果不确定时误删。对账每小时扫描最多100条、创建超过24小时且未确认的文档，数据库明确不存在时才清理；数据库异常保留文档。MySQL事务限时15秒，Mongo设置连接、读和选服超时。跨库没有分布式事务，长期暂停中的写请求、数据库恢复等异常场景仍需人工核对，不能把宽限期当成分布式事务保证。

评分汇总Redis缓存30至60秒，包含无评价结果；Redis失败回源，数据库失败不伪造零评分。零评价的averageScore为null。首期尚未推广完整布隆过滤器、热点互斥与Sentinel防护，已登记为后续增强；不得宣称这些缓存防护已完成。

## MQ与评分投影

MySQL汇总行串行生成评分版本，outbox与评价同事务。评论服务同步发布product-rating-changed-v1主题，Broker确认后才标记发送成功；失败指数退避，最大约256秒。事件只含菜品ID、版本、总条数、总分等快照，不含用户信息和正文。

商品服务启用comment-mq profile后，由product-rating-v1消费组接收事件，并在本库product_rating_stats中按revision原子覆盖。重复或旧版本不累加、不覆盖新值；消费者不吞异常，配置Broker最多重试16次，死信队列为%DLQ%product-rating-v1。MQ故障不会阻塞评论的MySQL提交，outbox留待重发。

每5分钟重置各菜品最新版本已发送事件，重新投递全量评分快照，用于修复商品投影被清空、消息遗漏等情况。这会产生可预期的重复消息，依赖版本幂等处理。首期只新增评价，未来审核、删除等逆向变更必须同步修改权威汇总、递增版本并写outbox，不能仅改status。

## 部署准备

1. 执行docs/database/migrations/comment_db/V1__create_comments.sql与product_db/V8__add_product_rating_projection.sql。给独立comment_service MySQL账号授予comment_db的SELECT/INSERT/UPDATE权限；产品服务账号应具有product_rating_stats的SELECT/INSERT/UPDATE权限。SQL已提供中文表字段注释，没有跨服务外键或跨库业务查询。
2. 在本机.env填写COMMENT_DB_PASSWORD、MONGO_ROOT_PASSWORD、COMMENT_MONGO_PASSWORD等独立强密码，不提交.env。按用户要求，评论服务YAML提供本机开发的MySQL/MongoDB账号密码及既有Redis、Nacos和内部凭据默认值；IDE未设置环境变量时使用这些默认值，显式环境变量仍可覆盖。Compose的.env不会自动传给IDE。
3. Compose新增comment profile下的mongo:7.0，绑定127.0.0.1:27017，独立数据卷；root仅初始化，应用使用comment_service用户，只授予comment_db的readWrite。docker/mongo/01-comment-init.js仅在新数据卷执行，旧卷需单独迁移。不要删除数据卷来重跑初始化。
4. MQ初始化任务新增product-rating-changed-v1主题，4个读写队列。现有MQ容器不会因配置文件修改自动创建新主题，需部署时执行初始化任务。
5. 评论服务默认comment-mq；商品服务显式激活comment-mq。两边使用同一ROCKETMQ_NAMESERVER。未启用消息配置时outbox仍入库，异步投影不更新。JWT、Redis、Nacos、内部共享密钥和文件服务地址沿用现有环境配置。
6. 发布docs/nacos/gateway-service.yaml中的评论路由后，再进行新服务注册、订单归属、图片归属、并发幂等、Mongo/SQL故障、消息重试及真实SQL乱序更新验收。

本期未引入额外事务框架或改动下单、库存、支付逻辑。两个新模块的依赖由现有Spring Boot 3.5.6及Spring Cloud BOM管理，不升级项目框架版本。


## 本次静态验证

已执行tools/check-comment-contracts.py，校验模块XML、身份来源、订单购买事实、跨库写入顺序、唯一约束、公开字段与消息绑定。使用JavaParser对28个新增Java文件做语法解析、SnakeYAML对6份配置做解析，均无语法错误；Mongo初始化脚本通过node --check；git diff --check通过。这些均为静态检查，不解析依赖是否可用、不进行Java类型检查，不代表JUnit、真实SQL、MongoDB或RocketMQ运行通过。

新增JUnit源码覆盖已完成订单与菜品事实、非完成状态拒绝、幂等重放、先内容后元数据、SQL失败保留正文、重复购买评价拒绝、字段验证、公开响应隐私、数据库异常时不清理Mongo内容。商品侧SQL重复/乱序、多实例并发、跨库故障和MQ重试/死信仍需实际基础设施验收。


## 2026-10-28 MongoDB部署结果

用户授权后已拉取mongo:7.0，创建并启动order-system-cloud-mongo-1及专用数据卷，映射127.0.0.1:27017。已修复Compose默认值插值语法，使用:-而非Spring配置的冒号默认值。

管理员comment_admin在admin库认证，应用comment_service在comment_db认证。真实认证验证通过；确认应用账号只有comment_db的readWrite权限，comments集合与pending_orphan_scan索引已建立，临时文档写入、读取及清理均成功。容器凭据保存于本机被Git忽略的.env；应用账号密码也已按用户要求作为本地开发默认值写入评论服务YAML，本文档不重复密码。

此步骤只完成MongoDB容器部署，不表示评论服务已接通：MySQL评论库与商品评分表迁移、MQ新主题、Nacos路由发布、IDE环境变量及Java依赖构建/服务联调仍待完成。

## 2026-10-28 MySQL 初始化与图片引用改造

已按用户授权创建 comment_db 及 comments、comment_rating_stats、comment_rating_outbox 三表；product_db.product_rating_stats 也已创建。comment_service 账号已验证连接，并仅授予 comment_db 的 SELECT/INSERT/UPDATE。商品服务现有账号的 product_db 权限包含新表。修改前的 product_db 备份保存在用户目录 .codex/backups/order_system_cloud/comment_database_20261028_095637。

MongoDB 内容模型源码已改为 imageFileKeys，公开响应 images 继续返回 URL。图片引用迁移方案见 database/migrations/IMAGE_FILE_KEYS.md。用户明确要求暂不停止服务或迁移图片字段，因此当前 Mongo 数据未改（只读预检为0条文档），现有运行进程仍是旧代码；后续必须停服迁移后再使用新源码。

### 2026-10-28 图片数据库迁移已执行

用户进一步授权执行第二步后，确认9092/9093/9105均停止监听，已完成 MySQL 四表图片 fileKey 迁移及 Mongo 评论图片迁移。MySQL逐行核对及迁移后只读预检通过，旧URL列已移除，已有头像引用保留。Mongo当前无评论文档。备份及详细结果见 docs/database/migrations/IMAGE_FILE_KEYS.md。此记录替代上文“图片迁移暂未执行”的旧状态；三个业务服务保持停止，未构建或启动。
