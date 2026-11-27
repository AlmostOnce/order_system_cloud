# file-service 文件服务

> 图片上传、元数据登记、内容读取与逻辑删除。默认端口 `9102`，数据库 `file_db`，本地文件系统存储。不依赖任何业务服务（不启用 Feign）；内容接口匿名开放供前端直接引用，图片归属校验经内部接口由各业务服务调用。无 MQ。

## 模块定位与职责边界

- 只做文件的存储与元数据管理：类型校验、摘要去重、归属登记、逻辑删除。
- 图片归属校验不向前端暴露上传者主键：业务服务（头像、窗口图片、评价图片、反馈图片等）经内部接口查询绑定元数据，完成「图片属于指定业务对象且由本人上传」的校验。
- 存储介质与类型校验在进程内完成，无对象存储外部依赖。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server（jose）/ Validation / Actuator |
| 数据 | MyBatis-Plus 3.5.8、Druid、MySQL `file_db`（默认 `127.0.0.1:9095`，账号 `file_service`） |
| 存储 | 本地文件系统（`file.storage.root`，默认 `/home/user/data/order-system/files`） |
| 缓存 | Redis 仅用于 JWT 注销黑名单 |
| 共享模块 | common-web、common-core、common-redis |

- 端口 `9102`；multipart 上限：单文件 5MB / 单请求 6MB（业务层与容器双重拦截）。
- `file.public-base-url` 默认指向网关（`http://localhost:9090/api`），拼接出对外访问地址。
- 连接初始化 `SET SESSION time_zone='+08:00'`；服务间共享令牌 `internal.scope.token`。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `FileController` | 上传、元数据、内容、删除接口；删除时从 JWT 角色判定管理员；响应头文件名清洗防注入 |
| `FileBindingInternalController` | 内部图片归属元数据查询（`X-Internal-Token`） |
| `FileStorageServiceImpl` | 上传流程编排：校验 → 摘要 → 秒传 → 落库；删除归属判定 |
| `FileStorage` / `LocalFileStorage` | 介质抽象与本地实现；路径归一化并断言不出根目录（防穿越） |
| `FileTypeValidator` | 魔数白名单校验（JPEG/PNG/GIF/WebP），不信任扩展名与 Content-Type |
| `FileSecurityConfig` 及错误处理器 | 无状态 JWT + 黑名单 fail-closed + 统一 401/403 |
| `FileObjectDO` / `FileObjectMapper` | `file_objects` 表映射 |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/file_db/V1`。

### file_objects（文件对象元数据）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| file_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 文件记录主键 |
| file_key | CHAR(32) | NOT NULL | uk_file_key | 对外文件键（UUID 去横线），接口只暴露此值 |
| biz_type | VARCHAR(32) | NOT NULL | 归属查询索引参与 | 业务类型：AVATAR/WINDOW_IMAGE/PRODUCT_IMAGE/RESTAURANT_IMAGE/COMMENT_IMAGE/OTHER |
| biz_ref | BIGINT UNSIGNED | 可空 | 归属查询索引参与 | 关联业务主键，上传时未知可为空后绑 |
| storage_type | VARCHAR(16) | NOT NULL，默认 LOCAL | — | 存储介质：LOCAL/MINIO/S3（仅 LOCAL 已实现） |
| object_key | VARCHAR(512) | NOT NULL | — | 介质内相对路径，服务端按内容摘要生成，绝不拼接用户输入 |
| original_name | VARCHAR(255) | NOT NULL | — | 原始文件名，仅展示，不参与落盘路径 |
| file_ext | VARCHAR(16) | NOT NULL | — | 扩展名（小写、魔数白名单内） |
| content_type | VARCHAR(128) | NOT NULL | — | MIME 类型，由魔数校验后确定 |
| file_size | BIGINT UNSIGNED | NOT NULL | — | 字节数 |
| sha256 | CHAR(64) | NOT NULL | 摘要索引参与 | 内容摘要，秒传去重与完整性校验 |
| uploader_id | BIGINT UNSIGNED | NOT NULL | 上传者索引参与 | 上传者 user_id（取自 JWT sub） |
| is_deleted | TINYINT | NOT NULL，默认 0 | — | 逻辑删除：0 正常、1 已删 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 创建时间 |
| updated_at | DATETIME | NOT NULL，自动更新 | — | 更新时间 |

- 唯一键：`uk_file_key(file_key)`。
- 普通索引：`idx_sha256(sha256)`（秒传）、`idx_uploader(uploader_id, biz_type)`、`idx_biz(biz_type, biz_ref)`（归属校验）。

**存储目录策略**：`objectKey = {sha256前2位}/{sha256}.{ext}`——摘要一级目录 + 内容寻址，不含上传日期；路径全部服务端生成、不含用户输入；同一内容摘要无论何时上传都对应同一路径，天然去重（秒传）。删除先逻辑删记录，仅当该对象键已无其它未删除引用时回收物理文件。

## 核心业务流程

### 上传（`FileStorageServiceImpl.upload`，事务内）

1. 非空与大小校验（≤5MB）。
2. 魔数判定扩展名与 MIME（不在白名单返回 400）。
3. 计算 SHA-256，生成内容寻址路径；已存在即秒传（跳过写盘）。
4. 写入文件（按需创建父目录）→ 生成 fileKey → 落库登记 → 返回含公开访问地址的响应。

### 归属校验与删除

- 上传时可携带业务类型与业务引用；业务服务后续经内部接口校验「fileKey 属于指定业务类型 + 业务对象，且上传者为当前操作者」，不通过即拒绝绑定。
- 删除：非本人且非系统管理员返回 403；更新逻辑删除标记后，若同一对象键已无其它未删除记录引用，回收物理文件；物理回收失败只记日志，不影响已生效的逻辑删除。
- 内容读取：匿名开放，响应带 `nosniff`、`Cache-Control: public, max-age=3600` 与 inline 下载头。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 相同内容重复上传 | 内容寻址存储：objectKey 由 SHA-256 生成，上传前按摘要查重实现秒传（跳过写盘）；摘要索引 `idx_sha256` 支撑查重 | 重复上传返回同一 fileKey 语义下的新记录或已有路径，不重复占盘 |
| 同内容并发上传 | 同一摘要对应同一路径，并发写盘指向相同目标文件 | 后写覆盖先写同一内容（字节级相同，无影响） |
| 删除与归属校验并发 | 删除为逻辑删除（is_deleted 置 1），归属校验按活跃记录过滤；随后按对象键回查活跃引用数，为 0 才回收物理文件 | 已删记录的归属查询按不存在处理（统一 404 语义），不泄露状态；仍被引用的内容不会被物理删除 |
| 路径注入 | 路径全部服务端按摘要生成，读取路径归一化后断言位于存储根目录内 | 越界路径拒绝 |
| 黑名单不可确认 | JWT 黑名单查询失败按无效令牌拒绝 | fail-closed，不降级放行 |

## 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/files/upload` | 登录 | multipart 上传，可携带业务类型/引用 |
| GET | `/files/{fileKey}` | 登录 | 元数据查询 |
| GET | `/files/{fileKey}/content` | 匿名 | 文件内容 |
| DELETE | `/files/{fileKey}` | 登录 | 本人或系统管理员，逻辑删除 |
| GET | `/internal/files/{fileKey}` | X-Internal-Token | 归属元数据（含上传者，不向浏览器暴露） |
| GET | `/actuator/health`、swagger | 匿名 | 健康与文档 |

## 消息、异步与缓存设计

- 无 MQ 生产与消费；浏览器侧靠内容接口的 Cache-Control 缓存，服务端无内容缓存层。

## 安全设计

- 路径穿越防护：所有读取路径归一化后必须位于存储根目录内。
- JWT 黑名单状态无法确认时按无效令牌拒绝（fail-closed）；内部令牌未配置一律拒绝。
- 内容类型由服务端魔数决定，响应禁用 MIME 嗅探；「不存在」与「已删除」统一按 404 返回，不泄露状态。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `file.storage.root` | `/home/user/data/order-system/files` | 存储根目录 |
| `file.public-base-url` | `http://localhost:9090/api` | 对外访问地址前缀 |
| `internal.scope.token` | — | 服务间共享令牌 |
| multipart 上限 | 5MB / 6MB | 单文件 / 单请求 |

## 异常与失败行为

- 类型白名单外返回 400；读取失败按 404 语义；写入失败按 500 语义。
- 逻辑删除后的文件内容仍可能因内容寻址被同名路径命中，但元数据查询按活跃记录过滤。

## 关键代码入口

`FileStorageServiceImpl`（上传编排与秒传）→ `LocalFileStorage`（路径安全）→ `FileBindingInternalController`（归属校验协议）。

## 接口与集成约定

- multipart 字段名约定：`file`、`bizType`、`bizRef`。
- 业务侧 `ManagedImageClient` 默认访问 `http://localhost:9102`，可用 `FILE_SERVICE_BASE_URL` 覆盖。
- 网关只对 `GET /api/files/*/content` 匿名放行，元数据/上传/删除仍需认证；不接受客户端提供的远程 URL。
- 上传成功不等于业务保存成功：绑定失败可复用 fileKey 重试上传结果。
- 目录编辑只接受 imageFileKey：省略表示保留、空串表示清除；变更必须校验上传归属、用途与业务 ID（校验在业务服务侧）。
- 图片 fileKey 迁移工具规则：预检旧值、备份、转换受控地址、验证后移除旧列；未知外部地址拒绝迁移。
- 查询响应保留 avatarUrl/imageUrl/images 字段，统一生成相对访问地址。

## 已知限制与未验证项

- 仅本地文件系统实现，MINIO/S3 枚举值保留未实现。
- 物理文件在最后一条引用记录被逻辑删除时回收；"上传端判断文件存在"与本回收之间的极小并发窗口内，同内容上传可能短暂指向已回收文件，再次上传同一内容会重新写盘自愈。

## 关联文档

- 根 README「数据库与中间件」：`file_db` 说明。
- `common-web/README.md`：`ManagedImageClient` 归属校验封装。
