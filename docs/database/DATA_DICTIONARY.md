# 数据库中文数据字典

覆盖当前六个业务数据库；金额单位为元，数量单位为份。

## user_db.users：用户账户表，保存登录身份、角色及账户余额

| 字段 | 类型 | 中文说明 |
|---|---|---|
| user_id | bigint | 用户内部自增主键，仅用于数据库及服务间关联 |
| user_uuid | char(36) | 对外稳定随机用户标识，UUID第四版，不作为登录密码 |
| username | varchar(64) | 用户展示昵称，不再作为登录账号 |
| role_code | varchar(32) | 角色编码：CUSTOMER顾客、STALL_MANAGER店主、RESTAURANT_MANAGER食堂经理、GENERAL_MANAGER总经理、SYSTEM_ADMIN系统管理员 |
| balance | decimal(12,2) | 账户可用余额，单位元 |
| password_hash | varchar(255) | 登录密码的BCrypt哈希，禁止保存明文 |
| pay_password_hash | varchar(100) | 独立支付密码的BCrypt哈希，空值表示尚未设置 |
| avatar_file_key | char(32) | 用户头像文件键，空值使用默认头像 |
| status | tinyint | 账号状态：1启用，0停用 |
| version | int | 乐观锁版本号，用于并发更新校验 |
| created_at | datetime(3) | 记录创建时间 |
| updated_at | datetime(3) | 记录最后更新时间 |
| deleted_at | datetime(3) | 逻辑删除时间，空值表示未删除 |
| email | varchar(254) | 登录邮箱，去除首尾空白并转小写，历史账号可为空 |
| user_no | char(10) | 系统生成的十位永久用户号，可用于登录 |

## user_db.user_data_scopes：用户数据范围授权表，限定管理人员可操作的窗口、食堂或全平台范围

| 字段 | 类型 | 中文说明 |
|---|---|---|
| scope_id | bigint unsigned | 数据范围授权记录主键 |
| user_id | bigint | 关联用户内部主键，引用用户服务的用户记录 |
| scope_type | varchar(20) | 授权范围类型：WINDOW窗口、RESTAURANT食堂、PLATFORM全平台 |
| scope_ref | bigint | 授权目标内部主键：窗口编号或食堂编号，全平台范围固定为0 |
| created_at | datetime | 记录创建时间 |
| updated_at | datetime | 记录最后更新时间 |

## user_db.balance_records：用户余额变动流水表，用于充值、消费和退款对账

| 字段 | 类型 | 中文说明 |
|---|---|---|
| record_id | bigint unsigned | 余额流水主键 |
| user_id | bigint | 关联用户内部主键，引用用户服务的用户记录 |
| change_type | varchar(20) | 余额变动类型：RECHARGE充值、PAY消费、REFUND退款 |
| amount | decimal(10,2) | 本次变动金额，单位元，收支方向由变动类型确定 |
| balance_after | decimal(10,2) | 本次变动完成后的账户余额，单位元 |
| biz_type | varchar(20) | 关联业务类型，与业务单据引用和变动类型共同保证记账幂等 |
| biz_ref | varchar(64) | 关联业务单据引用或幂等键，同一用户同类业务动作不可重复记账 |
| remark | varchar(255) | 业务备注 |
| created_at | datetime | 记录创建时间 |

## product_db.restaurants：食堂资料表，维护食堂展示信息和启用状态

| 字段 | 类型 | 中文说明 |
|---|---|---|
| restaurant_id | bigint | 食堂内部自增主键 |
| restaurant_code | varchar(32) | 食堂唯一业务编码 |
| restaurant_name | varchar(64) | 食堂名称 |
| location | varchar(64) | 食堂所在区域或位置 |
| phone | varchar(20) | 食堂对外联系电话，不用于用户注册或登录 |
| image_file_key | char(32) | 展示图片文件键，空值使用默认图片 |
| notice | varchar(255) | 食堂公告内容 |
| sort_order | int | 展示排序，数值越小越靠前 |
| status | tinyint unsigned | 食堂状态：1启用，0停用 |
| created_at | datetime | 记录创建时间 |
| updated_at | datetime | 记录最后更新时间 |

## product_db.dining_windows：食堂营业窗口表，保存窗口归属、营业状态和展示资料

| 字段 | 类型 | 中文说明 |
|---|---|---|
| window_id | bigint | 窗口内部自增主键 |
| restaurant_id | bigint | 所属食堂内部主键，关联食堂记录 |
| window_code | varchar(32) | 窗口唯一业务编码 |
| window_name | varchar(64) | 窗口名称 |
| description | varchar(500) | 窗口简介，在点单页图片下方横向滚动展示 |
| location | varchar(255) | 窗口所在楼层及具体方位 |
| image_file_key | char(32) | 展示图片文件键，空值使用默认图片 |
| status | tinyint unsigned | 窗口状态：1启用，0停用 |
| is_open | tinyint | 营业标记：1营业中，0暂停营业 |
| sort_order | int | 展示排序，数值越小越靠前 |
| created_at | datetime | 记录创建时间 |
| updated_at | datetime | 记录最后更新时间 |

## product_db.product_categories：窗口菜品分类表，分类归属于单个窗口

| 字段 | 类型 | 中文说明 |
|---|---|---|
| category_id | bigint | 菜品分类内部自增主键 |
| window_id | bigint | 所属窗口内部主键，关联商品服务窗口记录 |
| category_name | varchar(64) | 窗口内的菜品分类名称 |
| sort_order | int | 展示排序，数值越小越靠前 |
| status | tinyint unsigned | 分类状态：1启用，0停用 |
| created_at | datetime | 记录创建时间 |
| updated_at | datetime | 记录最后更新时间 |

## product_db.products：窗口菜品目录表，保存当前售价、分类和上下架状态

| 字段 | 类型 | 中文说明 |
|---|---|---|
| product_id | bigint | 菜品内部自增主键 |
| window_id | bigint | 所属窗口内部主键，关联商品服务窗口记录 |
| category_id | bigint | 所属窗口内的分类主键，空值表示未分类 |
| product_code | varchar(64) | 窗口内唯一的菜品业务编码 |
| product_name | varchar(128) | 菜品名称 |
| description | varchar(500) | 内容简介或说明 |
| image_file_key | char(32) | 展示图片文件键，空值使用默认图片 |
| price | decimal(10,2) | 当前菜品单价，单位元 |
| sale_status | tinyint unsigned | 菜品售卖状态：1上架，0下架 |
| sort_order | int | 展示排序，数值越小越靠前 |
| created_at | datetime | 记录创建时间 |
| updated_at | datetime | 记录最后更新时间 |

## order_db.orders：顾客订单主表，记录订单金额、状态及提交幂等信息

| 字段 | 类型 | 中文说明 |
|---|---|---|
| order_id | bigint unsigned | 订单内部自增主键 |
| order_no | varchar(32) | 对外唯一订单号 |
| user_id | bigint unsigned | 关联用户内部主键，引用用户服务的用户记录 |
| window_id | bigint unsigned | 所属窗口内部主键，关联商品服务窗口记录 |
| total_amount | decimal(12,2) | 服务端核算的订单总金额，单位元 |
| status | varchar(32) | 订单状态：待支付、已支付、制作中、待取货、已完成、已取消 |
| remark | varchar(255) | 顾客下单备注 |
| pickup_code | varchar(16) | 订单取餐码 |
| version | int unsigned | 乐观锁版本号，用于并发更新校验 |
| created_at | datetime(3) | 记录创建时间 |
| updated_at | datetime(3) | 记录最后更新时间 |
| idempotency_key | varchar(128) | 顾客提交的下单幂等键，在用户维度唯一 |
| request_hash | char(64) | 规范化下单请求的摘要，用于拒绝同一幂等键提交不同内容 |

## order_db.order_items：订单菜品明细快照表，保存下单时的菜品名称、单价和数量

| 字段 | 类型 | 中文说明 |
|---|---|---|
| item_id | bigint | 订单明细内部自增主键 |
| order_id | bigint unsigned | 所属订单主键，关联本库订单表 |
| product_id | bigint | 关联菜品内部主键，引用商品服务菜品记录 |
| product_code_snapshot | varchar(64) | 下单时的菜品业务编码快照 |
| product_name_snapshot | varchar(128) | 下单时的菜品名称快照 |
| unit_price_snapshot | decimal(10,2) | 下单时的菜品单价快照，单位元 |
| quantity | int unsigned | 本次购买的菜品份数 |
| line_amount | decimal(12,2) | 该明细的单价乘数量金额，单位元 |
| created_at | datetime | 记录创建时间 |

## order_db.sales_event_outbox：订单销量事件本地消息表，保障支付销量事件可靠投递

| 字段 | 类型 | 中文说明 |
|---|---|---|
| event_id | varchar(80) | 销量事件唯一标识，用于消息消费幂等 |
| order_id | bigint unsigned | 已支付订单主键，每个订单仅生成一条销量事件 |
| payload | json | 待投递的销量事件JSON载荷，包含订单及菜品销量快照 |
| attempts | bigint | 事件投递失败后的累计重试次数 |
| next_attempt_at | datetime | 下一次允许尝试投递的时间 |
| published_at | datetime | 事件成功投递时间，空值表示尚未成功投递 |
| last_error | varchar(200) | 最近一次投递失败的错误摘要 |
| created_at | datetime | 记录创建时间 |

## order_db.undo_log：Seata分布式事务AT模式回滚日志表

| 字段 | 类型 | 中文说明 |
|---|---|---|
| branch_id | bigint | Seata分支事务标识 |
| xid | varchar(128) | Seata全局事务标识 |
| context | varchar(128) | 回滚日志序列化方式及扩展上下文 |
| rollback_info | longblob | 分支事务回滚所需的二进制数据 |
| log_status | int | 回滚日志状态：0正常，1防御状态 |
| log_created | datetime(6) | 回滚日志创建时间，精确到微秒 |
| log_modified | datetime(6) | 回滚日志最后修改时间，精确到微秒 |

## stock_db.undo_log：Seata分布式事务AT模式回滚日志表

| 字段 | 类型 | 中文说明 |
|---|---|---|
| branch_id | bigint | Seata分支事务标识 |
| xid | varchar(128) | Seata全局事务标识 |
| context | varchar(128) | 回滚日志序列化方式及扩展上下文 |
| rollback_info | longblob | 分支事务回滚所需的二进制数据 |
| log_status | int | 回滚日志状态：0正常，1防御状态 |
| log_created | datetime(6) | 回滚日志创建时间，精确到微秒 |
| log_modified | datetime(6) | 回滚日志最后修改时间，精确到微秒 |

## payment_db.payments：订单支付单表，记录支付渠道、处理状态及到账结果

| 字段 | 类型 | 中文说明 |
|---|---|---|
| payment_id | bigint unsigned | 支付单内部自增主键 |
| payment_no | varchar(32) | 对外唯一支付单号 |
| order_id | bigint | 关联订单内部主键，跨服务逻辑关联 |
| order_no | varchar(32) | 创建支付单时的订单号快照 |
| user_id | bigint | 付款用户内部主键，用于支付归属校验与对账 |
| channel_code | varchar(32) | 支付渠道编码，BALANCE表示账户余额支付 |
| amount | decimal(10,2) | 创建支付单时的应付金额快照，单位元 |
| paid_amount | decimal(10,2) | 支付成功后确认的实际到账金额，单位元 |
| channel_trade_no | varchar(64) | 支付渠道返回的交易流水号 |
| status | varchar(16) | 支付单状态：CREATED已创建、SUCCESS成功、FAILED失败、CLOSED关闭 |
| fail_reason | varchar(255) | 支付失败原因或异常备注 |
| notify_count | int | 累计收到的支付回调次数 |
| last_notify_at | datetime | 最近一次收到支付回调的时间 |
| success_time | datetime | 支付成功确认时间 |
| active_flag | tinyint | 进行中支付单标记，用于约束同一订单至多一个已创建支付单 |
| created_at | datetime | 记录创建时间 |
| updated_at | datetime | 记录最后更新时间 |

## stock_db.stock：窗口菜品库存主表，数据库数量为库存事实来源

| 字段 | 类型 | 中文说明 |
|---|---|---|
| stock_id | bigint unsigned | 库存记录内部自增主键 |
| window_id | bigint unsigned | 所属窗口内部主键，关联商品服务窗口记录 |
| product_id | bigint unsigned | 关联菜品内部主键，引用商品服务菜品记录 |
| total_qty | int | 库存初始或调整后的总量基线，用于库存对账 |
| available_qty | int | 当前可售库存份数，以数据库记录为准 |
| version | int | 乐观锁版本号，用于并发更新校验 |
| is_deleted | tinyint | 逻辑删除标记：0正常，1已删除 |
| created_at | datetime | 记录创建时间 |
| updated_at | datetime | 记录最后更新时间 |

## stock_db.stock_flow：窗口菜品库存变动流水表，记录扣减、回补及调整并保证业务幂等

| 字段 | 类型 | 中文说明 |
|---|---|---|
| flow_id | bigint unsigned | 库存变动流水内部自增主键 |
| biz_type | varchar(32) | 库存业务类型：ORDER_DEDUCT下单扣减、ORDER_RESTORE取消回补、ADMIN_ADJUST管理调整 |
| biz_ref | varchar(64) | 关联业务单据引用，与业务类型及库存记录共同保证幂等 |
| stock_id | bigint unsigned | 关联库存主表记录主键 |
| window_id | bigint unsigned | 所属窗口内部主键，关联商品服务窗口记录 |
| product_id | bigint unsigned | 关联菜品内部主键，引用商品服务菜品记录 |
| change_qty | int | 库存变动份数，扣减为负数，回补为正数 |
| before_qty | int | 本次变动之前的可售库存份数 |
| after_qty | int | 本次变动之后的可售库存份数 |
| operator_id | bigint unsigned | 操作用户内部主键，系统自动触发时可为空 |
| remark | varchar(255) | 业务备注 |
| created_at | datetime | 记录创建时间 |

## file_db.file_objects：上传文件对象元数据表，记录存储位置、归属及文件校验信息

| 字段 | 类型 | 中文说明 |
|---|---|---|
| file_id | bigint unsigned | 文件记录内部自增主键 |
| file_key | char(32) | 对外唯一文件键，为去除连接符的UUID，不暴露内部主键 |
| biz_type | varchar(32) | 文件业务类型：AVATAR头像、WINDOW_IMAGE窗口图、PRODUCT_IMAGE菜品图、RESTAURANT_IMAGE食堂图、COMMENT_IMAGE评价图、OTHER其他 |
| biz_ref | bigint unsigned | 文件关联业务记录的内部主键，尚未绑定时可为空 |
| storage_type | varchar(16) | 文件存储介质：LOCAL本地、MINIO或S3对象存储 |
| object_key | varchar(512) | 存储介质中的相对路径，由服务端生成 |
| original_name | varchar(255) | 上传时的原始文件名，仅作展示，不参与存储路径拼接 |
| file_ext | varchar(16) | 规范化的小写文件扩展名，须通过白名单校验 |
| content_type | varchar(128) | 通过文件内容校验确定的媒体类型 |
| file_size | bigint unsigned | 文件大小，单位字节 |
| sha256 | char(64) | 文件内容的SHA256摘要，用于去重及完整性校验 |
| uploader_id | bigint unsigned | 上传用户的内部主键，关联用户服务记录 |
| is_deleted | tinyint | 逻辑删除标记：0正常，1已删除 |
| created_at | datetime | 记录创建时间 |
| updated_at | datetime | 记录最后更新时间 |

## 待部署的图片引用变更（2026-10-28）

上方图片 URL 列仍对应现有运行数据库。源码已改为 users.avatar_file_key 及 restaurants/dining_windows/products.image_file_key，列定义为 CHAR(32) ASCII、可为空；评论 Mongo 文档改为 imageFileKeys。用户要求暂不停止服务或执行图片迁移，故不能把新源码直接连接旧图片列。迁移工具及执行顺序见 [图片文件键迁移](migrations/IMAGE_FILE_KEYS.md)。

同日已创建 comment_db.comments、comment_rating_stats、comment_rating_outbox 及 product_db.product_rating_stats，中文字段说明以 comment_db/V1 和 product_db/V8 建表脚本为准。

2026-10-28 后续执行更新：图片字段迁移已获授权并执行完成，上方字段已更新为当前实际结构；先前“待部署”段落仅为历史状态，迁移结果以 migrations/IMAGE_FILE_KEYS.md 为准。
