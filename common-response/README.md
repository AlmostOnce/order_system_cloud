# common-response 统一响应模块

> 统一 HTTP/Feign 响应体契约：仅依赖 common-core 与 jackson-annotations，刻意不引 Web 框架，网关（WebFlux）与业务服务（Servlet）共用。

## 关键类

| 类 | 职责 |
|---|---|
| `ApiResponse<T>` | 唯一类：`{code, message, data}`；`success(data)` 用 COMMON_000；`failure(ErrorCode[, message])` 不携带业务数据 |

## 使用约定

- 所有 REST 接口与 Feign 返回体必须是 `ApiResponse`；成功码固定 `COMMON_000`，失败时 `data` 为空。
- HTTP 状态码由各服务的 Web 边界与安全边界决定，本模块不参与。
