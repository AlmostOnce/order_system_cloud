# file-service 文件服务

> 统一接收图片上传、校验类型与用途、保存文件元数据并提供受控访问。默认端口 `9102`，数据库 `file_db`，当前存储实现为本地磁盘。

## 职责与边界

- 接收 multipart 上传，校验扩展名、内容类型、大小与业务用途。
- 生成对外文件键 `fileKey`（32 位去连字符 UUID），保存存储路径、校验摘要与上传者。
- 提供文件元数据查询、内容读取、删除和内部绑定信息查询。
- 业务服务（用户头像、食堂图、窗口图、菜品图、评价图）只保存 `fileKey`，访问地址由文件服务解析。

**边界：** 文件键不是下载权限；业务操作仍需校验上传者、业务类型与资源归属。文件二进制不写入业务表。

## 上传与使用

1. 客户端上传文件。
2. 文件服务校验白名单并通过文件内容确认媒体类型。
3. 计算 SHA256，生成本地对象路径并落库。
4. 业务服务保存 `fileKey`，需要时调用文件服务解析访问地址。
5. 更新头像/窗口图/菜品图时，业务服务核验文件归属与用途，避免占用他人文件或错绑类型。

## 存储

- `storage_type` 当前支持 `LOCAL`，表结构预留 `MINIO` / `S3`。
- 存储路径由服务端生成，不直接拼接原始文件名。
- 原文件名仅作展示；扩展名规范化并经过白名单校验。
- 删除为逻辑删除，是否物理清理取决于存储策略。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `POST /files/upload` | 上传文件 |
| `GET /files/{fileKey}` | 查询文件元数据 |
| `GET /files/{fileKey}/content` | 读取文件内容 |
| `DELETE /files/{fileKey}` | 删除文件 |
| `GET /internal/files/{fileKey}` | 内部绑定/归属信息 |

## 数据表

`file_objects`：

| 字段 | 说明 |
|---|---|
| `file_id` | 内部主键 |
| `file_key` | 对外唯一文件键（char(32)） |
| `biz_type` | `AVATAR` / `RESTAURANT_IMAGE` / `WINDOW_IMAGE` / `PRODUCT_IMAGE` / `COMMENT_IMAGE` / `OTHER` |
| `biz_ref` | 绑定的业务记录内部主键，未绑定为空 |
| `storage_type` | `LOCAL` / `MINIO` / `S3` |
| `object_key` | 存储介质中的相对路径 |
| `original_name` | 原始文件名，仅展示 |
| `file_ext` | 规范化小写扩展名 |
| `content_type` | 内容校验后的媒体类型 |
| `file_size` | 字节数 |
| `sha256` | 内容摘要，用于去重与完整性校验 |
| `uploader_id` | 上传用户内部主键 |
| `is_deleted` | 逻辑删除标记 |

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`FileController.java`](src/main/java/com/hbue/ordering/file/controller/FileController.java) | 上传、元数据、内容、删除 |
| [`FileBindingInternalController.java`](src/main/java/com/hbue/ordering/file/controller/FileBindingInternalController.java) | 内部绑定信息 |
| [`FileStorageServiceImpl.java`](src/main/java/com/hbue/ordering/file/service/impl/FileStorageServiceImpl.java) | 文件业务编排 |
| [`LocalFileStorage.java`](src/main/java/com/hbue/ordering/file/storage/LocalFileStorage.java) | 本地存储实现 |
| [`FileTypeValidator.java`](src/main/java/com/hbue/ordering/file/storage/FileTypeValidator.java) | 类型与内容校验 |
| [`FileSecurityConfig.java`](src/main/java/com/hbue/ordering/file/config/FileSecurityConfig.java) | 上传与访问安全规则 |

## 配置与排查

- 上传目录、单文件大小限制等由文件服务配置决定；部署时必须确保运行用户对存储目录有读写权限。
- 图片不显示：检查 `fileKey` 是否存在、业务表是否正确保存、文件记录是否被删除、客户端能否访问文件服务地址。
- 旧数据迁移需要同步核对历史图片字段与文件记录，不能只改 URL。
- 详细接入约定见 [`docs/FILE_MEDIA_INTEGRATION.md`](../docs/FILE_MEDIA_INTEGRATION.md)。
