# auth-service 认证服务

> 登录、注册编排、图片验证码、邮箱验证与密码找回转发、JWT 双令牌签发与刷新、登出、登录失败限流。默认端口 `9094`，**无数据库**——全部状态存 Redis。不持有用户表、不校验密码、不发邮件：账号事实与邮件全部委托用户服务。注销冷静期账号登录不签发任何正常令牌，只返回待注销状态与一次性撤回令牌。

## 模块定位与职责边界

- 只负责凭据校验编排、JWT 签发与 Redis 状态（刷新令牌、黑名单、限流、锁定）。
- 密码校验、注册、邮箱验证码发送/校验、找回密码确认均经 Feign 委托用户服务（带 `X-Internal-Token`）。
- 注销撤回令牌不在本服务存储：由用户服务保存（摘要键，10 分钟一次性），本服务只在登录响应中转发。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server（jose）、Validation、Actuator |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer + Sentinel（熔断降级） |
| 状态 | Redis（Redisson）：验证码、刷新令牌、黑名单、限流、锁定 |
| 共享模块 | common-web、common-response、common-redis、user-api |

- 端口 `9094`；无 profile 划分，单配置文件；Actuator 仅 health,info。
- JWT：issuer `http://localhost:9094`，RS256，key-id `order-system-rsa-key-2026`；私钥 PKCS#8、公钥 X.509 外置文件（读取失败启动失败）。
- 访问令牌 1800 秒（代码常量）；刷新令牌 2592000 秒（30 天，可配）。
- 验证码：4 位（可调 3–8）、5 分钟有效、每分钟每 IP 最多 20 张。
- 登录失败：15 分钟窗口 5 次失败锁定 15 分钟。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `AuthController` | 登录/注册/刷新/登出/验证码入口，不碰 Redis 与密码 |
| `EmailOwnershipController` / `PasswordRecoveryController` | 邮箱验证与找回密码的 HTTP 转发（含 IP 限流） |
| `AuthServiceImpl` | 编排：验证码 → 锁定检查 → 委托校验 → 签发；含注销冷静期分支 |
| `JwtTokenService` | RS256 签发访问令牌（含 username/roles/token_type/credential_version/jti） |
| `RefreshTokenServiceImpl` | 刷新令牌 Redis Hash 存取与原子消费 |
| `RefreshTokenLockService` | Redisson 轮换锁（SHA-256(令牌) 为键，等待 5 秒 / 租约 30 秒） |
| `AccessTokenBlacklistService` | jti 写黑名单，TTL 为原令牌剩余有效期 |
| `CaptchaServiceImpl` | AWT 绘制 PNG 验证码；GETDEL 一次性校验；IP 分钟级限流 |
| `LoginAttemptServiceImpl` | 失败计数与临时锁定（键为规范化账号的 SHA-256） |
| `EmailOwnershipServiceImpl` / `PasswordRecoveryServiceImpl` | 流程转发 + IP 限流 + 凭据格式校验 |
| `JwtKeyConfig` / `SecurityConfig` | 密钥加载；匿名白名单；验签 + 黑名单 fail-closed 解码器 |
| `ClientIpResolver` | 限流用来源 IP 解析（代理头优先） |

## 数据与缓存模型（全部 Redis，无表）

| 键 | 内容 | TTL |
|---|---|---|
| `auth:captcha:<captchaId>` | 验证码答案 | 5 分钟 |
| `auth:captcha:generate:<ip>` | 验证码生成限流 | 1 分钟 |
| `auth:refresh-token:<token>` | Hash：用户/凭据版本/角色 | 30 天 |
| `auth:refresh-token:lock:<sha256>` | 刷新轮换锁 | 租约 30 秒 |
| `auth:access-token:blacklist:<jti>` | 登出黑名单 | 原令牌剩余有效期 |
| `auth:login-attempt:failures|lock:<sha256(account)>` | 失败计数与锁定 | 15 分钟 |
| `email-ownership:ip:*` / `password-recovery:ip:*` | 邮箱/找回操作限流 | 10 分钟窗口 |

邮箱验证码本体存储在用户服务（TTL 10 分钟），本服务只持有转发凭据（64 位 hex 令牌，有效期 ≤600 秒）。

## 核心业务流程

### 密码登录

1. 图片验证码 GETDEL 一次性校验（失败不计入登录失败次数，防刷他人账号锁定）。
2. 账号锁定检查：锁定中直接拒绝，不再调用用户服务。
3. 委托用户服务校验密码：服务不可用返回 503；凭据错误计失败数，达阈值锁定。
4. 三种响应形态：
   - 邮箱未验证：返回补验凭据，**不签发任何令牌**。
   - **注销冷静期**（`buildDeletionPendingLogin`）：调用户服务签发一次性撤回令牌（600 秒），响应只含待注销状态、生效时间、撤回令牌与有效期、角色——绝不含访问/刷新令牌；撤回入口获取失败抛 503，**绝不退化为正常登录**；冷静期已过未处理时撤回令牌为空，客户端只展示进度。
   - 正常：签发双令牌。
5. 刷新接口同样拦截冷静期账号：删除旧刷新令牌并要求重新登录。

### 双令牌刷新

1. 取 SHA-256 分布式锁（防同令牌并发轮换）。
2. 读刷新令牌 → 重取用户当前身份 → 三重拦截：邮箱未验证 / 注销冷静期 / 凭据版本不一致，均删除旧令牌并要求重新登录（密码重置即全端失效）。
3. 签发新访问 + 新刷新令牌（轮换）；原子删除旧令牌，删除失败则清理新令牌并报错（防重复签发）。

### 登出

校验刷新令牌归属（用户不匹配 403）→ 先写访问令牌黑名单再删刷新令牌；黑名单写入失败向上抛错，不谎报注销成功。

### 邮箱验证码用途

| 用途 | 端点 | 凭据 |
|---|---|---|
| 注册邮箱验证 | `/auth/registration/code` → `/verify` | verificationToken（注册时提交） |
| 登录后补验/绑定 | `/auth/email-enrollment/code` → `/confirm` | authorizationToken |
| 找回密码 | `/auth/password-reset/code` → `/verify` → `/confirm` | resetToken |

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 同一刷新令牌并发轮换 | Redisson 锁（键为令牌 SHA-256，等待 5 秒 / 租约 30 秒）串行化轮换；锁内「读旧令牌 → 签发新对 → 原子删除旧令牌」 | 抢不到锁返回 503，防止并发重复签发两套令牌 |
| 删除旧令牌失败 | 先签发新令牌后原子删除旧令牌；删除失败则清理刚签发的新令牌并报错 | 保证旧令牌不残留、新令牌不悬挂 |
| 刷新期间密码被改/注销提交 | 轮换时重取用户当前身份并校验 `credential_version`，不一致删除旧令牌要求重新登录 | 旧会话即时失效 |
| 图片验证码重放 | GETDEL 一次性消费（TTL 5 分钟）：二次使用拿不到值即失败；验证码失败不计入登录失败次数（防刷他人账号锁定） | 重放直接拒绝 |
| 登录失败计数并发 | Redis 计数器 + 过期窗口（15 分钟 5 次锁定），键为规范化账号的 SHA-256 | 达阈值 429 锁定；Redis 故障统一 503（fail-closed） |
| 注销撤回令牌重放 | 令牌本体存用户服务（摘要键 + 10 分钟 TTL 一次性消费），本服务只转发 | 重放由用户服务侧拒绝 |
| 登出与刷新并发 | 登出先写访问令牌黑名单再删刷新令牌；黑名单写入失败向上抛错 | 不谎报注销成功；刷新令牌残留由 TTL 到期收敛 |

## 接口清单

| 方法 | 路径 | 匿名 | 说明 |
|---|---|---|---|
| GET | `/auth/captcha` | 是 | 图片验证码（PNG data URI） |
| POST | `/auth/login` | 是 | 登录（三种响应形态） |
| POST | `/auth/register` | 是 | 注册（携带邮箱验证凭据） |
| POST | `/auth/refresh` | 是 | 刷新双令牌 |
| POST | `/auth/logout` | 否 | 登出（双令牌 + 黑名单） |
| POST | `/auth/registration/code`、`/verify` | 是 | 注册邮箱验证 |
| POST | `/auth/email-enrollment/code`、`/confirm` | 是 | 绑定/补验邮箱 |
| POST | `/auth/password-reset/code`、`/verify`、`/confirm` | 是 | 找回密码 |

另放行：`/actuator/health`、swagger；会话 STATELESS，CSRF/formLogin/httpBasic 关闭。

## 安全设计

- JWT 解码 fail-closed：jti 缺失、黑名单命中、Redis 返回空或异常一律 401，不降级放行。
- 验证码、登录保护、刷新锁、令牌消费任一 Redis 故障统一 503，无法确认即拒绝。
- 内部调用统一注入共享令牌；用户服务侧降级工厂 fail-closed，绝不伪造登录成功。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `AUTH_CAPTCHA_LENGTH/TTL/WIDTH/HEIGHT/MAX_PER_MINUTE` | 4 / 5m / 120 / 40 / 20 | 验证码 |
| `AUTH_LOGIN_MAX_FAILURES / FAILURE_WINDOW / LOCK_DURATION` | 5 / 15m / 15m | 登录保护 |
| `security.jwt.refresh-token-ttl-seconds` | 2592000 | 刷新令牌有效期（访问令牌为代码常量 1800 秒） |
| `REDIS_HOST/PORT/PASSWORD` | 127.0.0.1:9096 | Redis |
| `INTERNAL_SCOPE_TOKEN` | 必填，无默认值 | 共享令牌 |
| `SWAGGER_ENABLED / SWAGGER_GATEWAY_BASE_URL` | 开 | 文档 |

## 异常与失败行为

- 用户服务不可用：登录 503（不计失败）；凭据错误 401 并计数；达阈值 429 锁定。
- 刷新令牌消费失败或锁获取失败：503，防止并发重复签发。
- 注销撤回令牌签发失败：503，冷静期账号保持不可登录状态。
- 启动期密钥缺失或 Redis 写入失败：启动失败或回滚刚写数据。

## 关键代码入口

`AuthServiceImpl`（登录三形态与刷新三重拦截）→ `RefreshTokenServiceImpl` + `RefreshTokenLockService`（轮换协议）→ `SecurityConfig`（fail-closed 解码器）。

## 凭据与限流细则

- 图片验证码剔除易混淆字符（0 O o 1 l I），比对忽略大小写。
- 注册编排校验顺序：图片验证码 → 邮件验证码 → 调用户服务，避免无效请求穿透下游；两种验证码失败文案统一为「验证码错误或已过期」防枚举，错误码区分来源。
- 邮件验证码消费语义：成功才删、失败保留并累计尝试，最多 5 次作废；同邮箱每日上限 10 封（配额键 TTL 至次日 0 点）。
- 网关层限流：`/api/auth/captcha` 每分钟 10 次、`/api/auth/email-code` 每分钟 3 次。
- 登录账号支持用户号或规范化邮箱；错误信息统一，不泄露账户是否存在；注册成功返回用户号、不自动登录。
- 访问令牌 `sub` 只放公开 UUID，不含内部用户 ID；旧版无 `credential_version` 声明的令牌按 0 处理。
- 找回密码凭据：HMAC 命名空间包含「找回用途 + 规范化邮箱」，与换绑验证码、授权票据互不通用；重置凭据为 256 位随机值、5 分钟有效、以 HMAC 摘要存储。
- 找回限流：每邮箱 60 秒 1 次、10 分钟 3 次；发送/验证/确认按来源 IP 10 分钟分别 10/30/20 次。
- SMTP 发送失败条件删除该次验证码，日志不含邮箱与异常详情；无对应账号同样返回受理结果（不发邮件、不建账号，防枚举）。
- 各用途的凭据、验证码与 Redis 键命名空间相互隔离，跨用途不能互相替代。

## 已知限制与未验证项

- 访问令牌有效期为代码常量，不可经配置调整。
- 验证码为 AWT 本地绘制，仅支持 PNG。
- 冷静期登录、撤回令牌链路的真机联调与多端会话失效验证待执行。

## 关联文档

- `user-service/README.md`：账号事实、邮箱验证码存储与注销协议。
- `gateway-service/README.md`：路由与注销撤回路径放行。
- 根 README「跨模块业务链路」：登录 → 双令牌 → 资源访问链路。
