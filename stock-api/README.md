# stock-api 库存契约模块

> stock-service 的内部调用契约：扣减、回补、退款回补、库存查询，并自带业务错误码枚举供调用方识别。

## Feign 客户端

| 客户端 | 用途 | 降级约定 |
|---|---|---|
| `StockServiceClient` | 下单扣减（Seata 全局事务关键分支）、取消回补、批量/窗口库存查询 | 降级工厂全方法返回服务不可用——扣减降级迫使调用方回滚全局事务，绝不伪装成功 |
| `StockRefundClient` | 部分退款回补 | **无成功降级**：远程结果不明由退款任务按退款号重试 |

## 错误码

`StockErrorCode`：`STOCK_409_01` 库存不足 / `STOCK_404_01` 记录不存在 / `STOCK_409_02` 并发冲突——定义在契约层，供库存服务抛出与订单服务区分「库存不足 vs 服务不可用」。

## 模型

`StockDeductRequest`、`StockRestoreRequest`、`StockRefundRequest`、`StockBatchQueryRequest`、`StockVO`。

## 使用约定

被订单、商品、推荐、库存服务（自引用）依赖；扣减/回补以业务引用幂等，重复调用返回原结果。
