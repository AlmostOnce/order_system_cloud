# 接口文档（OpenAPI / Swagger）使用说明

本项目已集成 springdoc-openapi（`2.9.1`），接口文档与代码同源：改动 Controller 后文档自动更新，不会再与代码脱节。本文说明入口、用法与开关。

## 一、访问入口

| 入口 | 地址 | 说明 |
|---|---|---|
| 网关聚合入口 | `http://localhost:9090/swagger-ui/index.html` | 一个页面查看全部 5 个服务，右上角下拉框切换分组 |
| 认证服务 | `http://localhost:9094/swagger-ui/index.html` | 直连查看 |
| 订单服务 | `http://localhost:9091/swagger-ui/index.html` | 直连查看 |
| 用户服务 | `http://localhost:9092/swagger-ui/index.html` | 直连查看 |
| 商品服务 | `http://localhost:9093/swagger-ui/index.html` | 直连查看 |
| 支付服务 | `http://localhost:9101/swagger-ui/index.html` | 直连查看 |

> 无论从聚合页还是直连页点 "Try it out"，请求都会发往网关 `http://localhost:9090`（由 `springdoc.gateway-base-url` + `springdoc.gateway-path` 决定），因此调用前需保证网关已启动。

## 二、鉴权

- 需登录的业务接口都要求请求头 `Authorization: Bearer <Access Token>`。
- 在 Swagger UI 右上角点 **Authorize**，填入登录接口返回的 Access Token（**不要**手写 `Bearer` 前缀，UI 会自动补）。
- 免登录接口：`/api/auth/login`、`/api/auth/register`、`/api/auth/refresh`。

## 三、导入 Postman

仓库已放好可直接导入的文件（Postman 原生支持导入 OpenAPI 3）：

- [order-system-cloud.gateway.openapi.json](file:///e:/order_system_cloud/docs/postman/order-system-cloud.gateway.openapi.json)：**推荐**。5 个服务合并为一份，`baseUrl` 已设为 `http://localhost:9090`，接口按「服务 · 分组」归档，覆盖全部经网关可访问的对外接口。
- 单服务文件（[auth-service](file:///e:/order_system_cloud/docs/postman/auth-service.openapi.json) / [user-service](file:///e:/order_system_cloud/docs/postman/user-service.openapi.json) / [order-service](file:///e:/order_system_cloud/docs/postman/order-service.openapi.json) / [product-service](file:///e:/order_system_cloud/docs/postman/product-service.openapi.json) / [payment-service](file:///e:/order_system_cloud/docs/postman/payment-service.openapi.json) 的 `.openapi.json`）：保留该服务**全部**接口（含 `/internal/**`、`/mock/**`），供直连其端口调试。

导入方式：Postman → **Import** → 选择上述 JSON。导入后先调「认证服务 · 认证接口」的登录接口获取 Access Token，填到集合 Authorization 的 Bearer 中。

也可以实时导出：打开 `http://localhost:9090/api/{svc}/v3/api-docs`（`{svc}` 取 `auth` / `users` / `orders` / `products` / `payments`）保存 JSON；或直连 `http://localhost:{port}/v3/api-docs`。

> 地址约定：网关路由使用 `StripPrefix=1`，只剥离 `/api` 一段，因此对外地址 = `http://localhost:9090/api` + 各服务自身路径。例如认证服务的 `/auth/login` 对外即 `http://localhost:9090/api/auth/login`。

## 四、开关

文档能力默认开启。生产环境通过环境变量关闭：

```bash
SWAGGER_ENABLED=false
```

关闭后 `/v3/api-docs` 与 `/swagger-ui/**` 返回 404。该开关对各业务服务与网关同时生效，无需改代码。

## 五、为什么 `/internal/**`、`/mock/**` 不在网关文档里

`/internal/**`（服务间内部调用）与 `/mock/**`（模拟收银台，仅 dev）只允许直连对应服务端口访问：网关**没有**为它们配置路由，聚合文档的分组里也不含它们。

需要查看或调试时：

- 直连服务端口，如 `http://localhost:9101/swagger-ui/index.html`，可看到"内部接口（服务间调用）"分组；
- 内部接口还需携带共享密钥请求头 `X-Internal-Token`。

## 六、实现位置

- 共享文档配置：[OpenApiDocumentationConfig.java](file:///e:/order_system_cloud/common-web/src/main/java/com/hbue/ordering/common/web/docs/OpenApiDocumentationConfig.java)
- 网关文档路由：Nacos 配置 `gateway-service.yaml`（仓库镜像 [gateway-service.yaml](file:///e:/order_system_cloud/docs/nacos/gateway-service.yaml)）
- 网关聚合列表：[application.yml](file:///e:/order_system_cloud/gateway-service/src/main/resources/application.yml) 的 `springdoc.swagger-ui.urls`
- 版本管理：[pom.xml](file:///e:/order_system_cloud/pom.xml) 的 `springdoc-openapi-bom`
