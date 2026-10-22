# 用户公开 UUID 与内部主键隔离

## 身份规则

- `users.user_id` 继续做内部主键，订单、余额、库存操作、权限范围和文件上传者关联均不改主键。
- `users.user_uuid` 为稳定随机 UUID v4，CHAR(36)、非空、ASCII 二进制排序、唯一索引。V4 迁移给旧账号回填，数据库随机默认表达式覆盖旧写入程序；新注册和引导账号由 Java UUID.randomUUID() 生成。
- Access Token 原始签名载荷的 `sub` 只使用公开 UUID，不加入内部 ID claim。旧数字 subject 令牌由业务资源服务器拒绝，需要重新登录。
- 验签、issuer/有效期与 Redis 黑名单通过后，`JwtUserIdentity` 才解析 UUID。user-service 本地查索引；其他 Servlet 业务服务使用共享密钥调用 `/internal/users/identities/{userUuid}`。解析失败关闭访问。
- 服务端认证主体视图将 sub 转为内部主键，供既有业务逻辑使用，并携带 user_uuid。原始 tokenValue 保持原签名 UUID 令牌，不重新签发内部 ID 令牌。

## 对外接口

| 接口 | 公开标识 |
| --- | --- |
| 注册响应 | UserPublicVO：userUuid、username、roleCode |
| GET /users/me/profile | userUuid，不包含 userId |
| 角色、状态与数据范围管理 | 路径目标用户使用 userUuid，角色响应 UserPublicVO |
| 订单详情与订单列表 | 不序列化内部 userId |
| 文件公开元数据 | 不序列化 uploaderId、bizRef |
| 头像上传 | bizType=USER_AVATAR，不传 bizRef；上传者由 JWT 取内部 ID |

原 `/users/{userId}` 数字查询已删除。用户基础信息迁移到 `/internal/users/{userId}/basic`，使用 `X-Internal-Token` 校验；服务间认证和注册也校验该凭据，网关拒绝直接 POST /api/users 与 /api/users/authenticate。认证服务的公开 /auth/register 与 /auth/login 保持原路径。

用户管理 URL 中 `{userUuid}` 是随机 UUID，不再接受数字。窗口、菜品、订单本身的标识本次不调整。

## 文件归属

`ManagedImageClient` 改调文件服务 `/internal/files/{fileKey}`，验证共享密钥，避免公开元数据响应泄露上传者内部 ID。头像要求 bizRef 为空，窗口/菜品仍验证对应资源 ID；历史头像地址不变。

## 配置与兼容

- `IDENTITY_USER_SERVICE_BASE_URL`：各业务服务访问用户身份内部接口，默认 http://localhost:9092。
- `FILE_SERVICE_BASE_URL`：头像/商品图片绑定时访问文件内部元数据，默认 http://localhost:9102。
- `INTERNAL_SCOPE_TOKEN`：各服务一致，复用已有内部凭据；UserServiceClient 专用 Feign 配置自动附加该头。
- 需要使用本次多服务代码的一致版本，不能只切换认证服务而保留旧的业务 JWT 解码规则。
- 前端用户身份和待确认操作缓存命名空间改为 UUID。旧数字命名的本地待确认缓存不猜测归属、不删除；如存在旧待确认操作，先从服务端订单/余额记录核对状态，不能跨用户搬迁缓存。

## 数据库与验证记录

已在本地 user_db 执行 V4 迁移，10 个账号都有唯一 UUID v4，无空值。迁移前后用户数量、内部主键汇总和余额汇总一致。仅添加标识列和唯一索引，不重建业务关系。

前端 Node 测试验证 UUID 字符串解析、拒绝旧数字令牌、缓存隔离、头像不发送内部 ID，以及现有订单/刷新逻辑。后端增加身份转换、公开序列化、文件绑定边界等单元测试源码；按项目约束未执行 Maven、后端测试、编译或服务启停操作。当前运行实例的端到端行为未验证。
