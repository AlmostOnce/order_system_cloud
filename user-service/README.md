# user-service 用户服务

> 用户域服务：注册登录辅助、角色与数据范围授权、余额与流水、支付密码、邮箱验证与换绑、收藏（含事件 Outbox）、资料头像、账号注销全流程、管理端角色/状态/范围管理。默认端口 `9092`，数据库 `user_db`。余额账务（扣款/退款/流水）是全系统唯一权威。

## 模块定位与职责边界

- 拥有用户事实：账号、角色、状态、余额、收藏、数据范围授权。
- 不负责订单/支付/工单业务；注销阻断经 Feign 实时聚合订单服务与客服服务。
- `user-api` 为对外契约包：注册/认证（认证服务）、余额扣款/关闭/支付密码校验（支付服务）、数据范围（订单/商品服务）、收藏增量（推荐服务）、聊天身份（通知服务）等。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server（RSA JWT + Redis 黑名单）、spring-security-crypto（BCrypt） |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer、Spring Cloud Stream RocketMQ（仅生产） |
| 数据 | MyBatis-Plus 3.5.8 + Druid + MySQL `user_db`（默认 `127.0.0.1:9095`，账号 `user_service`） |
| 邮件 | spring-boot-starter-mail（QQ SMTP 465 SSL，凭据外置 `email-local.properties`） |
| 缓存 | Redis：JWT 黑名单、数据范围缓存、验证码/授权令牌/限流/租约 |

- `spring.profiles.include=user-favorite-mq`：收藏事件 Outbox 同步发布（3 秒超时），开关 `user.favorite-messaging.enabled`、间隔 1 秒。
- 注销参数：冷静期 72 小时、可放弃余额上限 20 元、提醒提前 24 小时、扫描间隔 5 分钟、单批 20 人、租约 300 秒。
- 支付密码限流：15 分钟内 5 次失败锁定（Redis 计数，不入库）。
- 引导账号开关 `USER_ADMIN_BOOTSTRAP_*` / `USER_MANAGER_BOOTSTRAP_*`（默认关闭）。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `UserController` | ping / 注册 / authenticate（认证服务登录时调用） |
| `UserWalletController` / `UserBalanceController` | 充值与流水、余额与支付密码 |
| `UserPasswordController` / `PayPasswordRecoveryController` | 登录密码修改、支付密码找回 |
| `UserEmailController` | 邮箱换绑授权与验证码 |
| `UserFavoriteController` / `UserProfileController` / `UserAvatarController` | 收藏、资料、头像 |
| `AccountDeletionController` | 学生注销：资格、验证码、授权、提交、撤回 |
| `UserRoleAdministrationController` 等三个管理端控制器 | 角色、状态、数据范围管理 |
| `UserBalanceInternalController` / `BalanceRefundInternalController` | 支付扣款/关单、退款入账 |
| `UserScopeInternalController` / `UserIdentityInternalController` / `EmailOwnershipInternalController` / `PasswordRecoveryInternalController` / `FavoriteRecommendationInternalController` / `AccountDeletionInternalController` | 数据范围、身份、邮箱所有权、密码找回、收藏增量、撤回令牌签发 |
| `UserServiceImpl` | 注册/认证/角色/状态/资料/换邮箱/引导账号 |
| `UserBalanceServiceImpl` / `UserWalletServiceImpl` / `BalanceRefundServiceImpl` | 余额扣款与结算屏障、充值流水、退款入账 |
| `PayPasswordServiceImpl` / `PayPasswordRecoveryServiceImpl` / `PasswordChangeServiceImpl` / `PasswordRecoveryServiceImpl` | 各密码域 |
| `EmailVerificationService` / `EmailChangeAuthorizationService` / `EmailOwnershipServiceImpl` / `SmtpEmailCodeSender` | 邮箱验证码与通知邮件 |
| `UserFavoriteServiceImpl` / `FavoriteEventOutboxPublisher` | 收藏与事件发布 |
| `UserDataScopeServiceImpl` / `UserScopeAdministrationServiceImpl` | 范围快照缓存与授予（经商品服务校验真实存在） |
| `AccountDeletionServiceImpl` / `AccountDeletionVerificationService` / `AccountDeletionBlockerService` / `AccountDeletionScanTask` | 注销业务、验证与令牌、阻断聚合、到期扫描 |
| `OrderDeletionBlockerClient` / `SupportDeletionBlockerClient` | 订单/工单阻断 Feign |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/user_db/`（V1–V14）。

### users（用户账号表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| user_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 用户内部主键 |
| user_uuid | CHAR(36) | NOT NULL，默认随机 UUID v4 | uk_users_user_uuid | 对外稳定随机 UUID，不暴露内部主键 |
| username | VARCHAR(64) | NOT NULL | uk_users_username | 用户名 |
| role_code | VARCHAR(20) | NOT NULL，默认 'CUSTOMER' | — | 角色编码，默认普通用户 |
| balance | DECIMAL(10,2) | NOT NULL，默认 0.00 | — | 账户余额 |
| password_hash | VARCHAR(100) | NOT NULL | — | 登录密码哈希 |
| pay_password_hash | VARCHAR(100) | 可空 | — | 余额支付独立密码哈希，与登录密码完全分离；未设置前禁止使用余额 |
| status | TINYINT | NOT NULL，默认 1 | — | 账号状态 |
| version | INT | NOT NULL，默认 0 | — | 乐观锁版本号 |
| credential_version | BIGINT | NOT NULL，默认 0 | — | 登录凭据版本，找回密码后递增并撤销旧会话 |
| user_no | CHAR(10) | NOT NULL | uk_users_user_no | 永久登录用户号（10 位随机数字，注销后不复用） |
| email | VARCHAR(254) | 可空 | uk_users_email | 登录邮箱 |
| email_verified_at | DATETIME | 可空 | — | 当前邮箱实际验证成功时间，历史账号待补验 |
| avatar_url | VARCHAR(255) | 可空 | — | 头像地址 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |
| deleted_at | DATETIME | 可空 | — | 软删除时间 |
| deletion_requested_at | DATETIME | 可空 | — | 注销申请提交时间，为空表示无待生效申请 |
| deletion_effective_at | DATETIME | 可空 | — | 注销生效时间，申请后默认 72 小时 |
| deletion_reminded_at | DATETIME | 可空 | — | 生效前 24 小时提醒邮件发送时间 |

- 唯一键：`uk_users_username`、`uk_users_user_uuid`、`uk_users_user_no`、`uk_users_email`。
- 索引：`idx_users_deletion_effective(deletion_effective_at)`——到期任务扫描待生效注销账号。
- 历史变更：V6 已删除 `phone` 列及唯一键，账号仅用用户号或邮箱登录。

### user_data_scopes（数据范围授权表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| scope_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 授权记录主键 |
| user_id | BIGINT | NOT NULL | uk_user_data_scopes；idx_user_data_scopes_user | 所属用户 |
| scope_type | VARCHAR(20) | NOT NULL | uk_user_data_scopes | WINDOW 档口 / RESTAURANT 餐厅 / PLATFORM 全平台 |
| scope_ref | BIGINT | NOT NULL，默认 0 | uk_user_data_scopes | 档口/餐厅 ID；PLATFORM 时为 0 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 唯一键：`uk_user_data_scopes(user_id, scope_type, scope_ref)`。跨库不建外键，分配时经商品服务校验真实存在。

### balance_records（余额流水表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| record_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 流水主键 |
| user_id | BIGINT | NOT NULL | uk_balance_records_biz；用户时间与退款溯源索引首列 | 所属用户 |
| change_type | VARCHAR(20) | NOT NULL | uk_balance_records_biz；CHECK 限 RECHARGE/PAY/REFUND/WRITE_OFF | 变动类型：充值/消费/退回/注销余额放弃 |
| amount | DECIMAL(10,2) | NOT NULL | — | 变动金额 |
| balance_after | DECIMAL(10,2) | NOT NULL | CHECK ≥0 | 变动后余额 |
| biz_type | VARCHAR(20) | 可空 | uk_balance_records_biz | 业务单据类型（如 PAYMENT、ORDER） |
| biz_ref | VARCHAR(64) | 可空 | uk_balance_records_biz | 业务单据引用（如 payment_no、order_no） |
| remark | VARCHAR(255) | 可空 | — | 备注 |
| original_biz_ref | VARCHAR(64) | 可空（V10 新增） | 退款溯源索引参与 | 退款关联的原支付单号，旧流水为空 |
| created_at | DATETIME | NOT NULL，默认当前时间 | 用户时间索引参与 | 创建时间 |

- 唯一键：`uk_balance_records_biz(user_id, biz_type, biz_ref, change_type)`——同一单据同一动作只记一次账，记账幂等。
- 索引：`idx_balance_records_user_created(user_id, created_at)`、`idx_balance_refund_origin(user_id, original_biz_ref, change_type)`。

### balance_settlement_guard（余额结算屏障表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| guard_id | BIGINT | NOT NULL，自增 | 主键 | 结算屏障内部编号 |
| user_id | BIGINT | NOT NULL | uk_settlement_ref 首列 | 所属用户内部编号 |
| biz_type | VARCHAR(32) | NOT NULL | uk_settlement_ref | 业务类型 |
| biz_ref | VARCHAR(64) | NOT NULL | uk_settlement_ref | 业务引用 |
| amount | DECIMAL(10,2) | NOT NULL | — | 应扣金额 |
| closed | TINYINT | NOT NULL，默认 0 | — | 是否已关闭未扣款结算 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 创建时间 |

- 唯一键：`uk_settlement_ref(user_id, biz_type, biz_ref)`。扣款与安全关闭共用的持久结算屏障。

### user_favorites（收藏关系表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| favorite_id | BIGINT | NOT NULL，自增 | 主键 | 收藏关系内部编号，同时作为稳定分页游标 |
| user_id | BIGINT | NOT NULL | uk_user_favorite_target；分页索引首列 | 收藏所属用户内部编号 |
| target_type | VARCHAR(16) | NOT NULL | uk_user_favorite_target；分页索引 | WINDOW 窗口店铺 / PRODUCT 菜品 |
| target_id | BIGINT | NOT NULL | uk_user_favorite_target；分页索引 | 商品服务的窗口或菜品 ID，不建跨服务外键 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 收藏创建时间 |

- 唯一键：`uk_user_favorite_target(user_id, target_type, target_id)`——同一用户对同一目标只保留一条。
- 索引：`idx_user_favorite_page(user_id, target_type, favorite_id)`——按用户和类型倒序游标分页。目标停用/下架时关系保留。

### user_favorite_event_outbox（收藏变更事件）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NOT NULL，自增 | 主键 | 事件全局递增编号，同一收藏关系的数值顺序用于拒绝旧事件 |
| user_id | BIGINT | NOT NULL | 目标索引首列 | 收藏所属用户内部编号 |
| target_type | VARCHAR(16) | NOT NULL | 目标索引 | PRODUCT 或 WINDOW |
| target_id | BIGINT | NOT NULL | 目标索引 | 菜品或窗口编号 |
| active | BOOLEAN | NOT NULL | — | 真实新增为 1，真实删除为 0（防旧收藏复活的墓碑） |
| changed_at | DATETIME(3) | NOT NULL，默认当前时间 | — | 事务登记变更时间，不作为事件顺序依据 |
| attempts | INT | NOT NULL，默认 0 | — | 发布失败次数 |
| next_attempt_at | DATETIME(3) | NOT NULL，默认当前时间 | 待发布扫描索引参与 | 允许再次发布的时间 |
| published_at | DATETIME(3) | 可空 | 待发布扫描索引首列 | Broker 同步确认接收时间，空表示仍需重试 |
| last_error | VARCHAR(128) | 可空 | — | 最近发布异常类型，不保存个人资料与异常详情 |

- 索引：`idx_favorite_outbox_pending(published_at, next_attempt_at, id)`、`idx_favorite_outbox_target(user_id, target_type, target_id, id)`。

验证码、授权令牌、撤回令牌、失败计数、扫描租约均为 Redis 键，无对应表。

## 核心业务流程

### 余额账务（全系统唯一权威）

- 扣款：锁定用户行 → 余额条件扣减 + 流水 + 结算屏障同事务原子完成；以业务引用幂等（支付号），响应丢失后同号重试仍返回成功（已入账事实优先）。
- 退款入账：行锁 → 按退款号幂等（同号重放校验原支付号与金额后直接成功）→ 校验原支付流水存在、累计退款不超实付 → 入账 + REFUND 流水。
- 充值：幂等键写入流水。
- 注销核销：注销生效时放弃余额写 WRITE_OFF 流水。

### 支付密码

设置需登录密码确认；校验失败 Redis 计数（15 分钟 5 次锁定）；找回走授权 → 验证码 → 校验 → 确认四步。

### 邮箱

- 换绑：先验登录密码换授权令牌 → 新邮箱验证码（发送限流）→ 消费绑定。
- 注册邮箱验证与密码找回供认证服务内部调用；验证密钥未配置一律拒绝（fail-closed）。

### 收藏

新增/移除与 Outbox 同事务写入（删除事件为墓碑防迟到旧事件复活）；发布器同步发送、确认后才标记；关闭开关时照常落 Outbox，恢复后补发。

### 账号注销全流程

1. 资格查询：余额 ≤20 元 + 实时聚合订单阻断（未完成订单、未结算/被拒退款）与工单阻断（未关闭工单），不缓存——防止处理间隙新订单导致误注销。
2. 验证码 → 一次性授权令牌 → 提交：写申请/生效时间（+72 小时）、撤销全部会话、提升凭据版本使旧令牌失效、签发撤回令牌、发送确认邮件。
3. 撤回：冷静期内可撤回；撤回接口匿名访问（此时无正常令牌），凭 10 分钟一次性撤回令牌。
4. 到期扫描（5 分钟一批 20 人，Redis 租约防多实例并发）：同一事务内复核阻断项后匿名化——改名、清邮箱/头像/密码、余额核销、置停用与删除时间、提升凭据版本；用户号与 UUID 永不复用，订单/退款/流水/工单按原主键保留。
5. 生效前 24 小时提醒邮件：条件更新抢占提醒权，只提醒一次。

### 管理端

角色变更与状态停用均提升凭据版本使旧令牌失效；范围授予前经商品服务校验窗口/食堂真实存在；范围快照短缓存（TTL 300 秒）。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 余额并发扣减 | 用户行 `FOR UPDATE` 锁定后执行「条件扣减 + 流水 + 结算屏障」同事务写入；余额条件更新防负余额 | 扣减失败整体回滚，屏障不落库，调用方可安全重试 |
| 扣款响应丢失后的重复请求 | 流水唯一键 `(user_id, biz_type, biz_ref, change_type)`：同业务引用同动作只记一次账；重试回查命中直接返回成功（已入账事实优先） | 不产生第二次扣款，屏障唯一键兜底并发创建 |
| 退款重复入账 | 按退款单号幂等：命中后校验原支付号与金额一致才返回成功，不一致报错；累计退款不超实付的校验在行锁内完成 | 校验失败拒绝入账，不静默吸收 |
| 支付密码暴力尝试 | Redis 计数限流：15 分钟窗口 5 次失败锁定（键为用户维度，不入库） | 锁定期直接拒绝校验请求 |
| 邮箱验证码重放/共享 | 验证码一次性消费 + 发送频控 + 授权令牌一次性使用；验证密钥缺失一律拒绝 | 重复消费失败；未配置密钥时整个邮箱域 fail-closed |
| 收藏事件重复/乱序 | Outbox 自增 ID 天然保序，消费侧按 `source_version` 单调比较（数值比较，不按字符串）；删除事件为墓碑压过迟到旧事件 | 旧事件被拒绝；发布失败指数重试，开关关闭时照常落 Outbox 恢复后补发 |
| 注销扫描多实例并发 | Redis 租约（300 秒）抢占：只有持租约实例处理当批账号；单批 20 人限流数据库压力 | 无租约实例本轮跳过；租约过期自动释放 |
| 注销提交与新区单竞态 | 生效时同一事务内复核阻断项（订单/工单实时查询）再匿名化；期间新订单会让资格查询失败 | 复核发现阻断即拒绝生效，账号保持冷静期状态 |
| 提醒邮件重复发送 | 条件更新抢占提醒权（`deletion_reminded_at` 由空置非空），只有 1 行更新的赢家发邮件 | 发送失败不占用提醒权，下轮重试 |
| 数据范围缓存与授权撤销 | 范围快照 Redis 缓存 TTL 300 秒，撤销时用户服务主动删除缓存键 | 缓存未及时失效时最多 5 分钟延迟 |
| 凭据版本与会话失效 | 改密/注销/角色变更提升 `credential_version`，资源服务校验令牌携带版本不一致即拒绝 | 旧令牌即时失效，不等自然过期 |

## 接口清单

顾客端（JWT）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/users` / `/users/authenticate` | 注册 / 登录校验（公开） |
| GET | `/users/me/profile`、`/users/me/balance`、`/users/me/balance/records` | 资料、余额、流水 |
| POST | `/users/me/recharges` | 充值（幂等键） |
| PUT | `/users/pay-password`、`/users/me/password`、`/users/me/profile`、`/users/me/avatar` | 支付密码、登录密码、资料、头像 |
| POST | `/users/me/email/authorization|captcha|verification-code` + PUT | 邮箱换绑 |
| PUT/DELETE/POST/GET | `/users/me/favorites/**` | 收藏增删、状态批量、列表 |
| POST | `/users/me/pay-password/recovery/**` | 支付密码找回 |
| GET/POST | `/users/me/deletion/eligibility|verification-code|authorization|（提交）` | 注销资格/验证码/授权/提交 |
| POST | `/users/me/deletion/cancel` | 撤回注销（匿名，一次性令牌） |

管理端：角色与状态（SYSTEM_ADMIN）、数据范围读写删（RESTAURANT_MANAGER / GENERAL_MANAGER）。

内部接口（`/internal/**`，共享令牌）：余额扣款/关闭、退款入账、数据范围、身份、头像、注册与绑定验证、密码找回、收藏增量、撤回令牌签发。

## 消息、异步与缓存设计

| 项 | 内容 |
|---|---|
| 生产 | `user-favorite-changed-v1`（消费方推荐服务），Outbox 同步发布 |
| 消费 | 无 |
| 缓存 | 数据范围快照 TTL 300 秒；验证码/令牌/限流/租约均 Redis |
| 定时 | 注销到期扫描、生效前提醒、收藏 Outbox 补发 |

## 安全设计

- 内部接口共享令牌 fail-closed；依赖方（支付、订单）持有降级工厂，扣款降级迫使调用方回滚。
- 邮箱验证密钥、验证码发送限流、授权令牌一次性消费。
- 注销链路三层防线：邮箱验证码、一次性授权令牌、冷静期撤回令牌。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `account-deletion.cooling-off-hours / max-forfeitable-balance / reminder-lead-hours` | 72 / 20.00 / 24 | 注销冷静期与上限 |
| `account-deletion.scan-* / lease-seconds` | 300000 / 20 / 300 | 扫描节奏与租约 |
| `security.pay-password.max-failures / failure-ttl-seconds / lock-ttl-seconds` | 5 / 900 / 900 | 支付密码限流 |
| `email.*` / `email-local.properties` | — | SMTP 与验证密钥 |
| `USER_ADMIN_BOOTSTRAP_*` / `USER_MANAGER_BOOTSTRAP_*` | false | 引导账号 |
| `user.favorite-messaging.enabled / publish-delay-ms` | true / 1000 | 收藏事件 |
| `internal.scope.token` | — | 共享令牌 |

## 异常与失败行为

- 余额记账「已入账事实优先」：响应丢失后同业务引用重试返回成功，不重复入账。
- 扣款与结算屏障同事务原子；退款累计校验防超退。
- 注销阻断依赖不可用即拒绝注销（fail-closed）；提醒邮件失败由扫描任务下轮重试（提醒权条件更新未占用）。

## 关键代码入口

`UserBalanceServiceImpl` / `BalanceRefundServiceImpl`（账务幂等）→ `AccountDeletionServiceImpl` + `AccountDeletionScanTask`（注销全流程）→ `UserDataScopeServiceImpl`（范围模型）→ `user_db` 迁移脚本。

## 验证、找回与资料编辑细则

### 邮箱验证与换绑

- 验证码统一 10 分钟有效、最多 5 次错误尝试；注册/补验发送按邮箱每 60 秒 1 次、10 分钟 3 次。
- 注册授权 5 分钟一次性、不因填写耗时延长；补验凭据 10 分钟、验证码不得超出补验凭据期限。
- 补验凭据绑定用户 ID、登录密码哈希摘要、原邮箱、角色与凭据版本，期间任一变化即失效。
- 补验成功在同一事务更新邮箱、验证时间并递增凭据版本；并发由行锁 + 验证码一次性 + 授权 CAS + 邮箱唯一约束处理。
- 数据库事务回滚不恢复已消费的 Redis 凭据：失败须重新验证，不冒险恢复授权。
- 换绑流程：图片验证码与认证服务共用 Redis 键原子消费；发送凭据绑定「用户 + 邮箱 + 授权凭据」且单次有效；换绑接口只更新邮箱字段；重发验证码需重新人机验证。

### 密码找回与修改

- 登录密码：新密码 6–64 字符且不超过 BCrypt 72 字节上限、不去除空格；行锁内校验账号/邮箱/版本，新旧密码相同则拒绝（不消费授权、不撤销会话）；密码写入与凭据版本递增同事务。
- 支付密码：新支付密码 6–20 位且不得等于登录密码或旧支付密码；授权 10 分钟、验证码 10 分钟（授权先到期须重新验证）、重置授权 5 分钟；登录密码校验 10 分钟最多 5 次（含成功）；发送每分钟 1 次、10 分钟 3 次；验证码错 5 次作废。
- 支付密码重置成功后清理失败计数且登录会话保持有效；响应返回脱敏邮箱；未绑定邮箱不提供自助恢复。

### 资料编辑

- 昵称去空格后 2–64 字符；邮箱去空格小写、≤254 字符。
- 未绑定邮箱的历史用户可保持空邮箱只改昵称；已有绑定不能清空；邮箱变化必须校验登录密码。
- 同事务锁本人行，只更新昵称/邮箱/版本/时间。

### 钱包接口契约

- 充值幂等键 8–64 位（字母/数字/下划线/连字符），重试必须用原值；单笔 0.01–10000 元、最多两位小数；同键不同金额 409。
- 流水分页：type 可选 RECHARGE/PAY/REFUND，beforeId 游标，limit 默认 20、范围 1–50，按 record_id 倒序。

### 收藏接口边界

- 状态批量单次最多 100 个 ID；分页 limit 默认 20、最大 50，游标必须为正数；非法类型、非正 ID、空批量、非法游标统一参数错误，不执行无界查询。
- 新增/取消幂等：upsert 重复成功、删除不存在记录按成功返回。
- 首期明确不做：收藏分类、排序、浏览历史、热门收藏、推荐位、分享与管理端。

### 角色与数据范围硬约束

- 禁止修改本人角色（403）；授予范围不得越出自身范围；不得对本人授权（403）；禁用/删除最后一个启用的系统管理员被拒绝（409）；审计日志只追加不可改删，固定字段：操作者、角色、动作、目标类型/ID、前后值、结果。
- 失败语义区分：角色具备但访问私有数据越权返回 404（不泄露存在性），公开资源越权返回 403。
- 角色分配与范围授予分离：范围授予只写授权表、不改角色；一次操作不得同时改变身份与范围。
- 授予链：初始总经理 → 授予餐厅经理 → 餐厅经理授予档口负责人；系统管理员是技术角色，不碰业务数据、不分配范围、不推进订单。
- 数据范围不放入 JWT（避免撤销后的越权窗口），缓存键 `authz:user-scope:{userId}`，TTL 300 秒并由用户服务主动删除。
- 引导账号：开关开启但配置缺失或非法（用户名 2–50 字符、初始密码 12–64 字符）则启动失败；SYSTEM_ADMIN 与 GENERAL_MANAGER 独立判断；已存在启用的同角色账号则跳过且不覆盖密码；被占用用户名拒绝自动提权；仅新建总经理时才同事务写入平台范围；只允许单实例临时开启。
- 历史迁移边界：分配用户号时发现重复邮箱明确中止迁移，禁止自动合并账号。
- 内部身份接口为 `/internal/users/{userId}/basic`；头像上传 bizType 为 USER_AVATAR、bizRef 必须为空、上传者取自 JWT；跨服务身份解析实时核对邮箱验证状态。

## 已知限制与未验证项

- 真实邮件发送、跨服务注销联调、冷静期到期任务的运行验收待执行。
- 余额无充值渠道对接，充值接口面向测试场景。
- 数据范围缓存 5 分钟内的新授权存在延迟。

## 关联文档

- `docs/database/migrations/user_db/`：建表与变更脚本。
- `auth-service/README.md`：登录与注销冷静期令牌拦截。
- `payment-service/README.md`：余额扣款协议。
- 根 README「跨模块业务链路」：注册登录、余额支付、注销链路。
