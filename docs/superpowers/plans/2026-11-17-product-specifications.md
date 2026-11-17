# 菜品规格选择实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持店主管理可选菜品规格、学生选择规格加入购物车，并让核价、库存、订单和历史展示保持一致。

**Architecture:** 规格配置由 product-service 的关系表持有；product-api 提供菜单与权威核价契约。cart-service 以菜品和规范化选择键区分购物车行，order-service 以服务端核价结果保存规格快照，学生端只负责选择和展示。库存、评价、月售与销量仍以菜品为业务维度。

**Tech Stack:** Spring Boot、MyBatis-Plus、MySQL、Redis Lua、RocketMQ 现有订单事件、uni-app x / UTS / UVue。

**Spec:** [2026-11-17-product-specifications-design.md](../specs/2026-11-17-product-specifications-design.md)

## Global Constraints

- 规格组最多 10 个，每组最多 20 个选项；单组单选，可设必选或可选。
- 单价为菜品基础价加所选非负选项加价；价格由商品服务计算。
- 库存、销量榜、月售和评价继续按菜品聚合。
- 商家管理页面不加入 uni-app；通过现有商品管理 API 暴露给独立 Vue 管理端。
- 所有新建或修改的 Java/UTS 代码补充详细中文注释；不运行 Maven、构建或测试。

## Review Focus

- 必选规格缺失、重复选项、跨菜品/跨组的选项 ID：核价整体拒绝，不采用客户端金额。
- 同一菜品的不同选择键：购物车增减、勾选、删除、清理互不串行。
- 老 Redis 条目缺少选择键：按 `base` 读；必选规格后来启用时不得自动选择或下单。
- 同一订单同菜多规格：允许不同规格分行，拒绝相同规格重复行；库存请求按 productId 聚合。
- 商家修改/停用规格：购物车重新核价；无效项保留并阻止下单；已创建订单继续展示快照。

## Task 1: 商品规格模型、管理 API、菜单与核价

**Files:**
- Create: `docs/database/migrations/product_db/V12__create_product_specification_tables.sql`
- Create: product-service 的规格组/选项 DO、Mapper、请求/视图对象、Service 接口与实现、管理 Controller。
- Modify: `product-service` 商品菜单组装、后台管理权限/数据范围校验、菜单缓存版本、商品搜索来源 Outbox 更新。
- Modify: `product-api` 核价请求/响应契约，使一行商品可携带 `selectedOptionIds`，返回规范化 `selectionKey`、核算价格与规格文字。
- Modify: `docs/database/PRODUCT_CATALOG.md`、`docs/API_DOCS.md`，补充字段、权限、选择规则和 Postman 样例。

**Interfaces:** 菜单菜品的 `specificationGroups` 始终为数组；核价行以 `(productId, selectionKey)` 区分；无规格选择键固定为 `base`。管理 PUT 使用完整替换语义。

- [x] 完成非破坏性迁移；组、选项外键与排序/状态索引有中文注释。
- [x] 实现管理 GET/PUT，校验店主数据范围、名称唯一、组/选项上限、非负两位小数加价及必选组含零加价项。
- [x] 菜单批量读取启用规格，规格变更清理菜单缓存并写搜索 Outbox。
- [x] 核价逐行校验商品和选项归属、必选完整性、单组选项数量；服务端计算价格和规范化选择键。
- [x] 静态检查 DTO、Mapper、缓存版本和 Swagger 路径一致。

## Task 2: Redis 购物车规格行

**Files:**
- Modify: `cart-service` 的 CartItem 请求/持久化对象、Controller、Service/Impl、Repository、VO、`cart-mutate.lua`、`cart-cleanup.lua`。
- Modify: `docs/CART_API.md` 与购物车服务内部注释。

**Interfaces:** 变体增减/勾选/删除路径使用 `/items/{productId}/selections/{selectionKey}`；旧无规格路径继续作为 `base` 别名。清理快照包含 productId、selectionKey、quantity、revision。

- [x] 加购前通过商品服务核验选择项，Redis 中按 `windowId:productId[:selectionKey]` 保存。
- [x] Lua 校验 Hash 字段、JSON 选择键和 revision 一致后原子改写；旧 JSON 缺键按 `base` 兼容。
- [x] 购物车查询按规格行批量核价，并返回规格文字、规范化键和服务端价格。
- [x] 清理接口同时核对订单明细和 Redis 选择键，保留先验证所有候选再统一删除的原子行为。
- [x] 更新 Postman 示例，静态核对 Java 字段与 Lua ARGV/JSON 名称一致。

## Task 3: 订单行、幂等、库存与历史快照

**Files:**
- Create: `docs/database/migrations/order_db/V9__add_order_item_specification_snapshot.sql`
- Modify: `order-api` 的已购商品投影；`order-service` 创建请求、订单明细 DO/VO、订单创建/取消与购物车核对服务、库存构造和详情投影。
- Modify: `docs/ORDER_PAGINATION.md` 或相关订单 API 文档及 `docs/database/PRODUCT_CATALOG.md` 的订单快照说明。

**Interfaces:** 订单行接受 `selectedOptionIds`，保存 quote 返回的 `selectionKey` 和 `specificationTextSnapshot`；订单清理投影返回 selectionKey。

- [x] 同菜不同选择键允许分行；重复同键拒绝；幂等摘要将选项 ID 排序后纳入摘要。
- [x] 用 `(productId, selectionKey)` 匹配商品报价，写入最终单价、行金额和规格文字快照。
- [x] 下单扣库存、待支付取消/超时回补库存前按 productId 合并数量，避免库存接口收到重复菜品行。
- [x] 历史订单无规格时返回空文字与 `base`；订单详情仅展示快照，不查当前配置。
- [x] 订单购物车清理校验与评论购买校验对重复 productId 保持正确。

## Task 4: 学生端选择器与规格订单展示

**Files:**
- Modify: `E:/order_system_cloud_user/api/menu.uts`、`api/cart.uts`、`api/order.uts`、`common/checkout.uts`。
- Modify: `E:/order_system_cloud_user/pages/menu/menu.uvue`、`pages/order/confirm.uvue`、`pages/order/detail.uvue`、`DESIGN.md`、`UX-CONTRACT.md`。

**Interfaces:** 菜单规格组提供 `groupId/name/required/options`；加购传选中的 optionId；每个购物车行保存 `selectionKey/specificationText/selectedOptionIds`；订单项使用同一选择数据，展示读取规格文字快照。

- [x] 无规格菜品保留直接加购；有规格菜品通过移动端底部选择面板完成单选后加购。
- [x] 必选组阻止空选；可选组允许空选；提交前展示当前加价后的单价。
- [x] 菜品卡数量按所有规格行汇总；购物车分行展示规格，逐行增减/移除使用 selectionKey。
- [x] 结算本地草稿和幂等重试保存选项 ID；清理请求携带同一选择键和原 revision。
- [x] 订单确认及详情展示规格快照，老订单缺字段时布局不留空白。
- [x] 多图店铺封面点击单张图片可预览完整图片组；单图预览行为保持不变。

## Task 5: 开发库迁移与静态收尾

**Files:**
- Create: `docs/database/seeds/product_specifications_dev.sql`，只为在售 DEV-/DEMO- 演示菜品初始化属性规格。
- Modify: `docs/superpowers/plans/2026-11-17-product-specifications.md` 与 `docs/PROJECT_PROGRESS.md`，记录迁移/剩余管理端页面验收状态。

- [x] 检查开发库目标、已有表/列与重复执行风险，完成临时逻辑备份后执行 product_db V12、order_db V9。
- [x] 执行演示规格初始化并验证可重复执行；69 个菜品、94 个规格组、260 个选项。
- [x] 查询并记录表结构、索引、外键、字段默认值和中文注释；原商品 77 行、订单明细 20 行保持不变。
- [x] 对两仓库执行 `git diff --check`、契约名称静态扫描和 SQL 静态核对；不运行 Maven、构建或测试。
- [x] 记录未执行服务运行验证的范围；店主 Vue 管理页面仍待独立管理端接入。
