# 用户号与邮箱登录

- 用户号：服务端分配的 10 位随机数字，以字符串传递，永久不变；唯一索引及分配重试防止冲突。
- UUID：继续用于公开身份和令牌 subject；内部主键和所有业务关联保持不变。
- username：展示昵称，保留现有唯一约束，不再用于登录。
- 邮箱：新注册必填，去除两端空白并转小写，以唯一索引约束；未接入邮箱所有权验证或邮件找回功能。
- 手机号：从注册、公开资料、登录流程和用户表结构中移除；V6 删除 phone 列及对应唯一索引。

## 接口

POST /api/auth/register：`{ "username": "校园用户", "email": "user@example.com", "password": "..." }`。
响应 data 增加 userNo；注册成功页展示用户号、复制入口和前往登录按钮，不自动登录。

POST /api/auth/login：`{ "account": "用户号或邮箱", "password": "...", "captchaId": "...", "captchaCode": "..." }`。
验证码、密码哈希校验、账号状态、令牌签发与失败限流继续生效；错误信息统一，不泄露账户是否存在。

GET /api/users/me/profile：包含 userNo、email，去除 phone。注册及个人资料响应均不暴露内部 userId。

## 数据迁移及发布

1. 停止注册写入，备份 user_db.users；在 user_db 执行 V5__add_user_number_and_email_login.sql。
2. 脚本为旧用户分配用户号并规范化邮箱；发现重复邮箱会明确中止，需先确认真实归属，禁止自动合并账号。
3. 同步发布 user-api、user-service、auth-service 及前端。旧版注册服务未写入 user_no，不可继续接受注册。
4. 无邮箱的旧用户使用补发的用户号。可在数据库工具查看 username、user_no、email 的映射，不伪造历史邮箱。
5. 2026-10-23 已对本机 user_db 执行迁移：10 个用户全部获得唯一的 10 位用户号；内部 ID、UUID、密码哈希、余额、角色及时间字段逐项比对保持不变。8 个缺少邮箱的旧账号继续保持空邮箱。
6. 迁移前完整用户表备份与校验清单位于 C:/Users/DELL/.codex/backups/order_system_cloud/user_number_20261023_115445/，未提交用户数据到仓库。后端未构建或重启，新登录与注册逻辑仍需同步发布。

## 验证范围

前端 test-auth-account 覆盖两种账号请求、邮箱注册、邮箱校验、注册用户号展示、记住账号和密码清空。
后端新增 UserAccountLoginTest，覆盖用户号/规范化邮箱认证、错误密码及注册分配；按项目约束未运行 Maven 或后端测试。

## 手机号列删除记录

2026-10-23 已在本机 user_db 执行 V6__remove_user_phone.sql，删除 users.phone 及 uk_users_phone。删除前完整备份位于 C:/Users/DELL/.codex/backups/order_system_cloud/remove_phone_20261023_115928/；10 个用户的全部其余字段逐项比对无变化。历史迁移文件保留原内容，新建数据库需依序执行至 V6。
