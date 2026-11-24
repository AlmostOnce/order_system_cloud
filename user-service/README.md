# user-service 用户与账户服务

> 维护用户身份、角色、数据范围、余额账务、支付密码、邮箱、收藏与账号注销。默认端口 `9092`，数据库 `user_db`。

## 职责与边界

- 用户资料：用户号、UUID、邮箱、昵称、密码哈希、角色、状态、头像文件键。
- 权限范围：窗口、食堂、全平台三级数据授权，支撑商家与管理角色的资源边界。
- 余额账务：充值、消费扣款、退款、余额流水与结算防重。
- 支付密码：独立于登录密码，负责设置、修改、找回与错误次数限制。
- 邮箱：换绑验证、注册邮箱验证、支付密码找回验证码。
- 顾客收藏：菜品与窗口收藏，写入事件 outbox 供推荐服务消费。
- 账号注销：资格核对、邮箱验证、72 小时冷静期、到期复核与匿名化。
- 内部接口：供认证、支付、订单、商品、搜索、推荐、反馈等服务查询身份、余额与数据范围。

**边界：** 公开接口不得返回密码哈希、支付密码哈希或内部用户 ID；授权以服务端数据范围为准，不信任前端传来的窗口/食堂编号。

## 身份标识

| 标识 | 作用 | 是否秘密 |
|---|---|---|
| `user_id` | 数据库关联与内部业务处理 | 否 |
| `user_uuid` | 对外稳定公开身份、JWT subject | 否 |
| `user_no` | 十位永久用户号，可作为登录账号 | 否 |

用户号/邮箱回答“是谁”，登录密码验证身份，支付密码保护余额操作，三者职责不同。

## 账户与余额

- 余额与流水同库，充值或扣款在同一本地事务内更新余额并记录 `balance_records`。
- 流水唯一键 `(user_id, biz_type, biz_ref, change_type)` 保证同一业务单据只记账一次。
- 充值请求携带 `Idempotency-Key`：同键同金额返回已有结果，同键不同金额冲突。
- `balance_after` 是该笔流水发生后的余额，不是实时余额。
- `balance_settlement_guard` 用于结算防重与关账保护。
- 当前充值不接第三方收款渠道，属于系统内入账流程。

## 支付密码

- 与登录密码分开保存 BCrypt 哈希。
- 首次设置通过登录密码确认身份，修改时校验原支付密码。
- 连续失败计数使用 Redis 带 TTL 计数器，达到阈值锁定一段时间；Redis 故障或锁定状态下不能当作验证成功。
- 支付密码找回走「授权 → 邮箱验证码 → 校验 → 确认」流程，见 [`docs/PASSWORD_RECOVERY.md`](../docs/PASSWORD_RECOVERY.md)。

## 邮箱与资料

- 修改或首次绑定邮箱需要登录密码和发送到目标邮箱的六位验证码；只改昵称不需要验证码。
- 验证码有效期 10 分钟；同一用户一分钟最多发送一次，同一邮箱 10 分钟最多三次。
- Redis 保存与用户、邮箱、验证码绑定的 HMAC 摘要，不保存明文；错误五次失效，正确验证用 Lua 原子消费。
- 发信通过 QQ SMTP，配置来自 `email-local.properties` 或 `MAIL_USERNAME` / `MAIL_PASSWORD` / `EMAIL_VERIFICATION_SECRET` 环境变量。`MAIL_PASSWORD` 是 SMTP 授权码。
- Redis 与 MySQL 不在同一事务：验证码消费后数据库写入失败可能需要重新获取验证码。

## 收藏

- `PUT /users/me/favorites/{targetType}/{targetId}` 收藏，`DELETE` 取消收藏，`POST /users/me/favorites/status` 批量查询状态，`GET /users/me/favorites` 分页查询。
- 收藏变更与 `favorite_event_outbox` 同事务落库，后台发布到 `user-favorite-changed-v1`，供推荐服务更新画像。
- 内部接口 `/internal/users/recommendations/favorites` 供推荐服务按游标增量导出。

## 账号注销

注销状态机：资格核对 → 邮箱验证 → 提交申请 → 72 小时冷静期 → 到期复核匿名化。

- 提交申请前检查未完成订单、反馈工单、余额等阻断项。
- 提交成功后递增凭据版本使刷新令牌失效，并把当前 Access Token 加入黑名单，提交设备立即下线。
- 冷静期内只能凭登录时签发的一次性撤回令牌撤回，撤回接口不要求登录令牌。
- 重复提交且申请仍待生效时返回原生效时间，不重置冷静期。
- 到期由扫描任务复核后执行匿名化，保留必要的交易与审计数据。

## 管理接口

| 接口 | 作用 |
|---|---|
| `PUT /users/{userUuid}/role` | 分配角色（系统管理员） |
| `PATCH /users/{userUuid}/status` | 启用/停用账号 |
| `GET/PUT/DELETE /users/{userUuid}/scopes` | 查询、保存、删除数据范围 |

角色硬约束：系统管理员不能修改自身角色、不能给自己分配数据范围、不能禁用/删除最后一个系统管理员。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `GET /users/me/profile` / `PUT /users/me/profile` | 查询/修改本人资料 |
| `GET /users/me/balance` | 余额与支付密码状态 |
| `POST /users/me/recharges` | 充值 |
| `GET /users/me/balance/records` | 余额流水（游标分页） |
| `PUT /users/pay-password` | 设置/修改支付密码 |
| `PUT /users/me/password` | 修改登录密码 |
| `POST /users/me/email/authorization`、`/captcha`、`/verification-code`、`PUT /users/me/email` | 邮箱换绑流程 |
| `PUT /users/me/avatar` | 绑定头像文件键 |
| `GET/POST /users/me/deletion/**` | 注销资格、验证码、授权、提交、撤回 |
| `GET /users/ping` | 健康探测 |

内部接口包括：用户注册与认证、身份解析、头像批量查询、余额扣款/关账/退款、数据范围查询、注销阻断项、密码找回等，统一以 `/internal/**` 暴露，不经网关。

## 数据表

| 表 | 作用 |
|---|---|
| `users` | 用户账户主表：用户号、UUID、邮箱、昵称、密码哈希、角色、状态、余额、支付密码哈希、头像文件键、乐观锁版本、注销字段 |
| `user_data_scopes` | 数据范围授权：`WINDOW` / `RESTAURANT` / `PLATFORM` |
| `balance_records` | 余额流水：充值、消费、退款及记账幂等键 |
| `balance_settlement_guard` | 余额结算防重与关账保护 |
| `user_favorites` | 菜品/窗口收藏 |
| `favorite_event_outbox` | 收藏变更事件本地消息表 |

`users` 主要字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | bigint | 内部自增主键 |
| `user_uuid` | char(36) | 对外稳定 UUID |
| `user_no` | char(10) | 十位永久用户号，可登录 |
| `username` | varchar(64) | 展示昵称 |
| `email` | varchar(254) | 登录邮箱，去空白转小写，历史账号可为空 |
| `role_code` | varchar(32) | `CUSTOMER` / `STALL_MANAGER` / `RESTAURANT_MANAGER` / `GENERAL_MANAGER` / `SYSTEM_ADMIN` |
| `balance` | decimal(12,2) | 可用余额 |
| `password_hash` | varchar(255) | 登录密码 BCrypt 哈希 |
| `pay_password_hash` | varchar(100) | 支付密码 BCrypt 哈希，空表示未设置 |
| `avatar_file_key` | char(32) | 头像文件键 |
| `status` | tinyint | 1 启用，0 停用 |
| `version` | int | 乐观锁版本 |
| `credential_version` | int | 凭据版本，注销或改密时递增使旧令牌失效 |
| `email_verified_at` | datetime | 邮箱验证时间 |

`user_data_scopes`：`scope_id`、`user_id`、`scope_type`、`scope_ref`（全平台固定 0）、`created_at`、`updated_at`。

`balance_records`：`record_id`、`user_id`、`change_type`（`RECHARGE` / `PAY` / `REFUND`）、`amount`、`balance_after`、`biz_type`、`biz_ref`、`remark`、`created_at`。

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`UserServiceImpl.java`](src/main/java/com/hbue/ordering/user/service/impl/UserServiceImpl.java) | 用户注册、认证与资料 |
| [`UserWalletServiceImpl.java`](src/main/java/com/hbue/ordering/user/service/impl/UserWalletServiceImpl.java) | 钱包与充值 |
| [`UserBalanceServiceImpl.java`](src/main/java/com/hbue/ordering/user/service/impl/UserBalanceServiceImpl.java) | 余额扣款与流水 |
| [`PayPasswordServiceImpl.java`](src/main/java/com/hbue/ordering/user/service/impl/PayPasswordServiceImpl.java) | 支付密码设置与校验 |
| [`PayPasswordRecoveryServiceImpl.java`](src/main/java/com/hbue/ordering/user/service/impl/PayPasswordRecoveryServiceImpl.java) | 支付密码找回 |
| [`EmailVerificationService.java`](src/main/java/com/hbue/ordering/user/service/impl/EmailVerificationService.java) | 邮箱验证码 |
| [`UserDataScopeServiceImpl.java`](src/main/java/com/hbue/ordering/user/service/impl/UserDataScopeServiceImpl.java) | 数据范围 |
| [`UserFavoriteServiceImpl.java`](src/main/java/com/hbue/ordering/user/service/impl/UserFavoriteServiceImpl.java) | 收藏与事件 |
| [`AccountDeletionServiceImpl.java`](src/main/java/com/hbue/ordering/user/service/impl/AccountDeletionServiceImpl.java) | 注销状态机 |
| [`AccountDeletionScanTask.java`](src/main/java/com/hbue/ordering/user/service/impl/AccountDeletionScanTask.java) | 冷静期到期扫描 |
| [`UserBootstrapRunner.java`](src/main/java/com/hbue/ordering/user/bootstrap/UserBootstrapRunner.java) | 初始总经理引导创建 |

## 迁移与运行

- 迁移脚本位于 [`docs/database/migrations/user_db`](../docs/database/migrations/user_db)。
- 迁移前检查重复邮箱、旧账号用户号分配、手机号列依赖和图片文件键历史数据。
- 发信配置只放在本机忽略文件或环境变量中，多实例的 `EMAIL_VERIFICATION_SECRET` 必须一致。
- 详细接口说明见 [`docs/WALLET_API.md`](../docs/WALLET_API.md)、[`docs/PROFILE_EDIT.md`](../docs/PROFILE_EDIT.md)、[`docs/USER_PUBLIC_ID.md`](../docs/USER_PUBLIC_ID.md)。
