# common-core 基础契约模块

> 零第三方依赖的纯 Java 基础模块：错误码契约、业务异常、系统角色与数据范围模型。

## 模块定位

只承载跨服务共享的纯 Java 类型，不引入任何框架依赖；被 common-response、common-web 及文件、库存、优惠券等服务直接引用。

## 关键类

| 类 | 职责 |
|---|---|
| `error/ErrorCode` | 统一错误码接口（code/message），异常处理不依赖具体枚举 |
| `error/CommonErrorCode` | 通用错误码：COMMON_400/401/403/404/409/429/500/503 与成功码 COMMON_000 |
| `exception/BusinessException` | 携带错误码的业务异常，由 Web 边界转统一响应 |
| `role/RoleCode` | 五级角色枚举（学生/摊主/餐厅经理/总经理/系统管理员） |
| `scope/DataScopeService` | 数据范围判定接口（窗口/餐厅/平台） |
| `scope/DataScopeType` / `UserScopeSnapshot` | 范围类型与只读快照；用户服务是唯一事实来源 |

## 使用约定

- 错误码组织：通用码在 `CommonErrorCode`，业务专属码由各模块自建枚举（如 `StockErrorCode`，形如 `STOCK_409_01`）。
- 跨服务可复用码放 CommonErrorCode，业务码不外溢。
