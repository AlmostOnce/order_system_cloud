# 图片文件键迁移

状态：2026-10-28 用户授权执行第二步后，MySQL 与 MongoDB 图片字段迁移已完成。三个业务服务已由用户停止，本次未构建或启动服务；后续应使用新源码启动。

业务表的新列均为 `CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL`：

| 表 | 旧列 | 新列 |
|---|---|---|
| user_db.users | avatar_url | avatar_file_key |
| product_db.restaurants | image_url | image_file_key |
| product_db.dining_windows | image_url | image_file_key |
| product_db.products | image_url | image_file_key |

MongoDB comment_db.comments 的 `images` 数组迁为 `imageFileKeys`，元素由受控访问地址变为文件键。API 返回的 avatarUrl/imageUrl/images 仍为可访问 URL，不改变展示契约。

## 只读预检

在后端仓库目录执行 `python tools/migrate-image-references.py` 和 `python tools/migrate-comment-image-references.py`。默认不会修改数据库。依赖本地 .env 的数据库账号及 WSL Ubuntu-24.04 中已启动的 Docker 容器，不使用 Maven。

MySQL 预检拒绝未知图片 URL、非法文件键和已删除/不存在的文件；Mongo 预检拒绝非法内容及新旧字段冲突。存在非文件服务图片时，应先导入文件服务并映射，不会自动丢弃图片。

## 执行顺序（本次已完成迁移，服务启动待用户进行）

1. 停止旧版用户、商品、评论服务，避免迁移期间继续写入；这三个端口为 9092、9093、9105。脚本执行模式会检查端口。
2. 执行 `python tools/migrate-image-references.py --apply`。先备份 user_db/product_db 及引用清单到用户目录 .codex/backups/order_system_cloud，再新增键列、回填并逐行核对；全部通过后删除旧 URL 列。过程可重跑以恢复部分完成的迁移，DDL 不是跨表原子事务。
3. 执行 `python tools/migrate-comment-image-references.py --apply`。先备份 comments 为保留 BSON 类型的 Extended JSON，再写 imageFileKeys 并移除 images。可重跑，冲突时拒绝覆盖。
4. 使用新源码启动服务后验证头像修改、菜单查询、管理端图片修改及评论图片提交查询。本次未构建、未重启、未执行 Java 单测。

回滚应在停服状态根据备份和实际执行阶段恢复对应库或集合，避免覆盖迁移后产生的新数据。历史建表 SQL 维持原样，旧库先走历史迁移再执行本工具；不直接对 URL 列改名来冒充键转换。

普通目录创建/编辑请求改用 `imageFileKey`。更新时不传保留原图，传空串清除，新键必须属于当前上传人并匹配图片用途和目标。专用头像、窗口、菜品图片接口仍提交 `{fileKey}`。实体只存键，文件内容与元数据仍由文件服务维护。

## 本次执行结果

- MySQL 四表迁移完成：users 18条、restaurants 3条、dining_windows 9条、products 77条。唯一的已有头像引用保留，逐行比较通过，四个旧 URL 列已删除，新列均为 CHAR(32)。
- MySQL 备份：C:/Users/DELL/.codex/backups/order_system_cloud/image_references_20261028_100725。
- MongoDB 当前0条评论，预检及执行通过；备份：C:/Users/DELL/.codex/backups/order_system_cloud/comment_images_20261028_100743.ejson。
- 执行后重新运行两个只读预检均通过；9092/9093/9105 无监听。未执行 Maven、构建或服务启动。
