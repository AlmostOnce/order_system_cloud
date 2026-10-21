# 注册改为邮箱 + 双验证码（图片 + 邮件）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `superpowers:executing-plans` 按任务完成；每个任务结束后都要接口/实机验证，通过再进入下一任务。

**Goal:** 把注册标识从**手机号整体换成邮箱**（`users.phone` 列删除），并为注册同时加入**图片验证码**（防机器人批量注册）与**邮件验证码**（验证邮箱归属，为后续找回密码铺路），同时补齐注册页服务条款提示与提交链路健壮性。

**Architecture:** 跨四层，严格执行「**备份 → 数据库 → 契约 → 后端 → 网关 → 前端**」顺序：

1. `user_db`：新增 `email` 列与唯一索引，**删除 `phone` 列与 `uk_users_phone`**（不可逆，先备份）；
2. `user-api` / `user-service`：注册与引导链路 `phone → email`，Mapper 查询方法改名；
3. `auth-service`：图片验证码接口、邮件验证码发送接口、注册时双重校验；
4. `gateway-service`：两个新接口加入匿名白名单 + 按 IP 限流；
5. `uni-app x`（`e:\order_system_cloud_user`）：注册页手机号字段替换为邮箱，新增邮件验证码行与图片验证码行。

**Tech Stack:** JDK 21 / Spring Boot 3.5.6 / `spring-boot-starter-mail`（SMTP）/ Redis / Spring Cloud Gateway（WebFlux）/ uni-app x（`.uvue` / `.uts`）。

**Spec（已核实）:** `users` 表现为 `user_id / username / role_code / phone / balance / password_hash / avatar_url / status / version / …`，含 `uk_users_phone`；`RegisterRequest` 为 `username / phone / password`；`UserRegisterCommand`、`UserDO` 均只有 `phone`；`UserMapper` 有 `selectByUsernameOrPhone` 与 `selectByPhone`；`UserBootstrapRunner` 使用 `USER_ADMIN_BOOTSTRAP_PHONE` / `USER_MANAGER_BOOTSTRAP_PHONE` 并校验 11 位手机号正则。**全仓库不存在任何短信/电话验证码实现。**

## 两种验证码的定位（不可互换）

| | 图片验证码 | 邮件验证码 |
|---|---|---|
| 目的 | 人机识别，拦截脚本 | 证明邮箱真实归属 |
| 生成 | 服务端即时出图 | SMTP 发送 6 位数字 |
| 有效期 | 120 秒 | 5 分钟 |
| 消费语义 | **先删后判**，一次性（失败可立即换图） | **成功才删**；失败保留并累计尝试次数（≤5 次） |
| 限流 | 按 IP | 同邮箱 60 秒冷却 + 每日上限 |

**为什么两者消费语义不同**：图片输错可立刻刷新换图，删掉无损失；邮件输错一次就得重发一封，体验很差，因此保留到 TTL 结束、用尝试次数上限防爆破。

## Global Constraints

- 所有逻辑代码（Java / uts / ts）必须写详细中文注释，风格与现有文件一致。
- 后端分层沿用：`controller` → `service` + `service.impl`；DTO 不混用实体。
- **DDL 先确认后执行**：Task 0 的 `ALTER TABLE` 必须先展示给用户确认，确认后才落地。
- **删列前必须备份 `user_db.users`**：Task 1 第一步导出 `users` 全表到 `backup/` 目录。
- **所有验证码答案只存 Redis**：不落库、不进响应体、不写日志；比对忽略大小写。
- **邮件凭据只走环境变量**（`.env` → `MAIL_*`），禁止硬编码、禁止入日志。
- 失败文案不区分「错误」与「过期」，统一为「验证码错误或已过期」，防枚举。
- 契约变更必须**前后端同改**，并回填 `docs/PROJECT_PROGRESS.md`。
- 前端动画只用 `transition` + 状态类切换，禁止 `@keyframes`；弹性出场 `cubic-bezier(0.34, 1.56, 0.64, 1)`，卡片 `cubic-bezier(0.22, 1.3, 0.36, 1)`。
- 前端视觉延续登录页：橙色品牌栏 + 白色圆角表单卡 + 米色底，不引入新配色。
- 改动文件保持 CRLF 行尾；不创建 git 提交、不推送远程。
- Maven 构建命令走 Windows PowerShell（WSL 内无 JDK）；其余 shell 走 WSL。

## Review Focus

- **备份先行**：任何 `DROP COLUMN phone` 操作执行前，必须先有可回滚的导出文件。
- **白名单最小化**：只放行 `GET /api/auth/captcha` 与 `POST /api/auth/email-code`，禁止 `/api/auth/**` 通配。
- 邮件发送失败（SMTP 不通、授权码错、被风控）必须返回**可读业务错误**，不能 500，也不能泄露 SMTP 细节。
- **校验顺序**：图片验证码 → 邮件验证码 → Feign 调 `user-service`，避免无效请求穿透下游。
- `email` 唯一索引允许多个 NULL（历史账号无邮箱），不得设 NOT NULL；新注册由应用层 `@NotBlank` 保证必填。
- 所有「用户名或手机号」类文案、变量名、环境变量必须同步改为「邮箱」，不得残留 `PHONE`。
- 前端任何分支下 `submitting` 与倒计时都必须能复位，不得永久卡住。
- 前端倒计时定时器必须在 `onUnload` 清理。

## 任务总览

| # | 层 | 内容 |
|---|---|---|
| 0 | — | 确认 email 新增 + phone 删除的 DDL（阻塞项） |
| 1 | user_db | 备份 users → 执行 `V4__switch_phone_to_email.sql` |
| 2 | user-api + user-service | 注册与引导链路 `phone → email` |
| 3 | auth-service | 图片验证码接口 |
| 4 | auth-service | 邮件验证码发送接口 + 限流 |
| 5 | auth-service | 注册接口 `phone → email` + 双重校验 |
| 6 | gateway-service | 白名单与刷接口限流 |
| 7 | 前端 | 手机号字段换邮箱 + 邮件验证码行 + 倒计时 |
| 8 | 前端 | 图片验证码行 |
| 9 | 前端 | 服务条款 + `catch` 兜底 + 返回时序 |
| 10 | 全链路 | 接口验证、双端实机、文档回填 |

---

### Task 0/1: 数据库 —— email 替换 phone

**Files:**
- Add `e:\order_system_cloud\docs\database\migrations\user_db\V4__switch_phone_to_email.sql`
- Backup `e:\order_system_cloud\backup\users-before-email-YYYYMMDD.sql`

**待确认 DDL（先展示，确认后再执行）：**

```sql
-- 1) 新增邮箱列：作为注册标识与找回密码凭据。
--    允许 NULL 以兼容历史账号（总经理/管理员引导账号可能没有邮箱），
--    MySQL 唯一索引对多个 NULL 不做冲突判定，历史数据不会互相影响。
ALTER TABLE users
    ADD COLUMN email VARCHAR(100) NULL
        COMMENT '注册邮箱，用作账号标识、邮件验证码与找回密码'
        AFTER username;

ALTER TABLE users
    ADD UNIQUE KEY uk_users_email (email);

-- 2) 删除手机号：注册不再收集手机号（系统从未实现短信验证）。
--    注意：此操作不可逆，执行前必须先备份 users 全表。
ALTER TABLE users
    DROP INDEX uk_users_phone,
    DROP COLUMN phone;
```

- [ ] 展示上述 DDL，取得用户确认。
- [ ] **先备份**：`mysqldump` 导出 `user_db.users` 到 `backup/users-before-email-YYYYMMDD.sql`，确认文件非空。
- [ ] 在本机 Docker MySQL（9095）的 `user_db` 执行迁移，并 `SHOW CREATE TABLE users` 回读确认：`email` 存在、`uk_users_email` 存在、`phone` 与 `uk_users_phone` 已消失。
- [ ] 不改动 `V1~V3` 历史脚本（迁移文件只增不改）。

### Task 2: 契约与用户服务 —— phone 全量改为 email

**Files:**
- Modify `e:\order_system_cloud\user-api\src\main\java\com\hbue\ordering\user\api\dto\UserRegisterCommand.java`
- Modify `e:\order_system_cloud\user-service\src\main\java\com\hbue\ordering\user\model\UserDO.java`
- Modify `e:\order_system_cloud\user-service\src\main\java\com\hbue\ordering\user\mapper\UserMapper.java`
- Modify `e:\order_system_cloud\user-service\src\main\java\com\hbue\ordering\user\service\UserService.java`
- Modify `e:\order_system_cloud\user-service\src\main\java\com\hbue\ordering\user\service\impl\UserServiceImpl.java`
- Modify `e:\order_system_cloud\user-service\src\main\java\com\hbue\ordering\user\bootstrap\UserBootstrapRunner.java`
- Modify `e:\order_system_cloud\.env.example` 与 `.env`
- Modify 相关测试：`UserBootstrapRunnerTest.java` 及注册相关测试

**实现要点（逐项勾选，避免漏改）：**

- [ ] `UserRegisterCommand`：`phone` → `email`（注释改为「注册邮箱」）。
- [ ] `UserDO`：`@TableField("phone") private String phone` → `@TableField("email") private String email`。
- [ ] `UserMapper`：
  - `selectByUsernameOrPhone` → `selectByUsernameOrEmail`，SQL 中 `phone = #{phone}` → `email = #{email}`；
  - `selectByPhone` → `selectByEmail`，SQL 同步改；
  - 方法上的中文注释与 `@Param` 一并更新。
- [ ] `UserService` / `UserServiceImpl`：
  - 注册查重改为「用户名或邮箱」；冲突文案「用户名或手机号已存在」→「用户名或邮箱已存在」；
  - `userDO.setPhone(...)` → `setEmail(...)`；
  - `bootstrapSystemAdmin` / `bootstrapGeneralManager` 的 `phone` 参数改为 `email`，查重文案改为「引导账号的邮箱已被其他账号占用」；
  - 相关 Javadoc 全部更新。
- [ ] `UserBootstrapRunner`：
  - `PHONE_PATTERN` → `EMAIL_PATTERN`（宽松正则，如 `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$`）；
  - 变量名 `adminPhone` / `managerPhone` → `adminEmail` / `managerEmail`；
  - `@Value("${USER_ADMIN_BOOTSTRAP_PHONE:}")` → `${USER_ADMIN_BOOTSTRAP_EMAIL:}`，总经理同理；
  - `requirePresent(prefix, "PHONE", ...)` → `"EMAIL"`。
- [ ] `.env.example` 与 `.env`：`USER_ADMIN_BOOTSTRAP_PHONE` / `USER_MANAGER_BOOTSTRAP_PHONE` → 对应 `_EMAIL`，值换成真实邮箱。
- [ ] `UserBootstrapRunnerTest`：参数名、非法格式用例（改为非法邮箱）同步更新。
- [ ] 全仓 `grep -n "phone\|Phone\|手机号\|PHONE"` 复查 `user-service` / `user-api` 下**零残留**（`product-service` 的商家联系电话 `restaurant.phone` 与本任务无关，保持不动）。

### Task 3: auth-service —— 图片验证码

**Files:**
- Add `...\auth\service\CaptchaService.java`、`...\auth\service\impl\CaptchaServiceImpl.java`
- Add `...\auth\dto\response\CaptchaVO.java`
- Modify `...\auth\controller\AuthController.java`

- [ ] `CaptchaVO`：`captchaId`（UUID 去横线）+ `imageBase64`（含 `data:image/png;base64,` 前缀）。
- [ ] 4 位字符，剔除易混淆字符（`0 O o 1 l I`），比对忽略大小写。
- [ ] Redis key `auth:captcha:{captchaId}`，TTL **120 秒**；**先删后判**（一次性）。
- [ ] 出图默认用 JDK 原生 `BufferedImage` + `Graphics2D`（随机字符 + 旋转 + 干扰线 + 噪点），**不引新依赖**；Spring Boot 默认 `java.awt.headless=true`，无需额外配置。若效果不佳再考虑 `hutool-captcha`（切换前先告知）。
- [ ] 控制器新增 `@GetMapping("/captcha")`，返回 `ApiResponse<CaptchaVO>`。

### Task 4: auth-service —— 邮件验证码发送

**Files:**
- Modify `e:\order_system_cloud\auth-service\pom.xml`（新增 `spring-boot-starter-mail`）
- Add `...\auth\service\EmailCodeService.java`、`...\auth\service\impl\EmailCodeServiceImpl.java`
- Add `...\auth\dto\request\EmailCodeRequest.java`
- Modify `...\auth\controller\AuthController.java`
- Modify `e:\order_system_cloud\auth-service\src\main\resources\application.yml`（`spring.mail.*`）
- Modify `e:\order_system_cloud\.env` 与 `.env.example`（新增 `MAIL_*`）

- [ ] 新增 `POST /auth/email-code`，请求体 `{ email }`，`@NotBlank` + `@Email`。
- [ ] Redis 键规划（全部带 TTL，不落库）：
  - `auth:email-code:{email}` → 6 位数字，TTL **300 秒**
  - `auth:email-code:cooldown:{email}` → 发送冷却，TTL **60 秒**
  - `auth:email-code:attempt:{email}` → 失败次数，TTL 同 300 秒，**上限 5 次**
  - `auth:email-code:quota:{email}:{yyyyMMdd}` → 当日发送计数，TTL 到次日 0 点，**上限 10 封**
- [ ] 命中冷却或超每日上限时返回明确业务错误（如「发送过于频繁，请稍后再试」），不静默丢弃。
- [ ] 邮件内容：纯文本 + 简单 HTML，含验证码与「5 分钟内有效」提示；**不发任何账号信息**。
- [ ] SMTP 配置从环境变量读取，`spring.mail.host/port/username/password` 全部走 `${MAIL_*}`，yml 中不写明文。
- [ ] 发送异常（`MailException`）捕获后转为「验证码发送失败，请稍后重试」，注释说明不暴露 SMTP 细节。
- [ ] 动手写代码前先测 SMTP 端口可达（本机代理 TUN 可能拦截）；不通则改用 587/STARTTLS 或换服务商，并告知用户。

### Task 5: auth-service —— 注册接口 email 替换 phone + 双重校验

**Files:**
- Modify `...\auth\dto\request\RegisterRequest.java`
- Modify `...\auth\service\impl\AuthServiceImpl.java`
- Modify 既有注册相关测试

- [ ] `RegisterRequest`：删除 `phone` 字段及其 `@Pattern`，新增 `email`（`@NotBlank` + `@Email` + `@Size(max = 100)`）。
- [ ] 新增 `emailCode`（`@NotBlank` + `@Pattern("^\\d{6}$")`）、`captchaId`（`@NotBlank`）、`captchaCode`（`@NotBlank` + `@Size(min=4,max=4)`）。
- [ ] `AuthServiceImpl.register(...)` 校验顺序：
  1. `captchaService.verifyOrThrow(captchaId, captchaCode)` —— 先删后判；
  2. `emailCodeService.verifyOrThrow(email, emailCode)` —— 成功才删；失败累计次数，达 5 次后该码作废；
  3. 通过后调 `user-service` 注册（命令带 `email`）。
- [ ] 两种验证码失败文案统一为「验证码错误或已过期」，并通过**错误码区分来源**（图片 / 邮件），供前端决定是否换图。
- [ ] 注释写明为何校验在 Feign 之前。

### Task 6: 网关 —— 白名单与限流

**Files:**
- Modify `e:\order_system_cloud\gateway-service\src\main\java\com\hbue\ordering\gateway\config\GatewaySecurityConfig.java`
- Modify `e:\order_system_cloud\auth-service\src\main\java\com\hbue\ordering\auth\config\SecurityConfig.java`
- Ref `...\gateway\filter\LoginRateLimitGlobalFilter.java`
- 路由无需改动：`/api/auth/**` 通配已覆盖

- [ ] `GatewaySecurityConfig`：新增 `GET /api/auth/captcha` 与 `POST /api/auth/email-code` 两条 `permitAll`。
- [ ] `auth-service` 的 `SecurityConfig` 的 `permitAll` 补入 `/auth/captcha`、`/auth/email-code`（**漏改即 401**）。
- [ ] 复用既有 Redis 限流模式，按 IP 限制：`/api/auth/captcha` 每分钟 10 次、`/api/auth/email-code` 每分钟 3 次。
- [ ] 白名单精确到方法 + 完整路径，禁止通配。

### Task 7: 前端 —— 手机号替换为邮箱 + 邮件验证码

**Files:**
- Modify `e:\order_system_cloud_user\pages\register\register.uvue`
- Modify `e:\order_system_cloud_user\api\auth.uts`
- Modify `e:\order_system_cloud_user\store\auth.uts`

- [ ] **删除**手机号字段的模板块、`phone` / `phoneError` 数据项、`validatePhone()` 函数、聚焦与错误态分支。
- [ ] 在原位置替换为邮箱字段：标签「邮箱」、占位「用于接收验证码与找回账号」、键盘类型 `email`。
- [ ] 新增邮箱校验函数 `validateEmail()`：非空 + 正则 `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`，与后端 `@Email` 保持宽松一致。
- [ ] 表单顺序：用户名 → 邮箱 → 邮箱验证码 → 密码 → 确认密码 → 图片验证码 → 主按钮 → 条款 → 返回登录。
- [ ] 邮箱验证码行：输入框（`maxlength="6"`，数字键盘）+ 右侧「获取验证码」按钮。
  - 点击后校验邮箱 → 调用发送接口 → 成功则进入 **60 秒倒计时**（按钮置灰显示「60s 后重发」）；
  - 倒计时用 `setInterval`，**必须在 `onUnload` 清理**；
  - 发送失败（含频繁）toast 后端文案，不启动倒计时。
- [ ] `api/auth.uts`：`register(username, phone, password)` → `register(username, email, password, emailCode, captchaId, captchaCode)`；新增 `sendEmailCode(email)`。
- [ ] `store/auth.uts`：`register()` 入参同步改为 email 与两个验证码参数。
- [ ] 视觉沿用现有字段样式（聚焦高亮 + 橙色扫出线 + 错误红字），不新增配色。

### Task 8: 前端 —— 图片验证码

**Files:**
- Modify `e:\order_system_cloud_user\pages\register\register.uvue`

- [ ] 图片验证码行：左侧输入框（`maxlength="4"`，占位「图片验证码」）+ 右侧图片（约 96×36，`mode="aspectFit"`），点击整图刷新。
- [ ] `onLoad` 拉取第一张；`@error` 或点击 → 重新拉取；拉取中显示占位，避免空白。
- [ ] 图片 `src` 直接使用返回的 `imageBase64`（已含 data URI 前缀）。
- [ ] 收到「图片验证码」类错误 → **自动换新图 + 清空输入框**（后端已删 key，旧图必然失效）。
- [ ] 注释写明该自动刷新逻辑的原因。

### Task 9: 前端 —— 服务条款、兜底与返回时序

**Files:**
- Modify `e:\order_system_cloud_user\pages\register\register.uvue`
- Ref `e:\order_system_cloud_user\pages\login\login.uvue`（`.options-hint` 的措辞与字号）

- [ ] 表单卡内、主按钮之下、「返回登录」之上插入一行条款提示，文案与登录页一致（「注册即表示同意校园食堂服务条款」），样式对齐 `.options-hint`，不单独做入场动画。
- [ ] 给 `doRegister(...).then(...)` 补 `.catch`：复位 `submitting` 并 toast 通用失败提示。
- [ ] `submitting` 复位统一到所有出口（`then` / `catch`；UTS 支持 `finally` 则收敛为一处）。
- [ ] 成功分支改为「先 `toastSuccess`，延迟约 800ms 再 `uni.navigateBack()`」，延迟期间保持 `submitting` 为 true 防重复点击。
- [ ] 注释说明延迟原因来自平台差异，并注明「双端验证无截断后可改回立即返回」。

### Task 10: 全链路验证与文档回填

**验证（WSL 侧 curl + docker redis-cli 取答案；Maven 走 PowerShell）：**

- [ ] `GET /api/auth/captcha` 匿名可访问，返回可渲染的 base64 PNG。
- [ ] `POST /api/auth/email-code` 匿名可访问，收到邮件；60 秒内重复请求被拒。
- [ ] 图片码正确 + 邮件码正确 → 注册成功，`users.email` 落库，`phone` 列已不存在。
- [ ] 图片码错误 → 失败；同一 `captchaId` 二次提交必失败（一次性生效）。
- [ ] 邮件码错误 → 失败且**码仍保留**，再输入正确仍可通过；连续错 5 次后该码作废。
- [ ] 两次注册使用同一邮箱 → 第二次返回「该邮箱已注册」，非 500。
- [ ] 邮件码过期（>5 分钟）后提交 → 失败。
- [ ] 停掉 SMTP（或填错授权码）→ 返回可读业务错误，非 500。
- [ ] 引导账号：开启 `USER_*_BOOTSTRAP_ENABLED` 后用新 `_EMAIL` 变量跑一次，确认账号创建成功。
- [ ] 服务日志中**搜不到**任何验证码答案与 SMTP 密码。

**前端双端（HBuilderX）：**

- [ ] 微信小程序：注册成功 / 邮箱已占用 / 邮箱格式错 / 两次密码不一致 / 图片码错（自动换图）/ 邮件码错（保留输入）/ 重发倒计时 / 断网恢复。
- [ ] App-Android（3.9+）：入场动画、聚焦高亮与扫出线、密码显隐、图片刷新、倒计时、toast 与返回时序。
- [ ] 页面销毁后确认倒计时定时器已清理。
- [ ] 回归登录页：注册成功后「记住账号」回填生效，返回后密码、密码确认、两种验证码均不残留。

**文档：**

- [ ] `docs/PROJECT_PROGRESS.md` 追加：两个新接口、Redis 键与 TTL 表、两种消费语义、两处白名单位置、契约字段变更（`phone` → `email`，新增 `emailCode` / `captchaId` / `captchaCode`）、`V4` 迁移与备份位置、新增 `MAIL_*` 与 `USER_*_BOOTSTRAP_EMAIL` 环境变量。
- [ ] `docs/database/SERVICE_DATABASE_INVENTORY.md` 更新 `users` 表结构说明（`email` 列、`phone` 列已删除）。
- [ ] `docs/ARCHITECTURE.md` 如需提及用户标识，同步改为邮箱。

---

## 参考（改动示意，非最终代码）

邮件验证码校验（失败保留 + 尝试上限）：

```java
// 与图片验证码不同：邮件输错一次就要重发一封，体验差，
// 因此成功才删除，失败保留到 TTL 结束，用尝试次数上限防爆破。
public void verifyOrThrow(String email, String code) {
    String key = EMAIL_CODE_PREFIX + email;
    String expected = stringRedisTemplate.opsForValue().get(key);
    if (expected == null || !expected.equalsIgnoreCase(code)) {
        Long attempts = stringRedisTemplate.opsForValue().increment(ATTEMPT_PREFIX + email);
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            // 达到上限即作废，避免继续爆破
            stringRedisTemplate.delete(key);
        }
        throw new BusinessException("验证码错误或已过期");
    }
    // 校验通过才消费
    stringRedisTemplate.delete(key);
    stringRedisTemplate.delete(ATTEMPT_PREFIX + email);
}
```

前端倒计时（务必清理定时器）：

```js
startCountdown() {
	this.countdown = 60
	this.timer = setInterval(() => {
		this.countdown--
		if (this.countdown <= 0) {
			clearInterval(this.timer)
			this.timer = null
		}
	}, 1000)
},
onUnload() {
	// 页面销毁必须清理，否则定时器会在页面卸载后继续回调
	if (this.timer != null) {
		clearInterval(this.timer)
		this.timer = null
	}
}
```
