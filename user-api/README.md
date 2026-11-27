# user-api 用户契约模块

> user-service 的 Feign 契约与模型：注册/认证、数据范围、余额账务、邮箱验证、密码找回、聊天身份。不暴露数据库实体。

## Feign 客户端

| 客户端 | 用途 | 降级约定 |
|---|---|---|
| `UserServiceClient` | 注册、密码校验、基础信息、注销撤回令牌签发 | 降级工厂：区分 HTTP 401 与基础设施故障，不伪造用户信息 |
| `UserScopeInternalClient` | 数据范围查询 | 失败返回服务不可用，调用方 fail-closed，不得当空范围放行 |
| `UserBalanceInternalClient` | 余额扣减/退款/销户 | **刻意不配降级**：扣款结果必须按状态码显式判定 |
| `EmailOwnershipClient` / `PasswordRecoveryClient` | 邮箱验证、找回密码三步 | 转发型客户端 |
| `ChatUserClient` | 聊天身份与角色 | — |

## 模型

- 命令：`UserRegisterCommand`、`UserPasswordVerifyCommand`、`UserBalanceDeductCommand`、`BalanceRefundCommand` 等。
- 视图：`UserBasicVO`（含邮箱验证与注销状态）、`UserPublicVO`、`UserScopeVO`、`AccountDeletionCancelTokenVO` 等。

## 使用约定

- 客户端自动注入 `X-Internal-Token`（认证类显式传参）；服务名 `user-service`。
- 被认证、通知、支付、订单、商品、优惠券、推荐服务依赖。
