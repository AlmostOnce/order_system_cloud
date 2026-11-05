# 注册邮箱验证与旧账号强制补验证

2026-11-05：代码、V9迁移脚本及学生端页面已完成；实际用户表已备份并执行V9；本次未启停服务、未运行Maven。用户服务用户查询已由HTTP 500恢复为200。本轮Web产物位于用户端 unpackage/dist/build/email-ownership-web，暂未覆盖Nginx，避免旧后端没有新增匿名接口而导致注册不可用。

## 已确认业务规则

新注册必须先通过图片验证码及邮件验证码，再提交昵称、密码和一次性邮箱授权创建账号。验证邮箱不会提前创建待验证用户。

旧账号也必须验证后才能继续使用。正确账号密码与登录图片验证码通过后，仅返回10分钟有效的邮箱补验凭据，不发Access Token或Refresh Token。旧账号可输入本人能够收信的新邮箱，再完成图片与邮件验证码；即使旧账号无邮箱或示例邮箱不可收信也有恢复入口。新邮箱不能属于其他账号。

补验成功将邮箱、email_verified_at、credential_version+1在同一个数据库事务中更新。用户随后重新登录；补验之前的旧Token不会因为邮箱已验证而恢复有效。

所有账号角色均受后端验证约束。uni-app只承载CUSTOMER补验页面；非学生账号密码正确但未验证时提示前往管理端，返回的受限后台契约可用于后续Vue管理端接入，不在uni-app增加管理功能。当前未实现管理端页面，可用受信任调试工具按下述接口完成验证，不能宣称已有管理界面。

## 新增及调整接口

公开接口经网关加 `/api`。验证码相关调用不携带Bearer，不要求旧账号先登录，也不触发Token续期。

| 接口 | 请求字段 | 成功结果 |
|---|---|---|
| POST /auth/registration/code | email、captchaId、captchaCode | 发送注册用途邮件验证码 |
| POST /auth/registration/verify | email、code（六位字符串） | verificationToken、expiresIn=300 |
| POST /auth/register | username、email、password、emailVerificationToken | 创建已验证CUSTOMER并返回用户号，不自动登录 |
| POST /auth/login | 原有account、password、captchaId、captchaCode | 已验证账号正常发Token；未验证返回emailVerificationRequired=true、emailVerificationToken、emailVerificationExpiresIn=600、roleCode，不含登录Token |
| POST /auth/email-enrollment/code | authorizationToken（登录返回的补验凭据）、email、captchaId、captchaCode | 向新邮箱发送补验用途验证码 |
| POST /auth/email-enrollment/confirm | authorizationToken、email、code | 绑定并验证邮箱，撤销旧凭据版本，之后重新登录 |

受限凭据是64位随机十六进制串，不是JWT，不能用作Authorization Bearer访问业务接口。注册与补验的凭据、验证码及Redis命名空间分别隔离，也不能拿密码找回、已登录换绑的凭据来替代。

内部API归用户服务 `/internal/users/email-ownership/...`，每个请求验证X-Internal-Token。其中enrollment/challenge只供认证服务在正确密码校验之后传入可信内部用户ID与凭据版本，不公开匿名用户ID授权接口。

## 数据模型与身份链

- users.email_verified_at：当前邮箱实际验证成功时间，NULL表示待验证；历史数据全部保持NULL，不根据“有邮箱”推定已验证。
- 新注册最终在user-service消费与邮箱绑定的5分钟一次性授权；注册查重和邮箱唯一键共同防并发重复建号。没有授权不能直接调用用户注册接口绕过认证服务。
- 旧账号补验凭据绑定用户ID、登录密码哈希摘要、原邮箱、角色及凭据版本；期间密码、邮箱、角色或凭据版本变化会使凭据失效。
- 最终补验使用用户行锁、验证码一次性消费和授权CAS消费，数据库邮箱唯一约束处理争抢。事务回滚不会恢复已消费的Redis凭据，失败时重新验证，不冒险恢复授权。
- 已登录换绑仍按原流程验证登录密码、人机及新邮箱OTP；更新邮箱时同步更新验证时间，不把未验证邮箱带入已验证状态。
- common-web.JwtUserIdentity在解析旧会话时实时核对邮箱验证状态；用户服务内部身份解析接口也强制核对。认证服务刷新Token时拒绝未验证账号并删除该刷新凭据。
- 通知WebSocket继续复用共享身份解析，既有会话在校验/推送时会拒绝未验证身份；物理连接清理遵循现有周期，不承诺所有设备立即弹出同一页面。

## 限流、邮件与客户端

邮件验证码10分钟有效，最多5次错误尝试；注册按目标邮箱每60秒1次、10分钟3次，补验按用户每60秒1次、10分钟3次且按目标邮箱限制。认证服务额外按来源IP限制发送与校验；来源解析复用认证流程的网关转发约定，仅用于限流，业务服务应通过受信任网关访问。

补验凭据10分钟有效；验证码即使尚在10分钟内，也不能超过补验凭据期限。注册授权5分钟有效，不因昵称/密码填写耗时而延长。

客户端先验证邮箱再进入注册表单，已验证邮箱不可随意编辑，提供重新验证入口。注册授权与补验授权仅在内存单次交接，密码不保存，离页清理，并拒绝晚到请求恢复凭据。发送成功切换到邮箱验证码表单，不立即重新显示图片验证码；重发需等待60秒并重新做人机验证。

## 迁移与发布顺序（V9已执行，其余发布步骤仍需确认）

1. 确认所有账号都会变为待补验，尤其7个无邮箱、8个示例/测试邮箱的历史账号；准备可收信邮箱及账号密码。不得批量将历史验证时间填为当前时间绕过确认规则。
2. 暂停旧业务入口并备份user_db，执行 docs/database/migrations/user_db/V9__email_verified_at.sql；该ALTER为单次迁移，重复执行前核对字段是否已存在。
3. 用户服务、认证服务、网关以及使用common-web身份解析的全部资源服务都必须加载新代码；user-api调用方同步更新。只更新前端或只更新认证服务不能阻止所有旧会话。
4. 全部服务具备新接口及校验后，再发布本轮用户端Web产物到Nginx。切换期间不应混跑仍允许未验证旧Token的实例。
5. 验证新注册、历史无邮箱补绑、同邮箱并发争抢、旧Access/Refresh Token拒绝、补验后重新登录及原换绑/找回回归。

新增功能没有改变旧用户号、UUID、昵称、角色、余额或历史订单；绑定成功后邮箱登录使用新邮箱。未完成补验的账号将按用户确认规则有意被阻止使用受保护业务，而不是旧账号登录兼容事故。

## 验证记录

已运行：前端邮箱验证/补验交互、注册与会话导航回归、Vue模板/原生CSS约束、Web构建；Redis验证码TTL、错误计数与并发一次消费脚本检查；隔离MySQL验证V9历史默认未验证、真实补验UPDATE只提升一次凭据版本且余额/昵称不变。隔离库已删除。随后实际user_db已执行V9，18个用户保持不变，邮箱已验证数量为0；迁移前备份位于 C:/Users/DELL/.codex/backups/order_system_cloud/email_ownership_20261105_111839/users_before_v9.sql。

已补但未运行Java测试：注册授权与邮箱绑定、凭据一次性/跨用途、邮箱改变不能消费、密码变化失效、已验证账号不能补验、没有授权不能建号、未验证不签发或续期Token、共享身份链拒绝旧会话、人机验证失败不发送邮件。

未验证：Java类型检查/测试执行、真实SMTP投递、真实旧账号登录/补绑、多实例并发及完整服务联调。没有运行Maven或重启服务；V9已执行。
