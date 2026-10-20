# 注册页收尾与图片验证码 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `superpowers:executing-plans` 按任务完成；每个任务结束后都要实机/接口验证，通过再进入下一任务。

**Goal:** ① 为注册流程加上**图片验证码**（替代短信验证码，防机器批量注册）；② 补齐注册页的服务条款提示与提交链路健壮性；使注册页在「取图 → 校验 → 提交 → 反馈 → 返回」全链路上无卡死、无提示截断、无遗漏告知。

**Architecture:** 跨三层改动，顺序为**后端 → 网关 → 前端**：

- `auth-service`：新增验证码生成接口与校验服务，答案只存 Redis（不落库、不入日志），注册接口契约新增两个字段；
- `gateway-service`：把验证码接口加入匿名白名单，并复用既有登录限流的写法防刷图；
- `uni-app x` 前端（`e:\order_system_cloud_user`）：注册页新增验证码 UI 并接入提交流程，同时完成原有三项收尾。

**Tech Stack:** JDK 21 / Spring Boot 3.5.6 / Redis（Redisson）、Spring Cloud Gateway（WebFlux）、uni-app x（`.uvue` / `.uts`）。

**Spec:** 校验规则基线为 `auth-service` 的 `RegisterRequest`；接口基线为 `AuthController`（`@RequestMapping("/auth")`）。**不采用短信验证码**的理由：短信需接入第三方短信服务，要求企业实名、签名与模板报备，个人学习项目不可行；图片验证码为纯服务端生成，零外部依赖、零备案。

## Global Constraints

- 所有逻辑代码（Java / uts / ts）必须写详细中文注释，风格与现有文件一致。
- 后端分层沿用现有约定：`controller` → `service` + `service.impl`，DTO 不混用实体。
- **验证码答案只存 Redis**：不落库、不进响应体、不写日志；比对时统一按小写。
- **验证码一次性**：无论比对成功还是失败，校验后**立即删除** Redis key，防重放与爆破。
- **后端契约变更必须前后端同改**，并同步回填 `docs/PROJECT_PROGRESS.md`。
- 前端动画只用 `transition` + 状态类切换，禁止 `@keyframes`；弹性出场用 `cubic-bezier(0.34, 1.56, 0.64, 1)`，卡片用 `cubic-bezier(0.22, 1.3, 0.36, 1)`。
- 前端视觉延续登录页语言：橙色品牌顶部栏 + 白色圆角表单卡 + 米色底，不引入新配色。
- 改动文件保持 CRLF 行尾。
- 不创建 git 提交、不推送远程仓库。

## Review Focus

- **白名单最小化**：只放行 `GET /api/auth/captcha` 一个方法+路径，禁止用 `/api/auth/**` 通配放行。
- **失败文案不可区分**「码错」与「已过期」，避免被枚举；前端必须把该文案识别为「需要换图」的信号。
- 验证码答案在任何日志、异常消息、响应体中**都不得出现**。
- 校验顺序：验证码校验必须在 Feign 调用 `user-service` **之前**，避免无效请求穿透到下游。
- 前端 `.catch` 兜底后 `submitting` 必须复位：任何分支下按钮都不得永久停在「提交中…」。
- 校验规则仍是后端约束的子集，不出现「前端放行、后端拒绝」的规则。

## 任务总览

| # | 层 | 内容 |
|---|---|---|
| 1 | auth-service | 验证码生成接口与 Redis 存储 |
| 2 | auth-service | 注册接口接入验证码校验（契约变更） |
| 3 | gateway-service | 白名单放行与刷图限流 |
| 4 | 前端 | 注册页验证码 UI 与提交流程 |
| 5 | 前端 | 补齐服务条款提示 |
| 6 | 前端 | 提交链路健壮性 |
| 7 | 前端 | 成功提示与返回时序 |
| 8 | 全链路 | 后端 + 网关 + 双端实机验证与文档回填 |

---

### Task 1: 后端 —— 图片验证码生成接口与 Redis 存储

**Files:**
- Add `e:\order_system_cloud\auth-service\src\main\java\com\hbue\ordering\auth\service\CaptchaService.java`（接口）
- Add `e:\order_system_cloud\auth-service\src\main\java\com\hbue\ordering\auth\service\impl\CaptchaServiceImpl.java`
- Add `e:\order_system_cloud\auth-service\src\main\java\com\hbue\ordering\auth\dto\response\CaptchaVO.java`
- Modify `...\auth\controller\AuthController.java`（新增 `GET /auth/captcha`）
- Modify `...\auth\service\impl\AuthServiceImpl.java` 的构造注入（若需要）

**实现要点：**

- [ ] 定义接口 `CaptchaService`：`CaptchaVO generate()`、`void verifyOrThrow(String captchaId, String captchaCode)`。
- [ ] `CaptchaVO` 返回两个字段：`captchaId`（UUID 去横线）+ `imageBase64`（**含** `data:image/png;base64,` 前缀，前端可直贴 `<image :src>`，省去拼接）。
- [ ] 答案规则：4 位，字符集剔除易混淆字符（`0 O o 1 l I` 等），比对时统一 `toLowerCase()`。
- [ ] Redis 存储：key `auth:captcha:{captchaId}`，value = 答案小写，**TTL 120 秒**；沿用 `common-redis` 既有封装或 Redisson。
- [ ] 新增控制器方法 `@GetMapping("/captcha")`，返回 `ApiResponse<CaptchaVO>`。
- [ ] **生图方式（默认走 A）**：
  - **A. JDK 原生自绘（默认，零新增依赖）**：`BufferedImage` + `Graphics2D`，随机字符 + 随机旋转 + 干扰线 + 噪点，`ImageIO.write(png)` → Base64。Spring Boot 默认 `java.awt.headless=true`，无需额外配置。
  - B. 备选：根 pom 引入 `cn.hutool:hutool-captcha:5.8.x`，用 `LineCaptcha/CircleCaptcha` 三行出图（若 A 落地效果不佳再切换，切换前先告知）。
- [ ] 注释写明 key 命名与 TTL 的理由（防长驻、防复用）。

### Task 2: 后端 —— 注册接口接入验证码校验

**Files:**
- Modify `e:\order_system_cloud\auth-service\src\main\java\com\hbue\ordering\auth\dto\request\RegisterRequest.java`
- Modify `e:\order_system_cloud\auth-service\src\main\java\com\hbue\ordering\auth\service\impl\AuthServiceImpl.java`
- Modify `e:\order_system_cloud\auth-service\src\test\java\...`（既有注册相关测试需补字段）

**实现要点：**

- [ ] `RegisterRequest` 新增 `captchaId`（`@NotBlank`）与 `captchaCode`（`@NotBlank` + `@Size(min=4,max=4)`），字段中文注释说明用途。
- [ ] `AuthServiceImpl.register(...)` 中，**在 Feign 调用 `user-service` 之前**先调用 `CaptchaService.verifyOrThrow(...)`。
- [ ] `verifyOrThrow` 语义：读 Redis → 缺失或比对失败 → 抛业务异常，文案统一为「验证码错误或已过期」（**不区分**是错还是过期）；随后**无论成败立即删除 key**。
- [ ] 校验规则与前端对齐：4 位、忽略大小写。
- [ ] 确认异常经 `GlobalExceptionHandler` 映射为非 500 的业务错误码。

### Task 3: 网关 —— 白名单放行与刷图限流

**Files:**
- Modify `e:\order_system_cloud\gateway-service\src\main\java\com\hbue\ordering\gateway\config\GatewaySecurityConfig.java`
- Modify `e:\order_system_cloud\auth-service\src\main\java\com\hbue\ordering\auth\config\SecurityConfig.java`
- Ref `e:\order_system_cloud\gateway-service\src\main\java\com\hbue\ordering\gateway\filter\LoginRateLimitGlobalFilter.java`（现成的 IP 限流写法）
- 路由无需改动：网关 `/api/auth/**` 通配已覆盖（见 `docs/nacos/gateway-service.yaml`）

**实现要点：**

- [ ] `GatewaySecurityConfig` 的 `authorizeExchange` 新增 `.pathMatchers(HttpMethod.GET, "/api/auth/captcha").permitAll()`，与既有三条白名单并列，注释说明原因。
- [ ] `auth-service` 的 `SecurityConfig` 的 `permitAll` 列表新增 `/auth/captcha`（**不加就会被 401 拦掉**）。
- [ ] 复用 `LoginRateLimitGlobalFilter` 的 Redis 限流模式，给 `GET /api/auth/captcha` 加**按 IP 限流**（建议每分钟 10 次），防止被脚本刷图。若判断改动过大，可先只做白名单、把限流登记为遗留项并告知。
- [ ] 白名单必须精确到方法+完整路径，禁止 `/api/auth/**` 通配。

### Task 4: 前端 —— 注册页验证码 UI 与提交流程

**Files:**
- Modify `e:\order_system_cloud_user\pages\register\register.uvue`（template + script + style）
- Modify `e:\order_system_cloud_user\api\auth.uts`（新增获取验证码接口）
- Modify `e:\order_system_cloud_user\store\auth.uts`（`register()` 增加两个入参）

**实现要点：**

- [ ] 新增验证码行，位置在**确认密码之后、主按钮之前**：左侧输入框（`maxlength="4"`，占位「图片验证码」）+ 右侧图片（约 96×36，`mode="aspectFit"`）+ 点击整图刷新。
- [ ] `onLoad` 时拉取第一张；图片 `@error` 或用户点击 → 重新拉取；拉取中显示刷新态/占位，避免空白。
- [ ] 图片 src 直接使用返回的 `imageBase64`（已含 data URI 前缀）。
- [ ] 提交前校验验证码非空（4 位由后端兜底，前端只做非空，避免与后端规则分叉）；提交时把 `captchaId` + `captchaCode` 一并送出。
- [ ] **关键**：收到「验证码错误或已过期」时，**自动换一张新图 + 清空输入框**（后端已删 key，旧图必然失效），并保持验证码行可见；注释写明原因。
- [ ] 视觉与交互沿用现有字段的空态/错误态样式：聚焦高亮 + 橙色扫出线、错误红字；不新增配色。
- [ ] 提交成功后原有「记住账号」回填逻辑不受影响。

### Task 5: 前端 —— 补齐服务条款提示

**Files:**
- Modify `e:\order_system_cloud_user\pages\register\register.uvue`（template + style）
- Ref `e:\order_system_cloud_user\pages\login\login.uvue`（现有 `.options-hint` 的措辞与字号）

- [ ] 在表单卡内、主按钮之下、「返回登录」之上插入一行条款提示 text，文案与登录页保持一致（「注册即表示同意校园食堂服务条款」）。
- [ ] 样式对齐登录页 `.options-hint` 的字号与颜色，类名沿用注册页命名风格（如 `.terms-hint`），必要时补 `margin-top` 拉开与按钮的距离。
- [ ] 确认新增元素不单独做入场动画：它随卡片整体淡入出现，不额外增加 `transition`。

### Task 6: 前端 —— 提交链路健壮性

**Files:**
- Modify `e:\order_system_cloud_user\pages\register\register.uvue`（`methods.submit`）

- [ ] 给 `doRegister(...).then(...)` 补 `.catch`：捕获异常时复位 `submitting` 并 toast 通用失败提示。
- [ ] 把 `submitting` 的复位统一到所有出口（`then` 与 `catch` 各复位一次；若 UTS 支持 `finally` 则收敛为一处），确保不存在卡在忙碌态的路径。
- [ ] 注释说明这是防御性兜底：`store/auth.uts` 的 `register()` 当前以 `Promise<string>` 收敛、不会 reject，故现状不会触发。

### Task 7: 前端 —— 成功提示与返回时序

**Files:**
- Modify `e:\order_system_cloud_user\pages\register\register.uvue`（`methods.submit` 成功分支）

- [ ] 成功分支改为「先 `toastSuccess`，延迟约 800ms 后再 `uni.navigateBack()`」，避免部分平台随页面销毁把 toast 截断。
- [ ] 延迟期间保持 `submitting` 为 true（按钮维持忙碌态），避免用户重复点击触发第二次请求。
- [ ] 注释写明延迟原因来自平台差异，并标注「若确认双端均无截断可回调为立即返回」。

### Task 8: 全链路验证与文档回填

**Files:**
- Verify 上述所有改动
- Modify `e:\order_system_cloud\docs\PROJECT_PROGRESS.md`

**后端 / 网关接口验证（用 WSL 侧 curl + docker redis-cli 取答案）：**

- [ ] `GET /api/auth/captcha` 匿名可访问，返回 `captchaId` + 可渲染的 base64 PNG。
- [ ] 用 Redis 中的答案（小写）提交注册 → **成功**。
- [ ] 用错误答案提交 → 失败，文案为「验证码错误或已过期」。
- [ ] **同一 `captchaId` 第二次提交必失败**（证明一次性删除生效）。
- [ ] 等 TTL 过期后提交 → 失败；Redis 中该 key 已不存在。
- [ ] 未带 `captchaId` / `captchaCode` 提交 → 参数字段校验失败，非 500。
- [ ] 确认服务日志中**搜不到**验证码答案。

**前端双端验证（HBuilderX）：**

- [ ] 微信小程序：注册成功 / 用户名已占用 / 手机号格式错误 / 两次密码不一致 / 验证码错误（自动换图 + 清空）/ 点击刷新换图 / 停网关后断网恢复。
- [ ] App-Android（3.9+）：复核入场动画、聚焦高亮与橙色扫出线、密码显隐、验证码图刷新、toast 与返回时序。
- [ ] 回归登录页：注册成功后「记住账号」回填生效，返回后密码与确认密码、验证码均不残留。

**文档：**

- [ ] 在 `docs/PROJECT_PROGRESS.md` 追加本次完成条目：验证码接口路径与方法、Redis key 与 TTL、一次性语义、两处白名单改动位置、前后端契约变更字段。
- [ ] 若最终启用 IP 限流，登记限流阈值与过滤器类名。

---

## 参考（改动示意，非最终代码）

前端提交链路目标形态（含验证码与 Task 6/7 的兜底、时序）：

```js
doRegister(
	this.username.trim(),
	this.phone.trim(),
	this.password,
	this.captchaId,
	this.captchaCode.trim()
)
	.then((message: string) => {
		// 成功：延迟返回，保证 toast 完整展示
		if (message.length == 0) {
			setTimeout(() => {
				uni.navigateBack()
			}, 800)
			return
		}
		// 失败：复位忙碌态后再提示
		this.submitting = false
		toast(message)
		// 验证码类错误：后端已删 key，旧图必然失效，必须换新图并清空输入
		if (message.indexOf('验证码') >= 0) {
			this.captchaCode = ''
			this.refreshCaptcha()
		}
	})
	.catch(() => {
		// 防御性兜底：当前 store 层不会 reject，此处仅防止未来改动导致按钮卡死
		this.submitting = false
		toast('注册失败，请稍后重试')
	})
```

后端校验语义示意：

```java
// 校验后立即删除 key：无论成败都消费掉，防止同一验证码被重复使用
public void verifyOrThrow(String captchaId, String captchaCode) {
    String key = CAPTCHA_KEY_PREFIX + captchaId;
    String expected = stringRedisTemplate.opsForValue().get(key);
    // 先删再判，避免并发下的双重消费
    stringRedisTemplate.delete(key);
    if (expected == null || !expected.equalsIgnoreCase(captchaCode)) {
        // 刻意不区分「错误」与「过期」，防止被枚举探测
        throw new BusinessException("验证码错误或已过期");
    }
}
```
