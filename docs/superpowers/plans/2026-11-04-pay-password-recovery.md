# 支付密码找回实施计划

**Goal:** 已登录学生通过登录密码、人机验证及绑定邮箱验证码重置支付密码。
**Architecture:** Controller → Service → Mapper，复用统一 Redis、现有一次性 Lua 和 SMTP；独立 pay-password-recovery 用途，不复用换绑或登录找回凭据。
**Tech Stack:** 现有 Spring、MP、Redis、SMTP、uni-app UTS；不新增依赖与表字段。

用户已确认推荐流程并要求直接完成。无邮箱不提供自助恢复；新密码6至20位，不得等于登录或旧支付密码。余额、流水、登录密码不变。

- [x] 新增独立服务、脱敏请求 DTO、本人 CUSTOMER 接口；登录校验10分钟最多5次、发送每分钟1次/10分钟3次、邮箱错误5次失效。
- [x] 授权与当前邮箱、登录/支付密码及凭据版本绑定；最终数据库行锁内核对快照并原子消费，防并发重放。
- [x] 学生端独立找回页面与入口，分步表单、倒计时、离页清理、晚到响应隔离。
- [x] 添加安全边界测试源码及前端交互检查；只运行允许的检查，不运行 Maven。
- [x] 更新学生端计划，区分源码完成与运行验收。

风险检查：跨用户/用途、密码或邮箱变更、错误次数与过期、并发重复提交、隐藏页晚到响应。后端 Java 测试、真实邮件及运行联调未经执行不能标记通过。

## 接口与验证记录

全部接口要求 CUSTOMER Bearer Token，经网关统一加 `/api`，用户ID取认证主体：

| POST路径 | 请求体 | 成功数据 |
|---|---|---|
| `/users/me/pay-password/recovery/authorization` | `loginPassword` | `authorizationToken`、脱敏 `maskedEmail` |
| `/users/me/pay-password/recovery/code` | `authorizationToken`、`captchaId`、`captchaCode` | 无 |
| `/users/me/pay-password/recovery/verify` | `authorizationToken`、六位字符串 `code` | `resetToken`、`expiresIn=300` |
| `/users/me/pay-password/recovery/confirm` | `resetToken`、`newPayPassword` | 无 |

授权10分钟、验证码10分钟（授权先到期则需重新验证），重置授权5分钟。发送成功仅切换邮箱验证码表单，不立即重新加载图片；重发需重新人机验证。错误5次后验证码删除，重发每分钟1次/10分钟3次。登录密码校验10分钟最多5次（包括成功）。使用现有QQ SMTP配置和共享HMAC密钥；验证码与授权仅在Redis保存摘要。当前项目没有历史邮箱已验证标志，此流程只能证明当前绑定邮箱可收信，不宣称历史邮箱已完成注册验证。

最终更新与普通支付密码修改共用用户行锁，锁内重新核对快照、拒绝复用密码并原子消费授权；数据库事务回滚后凭据不恢复，需重新找回。重置成功清理支付密码失败计数与锁定；账号登录会话保持有效。

已运行：学生端找回交互检查、既有登录找回回归、Vue模板及原生CSS约束检查、Redis TTL/一次消费/并发消费/条件删除检查、git diff --check。
未运行：Maven、Java测试与服务启动、真实SMTP及端到端支付联调；测试源码完成不等于运行通过。
未触碰原有 `.idea/sqldialects.xml` 删除。

Web构建已完成（HBuilderX编译器5.26），产物已同步本地Nginx静态目录，`http://localhost:8088/` 返回200。Java仅做13个文件语法解析，PARSE_ERRORS=0，不代表类型检查或运行验收。尚未进行浏览器页面视觉检查。
