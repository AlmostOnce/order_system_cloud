# 商品目录数据库

商品目录由 `product-service` 所有，数据库为 `product_db`。每条菜品记录只属于一个窗口；不同窗口即使售卖同名菜，也分别保存记录和价格。窗口分类同样各自维护。

## 表

- `dining_windows`：窗口基本信息。
- `product_categories`：窗口下的菜品分类。
- `products`：窗口下的菜品、售价和上架状态。
- `order_db.order_items`：下单时保存菜品信息、单价和数量快照。

商品库内通过外键保证菜品所属窗口有效、分类属于同一窗口。订单库只保存 `product_id` 作为追溯信息，不跨库设置外键；历史展示以订单明细快照为准。

## 执行迁移

迁移文件：

- `migrations/product_db/V1__create_product_catalog_tables.sql`：创建 `product_db` 及窗口、分类、菜品表。
- `migrations/V3__create_order_items_table.sql`：在已有 `order_db` 中创建订单明细表。

项目当前不自动执行 SQL 迁移。首次创建商品库时，需要用具有建库权限的 MySQL 账号执行商品库迁移；订单明细迁移针对已应用 V1、V2 的 `order_db` 执行。Docker 开发环境可从 MySQL 容器内运行：

```bash
docker exec -i order-system-cloud-mysql-1 sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' \
  < docs/database/migrations/product_db/V1__create_product_catalog_tables.sql

docker exec -i order-system-cloud-mysql-1 sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot order_db' \
  < docs/database/migrations/V3__create_order_items_table.sql
```

迁移使用 `CREATE DATABASE/TABLE IF NOT EXISTS`，可重复执行；已有同名表不会被重建或修改。

## 商品服务连接配置

商品服务使用独立的 `product_service` 数据库账号，只授予 `product_db` 的查询、写入、更新和删除权限。账号密码通过 `PRODUCT_DB_USERNAME`、`PRODUCT_DB_PASSWORD` 环境变量提供；本机开发值保存在被 Git 忽略的 `.env`，IDEA 运行配置需要单独设置这两个环境变量。

## 顾客菜单查询接口

商品服务提供以下只读接口，均要求携带有效的 CUSTOMER JWT：

| Gateway 路径 | 说明 |
|---|---|
| `GET /api/products/windows` | 查询所有启用窗口的摘要 |
| `GET /api/products/windows/{windowId}/menu` | 查询指定启用窗口的菜单 |

窗口菜单返回窗口信息、启用分类及各分类当前在售的菜品。停用窗口返回 `COMMON_404`；停用分类及下架菜品不会展示。允许 `category_id` 为空的在售菜品会放在 `uncategorizedProducts` 中。分类和菜品分别按 `sort_order`、ID 稳定排序。

Nacos 的 `gateway-service.yaml` 将 `/api/products/**` 转发至 `lb://product-service`，并使用 `StripPrefix=1`。Gateway 删除外层 `/api` 后，请求到达商品服务的 `/products/**` 路径。

菜单接口响应示例：

```json
{
  "code": "COMMON_000",
  "message": "操作成功",
  "data": {
    "windowId": 1,
    "windowCode": "W-DEMO-01",
    "windowName": "一楼测试窗口",
    "location": "一楼东侧",
    "categories": [
      {
        "categoryId": 1,
        "categoryName": "主食",
        "sortOrder": 10,
        "products": [
          {
            "productId": 1,
            "productCode": "DEMO-RICE-01",
            "productName": "鸡肉饭",
            "description": "开发环境示例菜品",
            "imageUrl": null,
            "price": 18.5,
            "sortOrder": 10
          }
        ]
      }
    ],
    "uncategorizedProducts": []
  }
}
```

## 目录管理接口

目录管理接口统一位于 `/products/admin/**`，经 Gateway 访问时使用 `/api/products/admin/**`。全部接口要求有效 JWT 且角色为 `ADMIN`；普通注册账号默认为 `CUSTOMER`。当前项目没有提供授予管理员角色的 HTTP 接口，部署前需要由可信的运维流程配置管理员身份。

| 方法 | Gateway 路径 | 说明 |
|---|---|---|
| `GET` | `/api/products/admin/windows` | 查询全部窗口，包括停用窗口 |
| `POST` | `/api/products/admin/windows` | 创建窗口 |
| `PUT` | `/api/products/admin/windows/{windowId}` | 全量更新窗口，可恢复启用 |
| `DELETE` | `/api/products/admin/windows/{windowId}` | 停用窗口，不物理删除 |
| `GET` | `/api/products/admin/windows/{windowId}/categories` | 查询窗口全部分类，包括停用分类 |
| `POST` | `/api/products/admin/windows/{windowId}/categories` | 在窗口下创建分类 |
| `PUT` | `/api/products/admin/categories/{categoryId}` | 全量更新分类，可恢复启用 |
| `DELETE` | `/api/products/admin/categories/{categoryId}` | 停用分类，保留分类和菜品 |
| `GET` | `/api/products/admin/windows/{windowId}/products` | 查询窗口全部菜品，包括下架菜品 |
| `POST` | `/api/products/admin/windows/{windowId}/products` | 在窗口下创建菜品 |
| `GET` | `/api/products/admin/products/{productId}` | 查询菜品详情 |
| `PUT` | `/api/products/admin/products/{productId}` | 全量更新菜品，可重新上架 |
| `DELETE` | `/api/products/admin/products/{productId}` | 下架菜品，不物理删除 |

窗口、分类和菜品的唯一键冲突返回 HTTP 409 / `COMMON_409`。菜品分类必须属于当前窗口；在售菜品只能选择启用分类。停用分类后，顾客菜单和下单核价都会排除该分类下的菜品；重新启用分类后，仍在售的菜品会重新出现。窗口和分类停用通过 `status=0` 实现，菜品下架通过 `sale_status=0` 实现。

创建窗口请求示例：

```json
{
  "windowCode": "W-BUILDING-A",
  "windowName": "一号楼窗口",
  "location": "一楼东侧",
  "status": 1
}
```

创建菜品请求示例：

```json
{
  "categoryId": 1,
  "productCode": "RICE-01",
  "productName": "鸡肉饭",
  "description": "鸡肉与米饭",
  "imageUrl": "",
  "price": 18.50,
  "saleStatus": 1,
  "sortOrder": 10
}
```

本地开发数据位于 `migrations` 之外的 `seeds/product_catalog_dev.sql`。脚本使用窗口编码、窗口内分类名称和窗口内菜品编码定位记录，可重复执行；测试数据包括下架菜品、停用分类菜品和未分类菜品，用于核对菜单过滤规则。执行方式：

```bash
docker exec -i order-system-cloud-mysql-1 sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' \
  < docs/database/seeds/product_catalog_dev.sql
```
