# 优惠券到期提醒（消息中心）实现报告

> 范围：coupon-service（扫描与发送）、notification-service（独立类型与落库）、学生端消息中心（跳转）。
> 未运行 Maven、未编译、未启动服务；**迁移脚本尚未执行**（本机无 mysql 客户端与 docker）。

---

## 1. 整体链路

```
coupon-service 定时任务（每 5 分钟）
  └─ 扫描 user_coupon：AVAILABLE + 未提醒 + 24 小时内到期（SQL 限定，最多 200 条/轮）
      └─ 逐条调用 notification-service 内部接口（X-Internal-Token）
          └─ 统一写入器：截断 → 幂等落库 → 偏好判断 → 事务提交后 WebSocket 提示
      └─ 通知服务确认成功后写 expiry_reminded_at
```

判断"是否进入 24 小时窗口"完全由 SQL 完成（`valid_until > NOW() AND valid_until <= NOW()+24h`），
Java 侧不重复计算时间窗，避免应用与数据库时钟偏差导致漏发或重复发。

## 2. 与订单通知、工单通知的独立性

| 维度 | 订单 | 工单 | 优惠券 |
| --- | --- | --- | --- |
| notification_type | `ORDER_STATUS` | `SUPPORT_REPLY` / `SUPPORT_STATUS` | `COUPON_EXPIRING` |
| 偏好分类 | `ORDER` | `SUPPORT` | `COUPON`（新增） |
| 业务引用 | `order_id` | `biz_type=SUPPORT` + `biz_id` | `biz_type=COUPON` + `biz_id=用户券编号` |
| 未读角标映射 | `ORDER_STATUS→ORDER` | `SUPPORT_*→SUPPORT` | `COUPON_EXPIRING→COUPON` |

补充说明：

- 偏好表是"缺省即开启"的稀疏存储，新增 `COUPON` 分类不需要回填历史用户，设置页会自动多出一项。
- 关闭 `ORDER` 或 `SUPPORT` 不影响券提醒，反之亦然；关闭后通知仍落库，只是不推送实时提示、不计入未读角标。
- 券提醒不写 `order_id`，因此消息中心不会把它当成订单通知处理。

## 3. 幂等设计（两层）

1. **本地标记**：`user_coupon.expiry_reminded_at`，发送成功后条件更新
   （`WHERE expiry_reminded_at IS NULL`），多实例并发只有一次能写成功，未命中视为已被其它实例处理。
2. **通知唯一键**：事件标识固定为 `coupon-expiry:{userCouponId}`，与接收人共同命中
   `uk_notification_recipient_event`。即使标记丢失导致重发，也只会落一条记录。

发送顺序是"**先发通知、后打标记**"：先打标记一旦通知失败就永远补不回来；
先发通知最多在标记失败时重发一次，而重发会被唯一键吸收。

单条失败只记日志并继续处理剩余券，该券仍是"未提醒"，下一轮重试；扫描本身失败也只记录，
由下次调度重试，不会中断后续提醒。

## 4. 代码改动

**notification-service**

| 文件 | 改动 |
| --- | --- |
| `model/NotificationCategory` | 新增 `COUPON`（优惠券到期提醒），设置页自动展示 |
| `mapper/NotificationMapper` | 未读统计 CASE 增加 `COUPON_EXPIRING→COUPON`，让偏好开关对券提醒生效 |
| `dto/NotificationDraft`、`dto/CouponExpiryNotificationRequest` | 新增统一草稿与券提醒请求契约 |
| `service/SystemNotificationWriter` + `impl/SystemNotificationWriterImpl` | 从反馈通知中抽出统一写入逻辑（校验、截断、幂等、偏好、提交后推送） |
| `service/impl/SupportNotificationService` | 改为组装草稿并委托统一写入器，行为与文案不变 |
| `service/impl/CouponExpiryNotificationService` | 券提醒的类型、兜底文案与业务跳转类型 |
| `controller/NotificationInternalController` | 新增 `POST /internal/notifications/coupon-expiring` |

**coupon-service**

| 文件 | 改动 |
| --- | --- |
| `model/UserCouponDO` | 新增 `expiryRemindedAt` |
| `mapper/UserCouponMapper` | 新增候选扫描与标记更新（扫描走 `(status, expiry_reminded_at, valid_until)` 索引） |
| `dto/CouponExpiryReminderRow` | 扫描行（券编号、用户、券名、截止时间） |
| `client/CouponNotificationClient` + `client/vo/...` | 调用通知服务内部接口 |
| `task/CouponExpiryReminderTask` | 定时任务：首轮延迟 1 分钟，之后每 5 分钟；单批 200 条 |
| `CouponServiceApplication` | 增加 `@EnableScheduling`，并注册新 Feign 客户端 |

可调参数：`coupon.expiry-reminder.initial-delay-ms`（默认 60000）、
`coupon.expiry-reminder.delay-ms`（默认 300000）。

**学生端（`E:/order_system_cloud_user`）**

`pages/notifications/orders.uvue`：优惠券提醒气泡显示"查看优惠券"，点击跳转
`/pages/coupons/list?tab=mine`；`canOpen` 同步识别 `bizType=COUPON`，避免被当成不可点击通知置灰。

## 5. 数据库迁移（未执行）

| 脚本 | 内容 |
| --- | --- |
| `docs/database/migrations/coupon_db/V2__coupon_expiry_reminder.sql` | 新增 `expiry_reminded_at` 与扫描索引，可重复执行 |
| `docs/database/migrations/notification_db/V7__coupon_expiry_notification_comment.sql` | 仅更新 `biz_type`、`biz_category` 注释，说明新增 COUPON |

`notification_db` 不需要结构变更：`notification_type` 已有 32 字符、`biz_type` 16 字符、
`biz_id` 64 字符均足够；`notification_preferences` 是稀疏表，新分类无需回填。

本机 PATH 中没有 `mysql` 客户端，也没有 docker，无法连接 `127.0.0.1:9095`，请在确认目标库后执行：

```
mysql -h 127.0.0.1 -P 9095 -u coupon_service -p < docs/database/migrations/coupon_db/V2__coupon_expiry_reminder.sql
mysql -h 127.0.0.1 -P 9095 -u <notification库账号> -p < docs/database/migrations/notification_db/V7__coupon_expiry_notification_comment.sql
```

不执行第一条时，扫描 SQL 会因缺少 `expiry_reminded_at` 列直接报错，任务日志每轮记录一次扫描失败。

## 6. 验证情况

已完成：

1. 全仓 `com.hbue.*` 导入可解析、包声明与目录一致。
2. IDE 诊断（error/warning）对 coupon-service、notification-service 无报错。
3. 公共模块确认没有 `@Scheduled`，在 coupon-service 开启调度不会顺带激活意外任务。
4. 逐项核对通知表列长度：`COUPON_EXPIRING`(15) ≤ 32、`COUPON`(6) ≤ 16、券编号 ≤ 64。

未完成：

- 迁移未执行（原因同上）。
- 编译、单元测试、服务启动、真实 MQ/WebSocket 链路未验证（项目约束禁止擅自使用 Maven）。
- 前端仅做源码修改，未运行 HBuilderX 构建。

## 7. 已知取舍

1. **按券提醒，不按用户合并**：一次有 3 张券同时在 24 小时内到期会收到 3 条提醒。
   若希望合并成"您有 3 张券即将过期"，需要把幂等键改成"用户 + 自然日"，可后续单独调整。
2. **领取时剩余有效期不足 24 小时的券会立即提醒**：扫描只看"是否进入窗口"，不区分领取时间，
   这符合"快过期了"的语义，但可能让刚领的短效券马上收到提醒。
3. 消息中心 UI 仍把系统通知统一放在"订单中心"会话里（含工单、券提醒），
   本次只保证后端类型与偏好独立；若要在界面上也拆成独立会话，需要另行设计入口分组。
4. 上一轮"待评价入口 + 角标"的提问尚未答复（入口位置、待评价口径、点击跳转），该项未开始。
