# 接口文档（OpenAPI / Swagger）使用说明

本项目已集成 springdoc-openapi（`2.9.1`），接口文档与代码同源：改动 Controller 后文档自动更新，不会再与代码脱节。本文说明入口、用法与开关。

## 一、访问入口

| 入口         | 地址                                          | 说明                                                             |
|--------------|-----------------------------------------------|------------------------------------------------------------------|
| 网关聚合入口 | `http://localhost:9090/swagger-ui/index.html` | 一个页面查看已配置的服务，右上角下拉框切换分组                   |
| 认证服务     | `http://localhost:9094/swagger-ui/index.html` | 直连查看                                                         |
| 订单服务     | `http://localhost:9091/swagger-ui/index.html` | 直连查看                                                         |
| 用户服务     | `http://localhost:9092/swagger-ui/index.html` | 直连查看                                                         |
| 商品服务     | `http://localhost:9093/swagger-ui/index.html` | 直连查看                                                         |
| 支付服务     | `http://localhost:9101/swagger-ui/index.html` | 直连查看                                                         |
| 购物车服务   | `http://localhost:9103/swagger-ui/index.html` | 七个业务接口：增删改查、核价与下单后快照清理，示例见 CART_API.md |
| 优惠券服务   | `http://localhost:9112/swagger-ui/index.html` | 学生领券、我的券、平台券管理；代码端口配置完成后可直连           |

2026-10-17 新增购物车 Swagger 分组。本优惠券实现新增 `/api/coupons/v3/api-docs` 文档路由和聚合项；仓库 Nacos
配置尚需按当前环境发布，旧静态 Postman 导出不包含优惠券服务，联调时请从运行中的网关实时导出。

> 无论从聚合页还是直连页点 "Try it out"，请求都会发往网关 `http://localhost:9090`（由 `springdoc.gateway-base-url` +
> `springdoc.gateway-path` 决定），因此调用前需保证网关已启动。

## 二、鉴权

- 需登录的业务接口都要求请求头 `Authorization: Bearer <Access Token>`。
- 在 Swagger UI 右上角点 **Authorize**，填入登录接口返回的 Access Token（ **不要**手写 `Bearer` 前缀，UI 会自动补）。
- 免登录接口：`/api/auth/login`、`/api/auth/register`、`/api/auth/refresh`。

## 三、导入 Postman

仓库已放好可直接导入的文件（Postman 原生支持导入 OpenAPI 3）：

- [order-system-cloud.gateway.openapi.json](file:///e:/order_system_cloud/docs/postman/order-system-cloud.gateway.openapi.json)：
  **推荐**。5 个服务合并为一份，`baseUrl` 已设为 `http://localhost:9090`，接口按「服务 · 分组」归档，覆盖全部经网关可访问的对外接口。
-
单服务文件（[auth-service](file:///e:/order_system_cloud/docs/postman/auth-service.openapi.json) / [user-service](file:///e:/order_system_cloud/docs/postman/user-service.openapi.json) / [order-service](file:///e:/order_system_cloud/docs/postman/order-service.openapi.json) / [product-service](file:///e:/order_system_cloud/docs/postman/product-service.openapi.json) / [payment-service](file:///e:/order_system_cloud/docs/postman/payment-service.openapi.json)
的 `.openapi.json`）：保留该服务 **全部**接口（含 `/internal/**`、`/mock/**`），供直连其端口调试。

导入方式：Postman → **Import** → 选择上述 JSON。导入后先调「认证服务 · 认证接口」的登录接口获取 Access Token，填到集合
Authorization 的 Bearer 中。

也可以实时导出：打开 `http://localhost:9090/api/{svc}/v3/api-docs`（`{svc}` 取 `auth` / `users` / `orders` / `products` /
`payments` / `coupons`）保存 JSON；或直连 `http://localhost:{port}/v3/api-docs`。

> 地址约定：网关路由使用 `StripPrefix=1`，只剥离 `/api` 一段，因此对外地址 = `http://localhost:9090/api` + 各服务自身路径。例如学生优惠券的
> `/coupons/claimable` 对外为 `http://localhost:9090/api/coupons/claimable`。

优惠券学生接口仅允许 CUSTOMER；`/coupons/admin/templates/**` 还要求 GENERAL_MANAGER 及平台数据范围。`/internal/coupons/**`
使用服务间共享令牌，不配置网关业务路由。

## 四、开关

文档能力默认开启。生产环境通过环境变量关闭：

```bash
SWAGGER_ENABLED=false
```

关闭后 `/v3/api-docs` 与 `/swagger-ui/**` 返回 404。该开关对各业务服务与网关同时生效，无需改代码。

## 五、为什么 `/internal/**`、`/mock/**` 不在网关文档里

`/internal/**`（服务间内部调用）与 `/mock/**`（模拟收银台，仅 dev）只允许直连对应服务端口访问：网关 **没有**
为它们配置路由，聚合文档的分组里也不含它们。

需要查看或调试时：

- 直连服务端口，如 `http://localhost:9101/swagger-ui/index.html`，可看到"内部接口（服务间调用）"分组；
- 内部接口还需携带共享密钥请求头 `X-Internal-Token`。

## 六、实现位置

-
共享文档配置：[OpenApiDocumentationConfig.java](file:///e:/order_system_cloud/common-web/src/main/java/com/hbue/ordering/common/web/docs/OpenApiDocumentationConfig.java)
- 网关文档路由：Nacos 配置 `gateway-service.yaml`
  （仓库镜像 [gateway-service.yaml](file:///e:/order_system_cloud/docs/nacos/gateway-service.yaml)）
- 网关聚合列表：[application.yml](file:///e:/order_system_cloud/gateway-service/src/main/resources/application.yml) 的
  `springdoc.swagger-ui.urls`
- 版本管理：[pom.xml](file:///e:/order_system_cloud/pom.xml) 的 `springdoc-openapi-bom`

## 菜品规格管理与核价（2026-11-17）

规格由独立Vue管理端消费，uni-app只做学生选择。以下路径为商品服务路径；经Gateway调用管理接口时在前面加 `/api`。

| 方法与路径                                                | 权限及行为                                                                   |
|-----------------------------------------------------------|------------------------------------------------------------------------------|
| `GET /products/admin/products/{productId}/specifications` | `STALL_MANAGER/RESTAURANT_MANAGER` JWT；校验所属窗口数据范围，返回启用组数组 |
| `PUT /products/admin/products/{productId}/specifications` | 同上；请求包含完整 `groups` 数组，省略的原配置软停用，空数组清除全部         |
| `GET /products/windows/{windowId}/menu`                   | 原学生菜单权限；每个菜品新增 `specificationGroups` 数组，无规格为 `[]`       |
| `POST /internal/products/quote`                           | 沿用CUSTOMER JWT透传鉴权；支持同菜多规格逐行核价，不接受客户端金额           |

管理PUT示例（Postman的Bearer Token使用有该窗口权限的店主/餐厅经理JWT）：

```http
PUT {{gatewayBaseUrl}}/api/products/admin/products/101/specifications
Authorization: Bearer {{managerAccessToken}}
Content-Type: application/json

{
  "groups": [
    {
      "name": "份量",
      "required": true,
      "sortOrder": 0,
      "options": [
        { "name": "标准份", "additionalPrice": 0.00, "sortOrder": 0 },
        { "name": "大份", "additionalPrice": 3.00, "sortOrder": 1 }
      ]
    }
  ]
}
```

GET/PUT成功时 `data` 为如下数组（示例编号以实际返回值为准）；编辑时必须带回已有的 `groupId` 与 `optionId`
，无编号代表新建，不接受跨商品或跨组编号：

```json
[
  {
    "groupId": 11,
    "name": "份量",
    "required": true,
    "sortOrder": 0,
    "options": [
      { "optionId": 21, "name": "标准份", "additionalPrice": 0.00, "sortOrder": 0 },
      { "optionId": 22, "name": "大份", "additionalPrice": 3.00, "sortOrder": 1 }
    ]
  }
]
```

组数最多10，每组1至20项；名称非空且最多50字符，同菜组名、同组选项名唯一。排序非负，选项加价非负、最多8位整数与2位小数；必选组至少含一个零加价选项。校验失败返回400，启用名称数据库唯一键冲突返回409，数据范围越权返回403。清除配置使用
`{ "groups": [] }`，不物理删除记录。

内部核价Postman请求体示例（请求头使用原调用链透传的CUSTOMER Bearer JWT；该路径不经Gateway公开）：

```json
{
  "windowId": 3,
  "items": [
    { "productId": 101, "selectedOptionIds": [22] },
    { "productId": 101, "selectedOptionIds": [21] }
  ]
}
```

单行响应含 `productId/productCode/productName/price/selectionKey/selectedOptionIds/specificationText`。例如基础价12元、大份加3元返回
`price:15.00`、`selectionKey:"22"`、`selectedOptionIds:[22]`、`specificationText:"份量：大份"`。无选择返回 `base/[]/""`
。同菜不同组合允许分行，同菜同组合重复、重复选项、跨菜品选项、已停用选项、同组选两项和缺失必选组均返回400；规格错误整体拒绝，不自动代选。窗口停用返回
`windowActive:false`；不可售菜品沿用原核价协议，从 `products` 结果中省略。

兼容旧请求 `{ "windowId": 3, "productIds": [101] }`
，其语义为无规格选择；新增必选规格后旧请求会因缺少选择被拒绝。新旧字段只能提交一个非空列表，每次最多100行，选择ID最多10个，服务端排序ID后用短横线生成稳定键。所有金额来自商品服务数据库，菜单只供预览，订单应保存规格文字和价格快照。
