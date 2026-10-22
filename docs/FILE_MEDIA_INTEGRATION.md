# 头像、窗口和菜品图片接入

## 调用流程

1. 前端选择一张图片，经现有 `POST /api/files/upload` 上传；multipart 字段为 `file`、`bizType`、`bizRef`。格式由文件服务按真实文件头校验，单张不超过 5MB。
2. 上传成功返回 fileKey，再调用业务绑定接口。上传本身不等于业务保存成功；保存失败可复用 fileKey 重试。
3. 业务服务通过文件服务受共享密钥保护的内部元数据接口验证上传者、业务类型、目标 ID 和图片类型，再将 `/api/files/{fileKey}/content` 保存到既有字段。不新增表，不跨服务查询数据库。

| 用途 | bizType | bizRef | 绑定接口（网关 /api 前缀） |
| --- | --- | --- | --- |
| 本人头像 | USER_AVATAR | 不传（上传者取自 JWT） | PUT /users/me/avatar |
| 窗口封面 | WINDOW_IMAGE | windowId | PUT /products/admin/windows/{windowId}/image |
| 菜品图片 | PRODUCT_IMAGE | productId | PUT /products/admin/products/{productId}/image |

绑定体均为 `{ "fileKey": "32位文件键" }`。用户头像只允许更新本人；窗口/菜品图片仅档口负责人和餐厅经理可修改，并复用既有窗口数据范围校验。图片更新仅修改图片列，避免全量更新覆盖名称、价格等字段。窗口封面修改后失效窗口列表和菜单缓存，菜品修改后失效所属窗口菜单缓存。

## 服务地址与内容访问

`ManagedImageClient` 默认访问文件服务 `http://localhost:9102`，可用 `FILE_SERVICE_BASE_URL` 指定地址。内部元数据端点为 `/internal/files/{fileKey}`，使用 `INTERNAL_SCOPE_TOKEN` 校验，不接受客户端提供的远程 URL。公开文件元数据隐藏上传者和业务引用，防止泄露用户内部主键。

网关仅允许匿名 `GET /api/files/*/content`，与文件服务内容读取规则一致；元数据、上传和删除仍需认证。前端把文件服务返回的 localhost 内容地址转换到当前 API_BASE_URL，便于手机访问。

## 前端入口

- 所有登录用户：我的 → 个人资料 → 更换头像。
- 商家：我的 → 窗口与菜品图片，选择所属窗口后维护窗口封面和菜品图。
- 顾客：首页窗口缩略图、菜单窗口封面与菜品缩略图使用文件服务地址；空地址或图片加载失败时自动回退到本地默认图。

默认图片位于 `E:/order_system_cloud_user/static/images/defaults`，来源和许可随目录保存。旧数据不强行批量写入图片列，前端回退即可覆盖既有空值。

## 验证与限制

前端 Node 测试覆盖上传 multipart、令牌续期、取消/超限、绑定失败复用文件、URL 归一化和默认图回退。补充后端元数据验证与管理范围/缓存单元测试源码；按项目约束未执行 Maven、后端测试或服务启停操作。

对正在运行的网关进行只读探测时，匿名图片内容请求仍返回 401，说明运行实例尚未体现此次源码中的放行规则。实际上传、绑定与浏览器图片展示的端到端联调尚未完成。
