# auth-service 认证服务

> 负责登录、注册、验证码、令牌签发与刷新、注销和密码找回。默认端口 `9094`，本身不拥有用户表，账号事实由 `user-service` 提供。

## 职责与边界

- 接收注册、登录、刷新、注销请求，完成认证流程与令牌生命周期管理。
- 生成图片验证码、限制登录尝试、签发 Access Token / Refresh Token。
- 承接注册邮箱验证、邮箱补绑验证和忘记密码验证码流程，实际发信与用户数据更新由 `user-service` 完成。
- 维护注销黑名单，使尚未过期的 Access Token 立即失效。

**边界：** 认证服务不保存用户资料、密码哈希、余额等账户事实；用户号、邮箱、密码校验都通过 `user-api` 调用用户服务。

## 令牌设计

| 令牌 | 形式 | 存储 | 用途 |
|---|---|---|---|
| Access Token | RSA 签名 JWT | 无状态，资源服务用公钥验签 | 访问业务接口 |
| Refresh Token | 随机字符串 | Redis Hash，带过期时间 | 换取新的 Access Token |

- Access Token 的 `sub` 使用用户公开 UUID，业务服务解析后再换成内部用户 ID。
- Refresh Token 默认有效期 30 天（以运行配置为准）；刷新时先获取与令牌摘要对应的分布式锁，再用 Redis `DEL` 成功作为并发消费的唯一门闩，防止同一令牌被并发重复轮换。
- 锁 Key 使用令牌摘要而不是原文，避免敏感信息进入锁名。
- 写入令牌 Hash 与设置 TTL 是两条 Redis 命令；设置过期失败会尝试删除，但进程在两条命令之间退出仍可能留下无 TTL 的键，这属于已知边界。

## 注销黑名单

JWT 签发后仅靠签名无法提前失效。注销时把 Access Token 的 `jti` 写入 Redis 黑名单，TTL 与令牌剩余寿命匹配；网关和资源服务验签时同时检查黑名单。

## 登录与注册

1. 客户端先请求图片验证码。
2. 登录提交用户号或邮箱、密码、验证码；服务校验验证码与登录尝试限制。
3. 调用用户服务校验账号密码，成功后签发 Access Token 与 Refresh Token。
4. 注册支持邮箱验证码：先向目标邮箱发送验证码，验证通过后拿到一次性注册凭据，再提交注册。
5. 邮箱会去空白并转小写；昵称仅用于展示，不能作为登录账号。登录错误不暴露账号是否存在。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `GET /auth/captcha` | 获取图片验证码，按来源 IP 分钟级限流 |
| `POST /auth/register` | 注册 |
| `POST /auth/login` | 登录 |
| `POST /auth/refresh` | 刷新令牌 |
| `POST /auth/logout` | 注销并拉黑当前 Access Token |
| `POST /auth/registration/code` | 发送注册邮箱验证码 |
| `POST /auth/registration/verify` | 校验注册邮箱验证码并换取注册凭据 |
| `POST /auth/email-enrollment/code` | 发送邮箱补绑验证码 |
| `POST /auth/email-enrollment/confirm` | 确认邮箱补绑 |
| `POST /auth/password-reset/code` | 发送密码找回验证码 |
| `POST /auth/password-reset/verify` | 校验密码找回验证码 |
| `POST /auth/password-reset/confirm` | 确认重置登录密码 |

经网关访问时统一加 `/api` 前缀，例如 `POST /api/auth/login`。

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`AuthController.java`](src/main/java/com/hbue/ordering/auth/controller/AuthController.java) | 验证码、登录、刷新、注销、注册入口 |
| [`EmailOwnershipController.java`](src/main/java/com/hbue/ordering/auth/controller/EmailOwnershipController.java) | 注册邮箱验证与邮箱补绑 |
| [`PasswordRecoveryController.java`](src/main/java/com/hbue/ordering/auth/controller/PasswordRecoveryController.java) | 忘记密码流程 |
| [`AuthServiceImpl.java`](src/main/java/com/hbue/ordering/auth/service/impl/AuthServiceImpl.java) | 登录、注册、注销编排 |
| [`CaptchaServiceImpl.java`](src/main/java/com/hbue/ordering/auth/service/impl/CaptchaServiceImpl.java) | 图片验证码生成与校验 |
| [`RefreshTokenServiceImpl.java`](src/main/java/com/hbue/ordering/auth/service/impl/RefreshTokenServiceImpl.java) | 刷新令牌存储与轮换 |
| [`RefreshTokenLockService.java`](src/main/java/com/hbue/ordering/auth/service/RefreshTokenLockService.java) | 刷新令牌分布式锁 |
| [`JwtTokenService.java`](src/main/java/com/hbue/ordering/auth/service/JwtTokenService.java) | JWT 签发 |
| [`LoginAttemptServiceImpl.java`](src/main/java/com/hbue/ordering/auth/service/impl/LoginAttemptServiceImpl.java) | 登录失败计数与限制 |
| [`JwtKeyConfig.java`](src/main/java/com/hbue/ordering/auth/config/JwtKeyConfig.java) | RSA 密钥加载 |
| [`SecurityConfig.java`](src/main/java/com/hbue/ordering/auth/config/SecurityConfig.java) | 认证服务安全规则 |

## 配置与排查

- RSA 密钥路径由 `security.jwt.*` 配置；服务配置中的 `file:` 路径必须在实际运行环境可访问。
- Redis 保存验证码、刷新令牌、黑名单和登录尝试计数，Redis 不可用会导致登录/刷新失败，不能把故障当成认证成功。
- 验证码接口必须同时在网关白名单和认证服务放行列表内，否则会返回未登录错误。
- 登录被限流时检查网关令牌桶与认证服务的登录尝试计数，两者是不同层面的保护。
- 密码找回与邮箱验证的详细规则见 [`docs/PASSWORD_RECOVERY.md`](../docs/PASSWORD_RECOVERY.md)、[`docs/EMAIL_OWNERSHIP.md`](../docs/EMAIL_OWNERSHIP.md)。
