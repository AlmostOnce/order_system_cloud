-- 根据当前 SHOW CREATE TABLE 生成；仅修改注释，保留完整字段定义、索引和约束。
-- UTF-8 编码；请在业务表迁移完成后执行。执行前应备份表结构。
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 10;

ALTER TABLE `user_db`.`users`
    MODIFY COLUMN `user_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户内部自增主键，仅用于数据库及服务间关联',
    MODIFY COLUMN `user_uuid` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT (lower(concat(hex(random_bytes(4)),_utf8mb4'-',hex(random_bytes(2)),_utf8mb4'-4',substr(hex(random_bytes(2)),2),_utf8mb4'-',elt((1 + (ord(random_bytes(1)) & 3)),_utf8mb4'8',_utf8mb4'9',_utf8mb4'a',_utf8mb4'b'),substr(hex(random_bytes(2)),2),_utf8mb4'-',hex(random_bytes(6))))) COMMENT '对外稳定随机用户标识，UUID第四版，不作为登录密码',
    MODIFY COLUMN `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户展示昵称，不再作为登录账号',
    MODIFY COLUMN `email` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录邮箱，去除首尾空白并转小写，历史账号可为空',
    MODIFY COLUMN `role_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CUSTOMER' COMMENT '角色编码：CUSTOMER顾客、STALL_MANAGER店主、RESTAURANT_MANAGER食堂经理、GENERAL_MANAGER总经理、SYSTEM_ADMIN系统管理员',
    MODIFY COLUMN `balance` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '账户可用余额，单位元',
    MODIFY COLUMN `password_hash` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录密码的BCrypt哈希，禁止保存明文',
    MODIFY COLUMN `pay_password_hash` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '独立支付密码的BCrypt哈希，空值表示尚未设置',
    MODIFY COLUMN `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户头像访问地址，空值使用默认头像',
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT '1' COMMENT '账号状态：1启用，0停用',
    MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于并发更新校验',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    MODIFY COLUMN `deleted_at` datetime DEFAULT NULL COMMENT '逻辑删除时间，空值表示未删除',
    MODIFY COLUMN `user_no` char(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '系统生成的十位永久用户号，可用于登录',
    COMMENT = '用户账户表，保存登录身份、角色及账户余额',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `user_db`.`user_data_scopes`
    MODIFY COLUMN `scope_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '数据范围授权记录主键',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '关联用户内部主键，引用用户服务的用户记录',
    MODIFY COLUMN `scope_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权范围类型：WINDOW窗口、RESTAURANT食堂、PLATFORM全平台',
    MODIFY COLUMN `scope_ref` bigint NOT NULL DEFAULT '0' COMMENT '授权目标内部主键：窗口编号或食堂编号，全平台范围固定为0',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    COMMENT = '用户数据范围授权表，限定管理人员可操作的窗口、食堂或全平台范围',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `user_db`.`balance_records`
    MODIFY COLUMN `record_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '余额流水主键',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '关联用户内部主键，引用用户服务的用户记录',
    MODIFY COLUMN `change_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '余额变动类型：RECHARGE充值、PAY消费、REFUND退款',
    MODIFY COLUMN `amount` decimal(10,2) NOT NULL COMMENT '本次变动金额，单位元，收支方向由变动类型确定',
    MODIFY COLUMN `balance_after` decimal(10,2) NOT NULL COMMENT '本次变动完成后的账户余额，单位元',
    MODIFY COLUMN `biz_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务类型，与业务单据引用和变动类型共同保证记账幂等',
    MODIFY COLUMN `biz_ref` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务单据引用或幂等键，同一用户同类业务动作不可重复记账',
    MODIFY COLUMN `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    COMMENT = '用户余额变动流水表，用于充值、消费和退款对账',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `user_db`.`balance_records` DROP CHECK `chk_balance_records_change_type`, ADD CONSTRAINT `chk_balance_records_change_type` CHECK ((`change_type` in (_latin1'RECHARGE',_latin1'PAY',_latin1'REFUND')));

ALTER TABLE `product_db`.`restaurants`
    MODIFY COLUMN `restaurant_id` bigint NOT NULL AUTO_INCREMENT COMMENT '食堂内部自增主键',
    MODIFY COLUMN `restaurant_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '食堂唯一业务编码',
    MODIFY COLUMN `restaurant_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '食堂名称',
    MODIFY COLUMN `location` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '食堂所在区域或位置',
    MODIFY COLUMN `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '食堂对外联系电话，不用于用户注册或登录',
    MODIFY COLUMN `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '展示图片访问地址，空值使用默认图片',
    MODIFY COLUMN `notice` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '食堂公告内容',
    MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示排序，数值越小越靠前',
    MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '食堂状态：1启用，0停用',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    COMMENT = '食堂资料表，维护食堂展示信息和启用状态',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `product_db`.`dining_windows`
    MODIFY COLUMN `window_id` bigint NOT NULL AUTO_INCREMENT COMMENT '窗口内部自增主键',
    MODIFY COLUMN `restaurant_id` bigint NOT NULL DEFAULT '1' COMMENT '所属食堂内部主键，关联食堂记录',
    MODIFY COLUMN `window_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '窗口唯一业务编码',
    MODIFY COLUMN `window_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '窗口名称',
    MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '窗口简介，在点单页图片下方横向滚动展示',
    MODIFY COLUMN `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '窗口所在楼层及具体方位',
    MODIFY COLUMN `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '展示图片访问地址，空值使用默认图片',
    MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '窗口状态：1启用，0停用',
    MODIFY COLUMN `is_open` tinyint NOT NULL DEFAULT '1' COMMENT '营业标记：1营业中，0暂停营业',
    MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示排序，数值越小越靠前',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    COMMENT = '食堂营业窗口表，保存窗口归属、营业状态和展示资料',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `product_db`.`product_categories`
    MODIFY COLUMN `category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜品分类内部自增主键',
    MODIFY COLUMN `window_id` bigint NOT NULL COMMENT '所属窗口内部主键，关联商品服务窗口记录',
    MODIFY COLUMN `category_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '窗口内的菜品分类名称',
    MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示排序，数值越小越靠前',
    MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '分类状态：1启用，0停用',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    COMMENT = '窗口菜品分类表，分类归属于单个窗口',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `product_db`.`products`
    MODIFY COLUMN `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜品内部自增主键',
    MODIFY COLUMN `window_id` bigint NOT NULL COMMENT '所属窗口内部主键，关联商品服务窗口记录',
    MODIFY COLUMN `category_id` bigint DEFAULT NULL COMMENT '所属窗口内的分类主键，空值表示未分类',
    MODIFY COLUMN `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '窗口内唯一的菜品业务编码',
    MODIFY COLUMN `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜品名称',
    MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容简介或说明',
    MODIFY COLUMN `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '展示图片访问地址，空值使用默认图片',
    MODIFY COLUMN `price` decimal(10,2) NOT NULL COMMENT '当前菜品单价，单位元',
    MODIFY COLUMN `sale_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '菜品售卖状态：1上架，0下架',
    MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示排序，数值越小越靠前',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    COMMENT = '窗口菜品目录表，保存当前售价、分类和上下架状态',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `order_db`.`orders`
    MODIFY COLUMN `order_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '订单内部自增主键',
    MODIFY COLUMN `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对外唯一订单号',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '关联用户内部主键，引用用户服务的用户记录',
    MODIFY COLUMN `window_id` bigint NOT NULL COMMENT '所属窗口内部主键，关联商品服务窗口记录',
    MODIFY COLUMN `total_amount` decimal(10,2) NOT NULL COMMENT '服务端核算的订单总金额，单位元',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单状态：待支付、已支付、制作中、待取货、已完成、已取消',
    MODIFY COLUMN `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '顾客下单备注',
    MODIFY COLUMN `pickup_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '订单取餐码',
    MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于并发更新校验',
    MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '记录最后更新时间',
    MODIFY COLUMN `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '顾客提交的下单幂等键，在用户维度唯一',
    MODIFY COLUMN `request_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规范化下单请求的摘要，用于拒绝同一幂等键提交不同内容',
    COMMENT = '顾客订单主表，记录订单金额、状态及提交幂等信息',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `order_db`.`order_items`
    MODIFY COLUMN `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单明细内部自增主键',
    MODIFY COLUMN `order_id` bigint unsigned NOT NULL COMMENT '所属订单主键，关联本库订单表',
    MODIFY COLUMN `product_id` bigint NOT NULL COMMENT '关联菜品内部主键，引用商品服务菜品记录',
    MODIFY COLUMN `product_code_snapshot` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '下单时的菜品业务编码快照',
    MODIFY COLUMN `product_name_snapshot` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '下单时的菜品名称快照',
    MODIFY COLUMN `unit_price_snapshot` decimal(10,2) NOT NULL COMMENT '下单时的菜品单价快照，单位元',
    MODIFY COLUMN `quantity` int unsigned NOT NULL COMMENT '本次购买的菜品份数',
    MODIFY COLUMN `line_amount` decimal(12,2) NOT NULL COMMENT '该明细的单价乘数量金额，单位元',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    COMMENT = '订单菜品明细快照表，保存下单时的菜品名称、单价和数量',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `order_db`.`sales_event_outbox`
    MODIFY COLUMN `event_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '销量事件唯一标识，用于消息消费幂等',
    MODIFY COLUMN `order_id` bigint unsigned NOT NULL COMMENT '已支付订单主键，每个订单仅生成一条销量事件',
    MODIFY COLUMN `payload` json NOT NULL COMMENT '待投递的销量事件JSON载荷，包含订单及菜品销量快照',
    MODIFY COLUMN `attempts` bigint NOT NULL DEFAULT '0' COMMENT '事件投递失败后的累计重试次数',
    MODIFY COLUMN `next_attempt_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下一次允许尝试投递的时间',
    MODIFY COLUMN `published_at` datetime DEFAULT NULL COMMENT '事件成功投递时间，空值表示尚未成功投递',
    MODIFY COLUMN `last_error` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次投递失败的错误摘要',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    COMMENT = '订单销量事件本地消息表，保障支付销量事件可靠投递',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `order_db`.`undo_log`
    MODIFY COLUMN `branch_id` bigint NOT NULL COMMENT 'Seata分支事务标识',
    MODIFY COLUMN `xid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Seata全局事务标识',
    MODIFY COLUMN `context` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回滚日志序列化方式及扩展上下文',
    MODIFY COLUMN `rollback_info` longblob NOT NULL COMMENT '分支事务回滚所需的二进制数据',
    MODIFY COLUMN `log_status` int NOT NULL COMMENT '回滚日志状态：0正常，1防御状态',
    MODIFY COLUMN `log_created` datetime(6) NOT NULL COMMENT '回滚日志创建时间，精确到微秒',
    MODIFY COLUMN `log_modified` datetime(6) NOT NULL COMMENT '回滚日志最后修改时间，精确到微秒',
    COMMENT = 'Seata分布式事务AT模式回滚日志表',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `stock_db`.`undo_log`
    MODIFY COLUMN `branch_id` bigint NOT NULL COMMENT 'Seata分支事务标识',
    MODIFY COLUMN `xid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Seata全局事务标识',
    MODIFY COLUMN `context` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回滚日志序列化方式及扩展上下文',
    MODIFY COLUMN `rollback_info` longblob NOT NULL COMMENT '分支事务回滚所需的二进制数据',
    MODIFY COLUMN `log_status` int NOT NULL COMMENT '回滚日志状态：0正常，1防御状态',
    MODIFY COLUMN `log_created` datetime(6) NOT NULL COMMENT '回滚日志创建时间，精确到微秒',
    MODIFY COLUMN `log_modified` datetime(6) NOT NULL COMMENT '回滚日志最后修改时间，精确到微秒',
    COMMENT = 'Seata分布式事务AT模式回滚日志表',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_db`.`payments`
    MODIFY COLUMN `payment_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '支付单内部自增主键',
    MODIFY COLUMN `payment_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对外唯一支付单号',
    MODIFY COLUMN `order_id` bigint NOT NULL COMMENT '关联订单内部主键，跨服务逻辑关联',
    MODIFY COLUMN `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建支付单时的订单号快照',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '付款用户内部主键，用于支付归属校验与对账',
    MODIFY COLUMN `channel_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道编码，BALANCE表示账户余额支付',
    MODIFY COLUMN `amount` decimal(10,2) NOT NULL COMMENT '创建支付单时的应付金额快照，单位元',
    MODIFY COLUMN `paid_amount` decimal(10,2) DEFAULT NULL COMMENT '支付成功后确认的实际到账金额，单位元',
    MODIFY COLUMN `channel_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付渠道返回的交易流水号',
    MODIFY COLUMN `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CREATED' COMMENT '支付单状态：CREATED已创建、SUCCESS成功、FAILED失败、CLOSED关闭',
    MODIFY COLUMN `fail_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付失败原因或异常备注',
    MODIFY COLUMN `notify_count` int NOT NULL DEFAULT '0' COMMENT '累计收到的支付回调次数',
    MODIFY COLUMN `last_notify_at` datetime DEFAULT NULL COMMENT '最近一次收到支付回调的时间',
    MODIFY COLUMN `success_time` datetime DEFAULT NULL COMMENT '支付成功确认时间',
    MODIFY COLUMN `active_flag` tinyint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'CREATED') then 1 else NULL end)) VIRTUAL COMMENT '进行中支付单标记，用于约束同一订单至多一个已创建支付单',
    MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '记录最后更新时间',
    COMMENT = '订单支付单表，记录支付渠道、处理状态及到账结果',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `stock_db`.`stock`
    MODIFY COLUMN `stock_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '库存记录内部自增主键',
    MODIFY COLUMN `window_id` bigint unsigned NOT NULL COMMENT '所属窗口内部主键，关联商品服务窗口记录',
    MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '关联菜品内部主键，引用商品服务菜品记录',
    MODIFY COLUMN `total_qty` int NOT NULL DEFAULT '0' COMMENT '库存初始或调整后的总量基线，用于库存对账',
    MODIFY COLUMN `available_qty` int NOT NULL DEFAULT '0' COMMENT '当前可售库存份数，以数据库记录为准',
    MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于并发更新校验',
    MODIFY COLUMN `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0正常，1已删除',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    COMMENT = '窗口菜品库存主表，数据库数量为库存事实来源',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `stock_db`.`stock_flow`
    MODIFY COLUMN `flow_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '库存变动流水内部自增主键',
    MODIFY COLUMN `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库存业务类型：ORDER_DEDUCT下单扣减、ORDER_RESTORE取消回补、ADMIN_ADJUST管理调整',
    MODIFY COLUMN `biz_ref` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联业务单据引用，与业务类型及库存记录共同保证幂等',
    MODIFY COLUMN `stock_id` bigint unsigned NOT NULL COMMENT '关联库存主表记录主键',
    MODIFY COLUMN `window_id` bigint unsigned NOT NULL COMMENT '所属窗口内部主键，关联商品服务窗口记录',
    MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '关联菜品内部主键，引用商品服务菜品记录',
    MODIFY COLUMN `change_qty` int NOT NULL COMMENT '库存变动份数，扣减为负数，回补为正数',
    MODIFY COLUMN `before_qty` int NOT NULL COMMENT '本次变动之前的可售库存份数',
    MODIFY COLUMN `after_qty` int NOT NULL COMMENT '本次变动之后的可售库存份数',
    MODIFY COLUMN `operator_id` bigint unsigned DEFAULT NULL COMMENT '操作用户内部主键，系统自动触发时可为空',
    MODIFY COLUMN `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务备注',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    COMMENT = '窗口菜品库存变动流水表，记录扣减、回补及调整并保证业务幂等',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `file_db`.`file_objects`
    MODIFY COLUMN `file_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '文件记录内部自增主键',
    MODIFY COLUMN `file_key` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对外唯一文件键，为去除连接符的UUID，不暴露内部主键',
    MODIFY COLUMN `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件业务类型：AVATAR头像、WINDOW_IMAGE窗口图、PRODUCT_IMAGE菜品图、RESTAURANT_IMAGE食堂图、COMMENT_IMAGE评价图、OTHER其他',
    MODIFY COLUMN `biz_ref` bigint unsigned DEFAULT NULL COMMENT '文件关联业务记录的内部主键，尚未绑定时可为空',
    MODIFY COLUMN `storage_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LOCAL' COMMENT '文件存储介质：LOCAL本地、MINIO或S3对象存储',
    MODIFY COLUMN `object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储介质中的相对路径，由服务端生成',
    MODIFY COLUMN `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传时的原始文件名，仅作展示，不参与存储路径拼接',
    MODIFY COLUMN `file_ext` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规范化的小写文件扩展名，须通过白名单校验',
    MODIFY COLUMN `content_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通过文件内容校验确定的媒体类型',
    MODIFY COLUMN `file_size` bigint unsigned NOT NULL COMMENT '文件大小，单位字节',
    MODIFY COLUMN `sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件内容的SHA256摘要，用于去重及完整性校验',
    MODIFY COLUMN `uploader_id` bigint unsigned NOT NULL COMMENT '上传用户的内部主键，关联用户服务记录',
    MODIFY COLUMN `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0正常，1已删除',
    MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    COMMENT = '上传文件对象元数据表，记录存储位置、归属及文件校验信息',
    ALGORITHM=INPLACE, LOCK=NONE;
