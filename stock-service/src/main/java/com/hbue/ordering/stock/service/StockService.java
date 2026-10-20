package com.hbue.ordering.stock.service;

import com.hbue.ordering.stock.api.dto.StockDeductRequest;
import com.hbue.ordering.stock.api.dto.StockRestoreRequest;
import com.hbue.ordering.stock.api.vo.StockVO;

/**
 * 库存业务服务。
 *
 * <p>对外只服务于内部调用：下单时扣减、取消/退款时回补、按档口菜品查询快照。
 * 所有写操作都在数据库事务内完成，并保证「同一业务单据同一菜品只生效一次」。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
public interface StockService {

    /**
     * 扣减库存。
     *
     * <p>流程为：定位库存记录 → 幂等预检 → Redis 原子预扣 → 加行锁写流水并乐观锁扣减。
     * 任意一行失败都抛出异常让调用方回滚，绝不允许部分扣减被当作成功。</p>
     *
     * @param request 扣减请求（订单号、档口、菜品明细）
     * @throws com.hbue.ordering.common.core.exception.BusinessException 库存不足、无库存记录或并发冲突时抛出
     */
    void deduct(StockDeductRequest request);

    /**
     * 回补库存。
     *
     * <p>只允许回补「确实扣减过」的订单菜品，且同一单据同一菜品只回补一次。</p>
     *
     * @param request 回补请求（订单号、档口、菜品明细）
     * @throws com.hbue.ordering.common.core.exception.BusinessException 无对应扣减记录或库存记录缺失时抛出
     */
    void restore(StockRestoreRequest request);

    /**
     * 查询指定档口菜品的库存快照。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @return 库存视图；无库存记录时返回 null
     */
    StockVO getStock(Long windowId, Long productId);
}
