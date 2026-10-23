# 数据库中文注释维护

覆盖当前六个业务数据库：user_db、product_db、order_db、payment_db、stock_db、file_db，共 16 张表、169 个字段，包括两张 Seata 回滚日志表。系统数据库不在修改范围内。

- `chinese_comments.json`：表、字段中文说明的维护来源；新增未知表或字段时，工具会停止并要求补充说明。
- `DATA_DICTIONARY.md`：根据实际数据库生成的中文数据字典。
- `all_chinese_comments.sql`：当前表结构对应的注释补齐脚本，使用 UTF-8 连接，修复空注释及历史乱码。
- `../../tools/apply-database-comments.py`：从实际 SHOW CREATE TABLE 提取完整字段定义，生成注释 SQL；默认只生成，传入 `--apply` 才执行。

表结构变更后应重新生成注释脚本，不应直接沿用旧版本的完整 MODIFY 定义，以免覆盖后续字段属性调整。历史建表迁移保持原样，新库完成全部业务迁移后再执行当前注释维护脚本。

执行模式会先备份表结构、生成注释回退 SQL、记录字段元数据及表数据校验值，再进行修改。验证类型、空值约束、默认值、字符集、排序规则、自动更新属性、索引、外键、检查约束和数据校验值。MySQL 自动补全的等价字符集声明单独按元数据核对；带显式字符集文字的 CHECK 表达式按原定义恢复。

2026-10-23 已在本机数据库执行。原始修改前备份位于：
`C:/Users/DELL/.codex/backups/order_system_cloud/chinese_comments_20261023_122216/`。
后续执行的验证清单保存在同级 `chinese_comments_*` 目录。备份不进入代码仓库。未运行 Maven 或重启任何服务。
