# common-web Web 横切模块

> Servlet 业务服务的 Web 横切能力：全局异常处理、JWT 身份转换、内部令牌校验、图片归属校验、管理审计、OpenAPI 共享配置。网关（WebFlux）不使用本模块。

## 关键类

| 类 | 职责 |
|---|---|
| `handler/GlobalExceptionHandler` | 全局异常：BusinessException 按错误码映射 HTTP 状态并返回统一响应；覆盖参数校验、越权等 |
| `security/JwtUserIdentity` | 令牌 sub（UUID）→ 内部用户 ID 转换：本地 SPI 或远程身份接口，校验凭据版本防旧令牌 |
| `security/PublicUserIdentityLookup` | 身份本地查询 SPI（仅用户服务实现） |
| `security/InternalScopeTokenVerifier` | 内部令牌校验：常量时间比较 `X-Internal-Token`；未配置一律拒绝 |
| `media/ManagedImageClient` | 调文件服务内部接口校验图片归属（上传人/类型/业务引用），不跨库读文件表 |
| `media/FileReferences` / `ImageFileRequest` | 文件键规范化、展示 URL 派生、请求校验 |
| `audit/ManagementAuditLogger` | 管理操作审计日志（固定字段、只追加、独立 AUDIT 通道） |
| `docs/OpenApiDocumentationConfig` | springdoc 共享配置：统一信息、JWT Bearer 安全方案、网关前缀 |

### OpenAPI 共享配置（OpenApiDocumentationConfig）

- 统一标题/描述/版本信息 + JWT Bearer 安全方案 + 显式设置 Server 为网关前缀（统一 `/api`，避免直连页产生重复路径前缀的空地址）。
- springdoc 以 `provided` 作用域引入，不向下游服务传递。
- 文档开关 `SWAGGER_ENABLED` 关闭后文档端点返回 404，网关与业务服务同时生效。

## 使用约定

- 除网关外全部 15 个业务服务依赖本模块。
- 服务间内部接口统一 `X-Internal-Token` 头 + `internal.scope.token` 配置，各服务取值必须一致。
- 对外令牌 sub 为 UUID，业务代码一律使用转换后的内部用户 ID。
