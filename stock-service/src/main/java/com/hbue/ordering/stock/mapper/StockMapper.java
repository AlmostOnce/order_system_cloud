package com.hbue.ordering.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.stock.model.StockDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 库存主表数据访问接口。
 *
 * <p>除基础 CRUD 外，提供按 (档口, 菜品) 定位库存的两条查询语句，以及两条原子更新语句：
 * 扣减按版本号做乐观锁并附带「可售数量足够」条件，回补只做累加。
 * 判断与更新都写在同一条 SQL 中，由数据库保证并发下不会超卖。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
public interface StockMapper extends BaseMapper<StockDO> {

    /**
     * 查询指定档口菜品的库存记录。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @return 库存记录；不存在时返回 null
     */
    @Select("SELECT * FROM stock WHERE window_id = #{windowId} "
            + "AND product_id = #{productId} AND is_deleted = 0")
    StockDO selectByWindowProduct(
            @Param("windowId") Long windowId,
            @Param("productId") Long productId
    );

    /**
     * 加行锁查询指定档口菜品的库存记录。
     *
     * <p>扣减与回补都先取这一把行锁，把「查流水是否已记账 → 改库存 → 写流水」
     * 串行化在同一库存行上：后到的事务会阻塞到前一个事务提交，
     * 从而能读到最新提交的流水记录，天然实现幂等与不超卖。</p>
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @return 库存记录；不存在时返回 null
     */
    @Select("SELECT * FROM stock WHERE window_id = #{windowId} "
            + "AND product_id = #{productId} AND is_deleted = 0 FOR UPDATE")
    StockDO selectByWindowProductForUpdate(
            @Param("windowId") Long windowId,
            @Param("productId") Long productId
    );

    /**
     * 查询全部未删除的库存记录，供定时对账逐条比对。
     *
     * @return 全部有效库存记录，按主键升序
     */
    @Select("SELECT stock_id, window_id, product_id, total_qty, available_qty, version, "
            + "is_deleted, created_at, updated_at FROM stock WHERE is_deleted = 0 "
            + "ORDER BY stock_id")
    List<StockDO> selectAllForReconcile();

    /**
     * 按乐观锁原子扣减可售数量。
     *
     * <p>更新条件同时要求「版本号仍是本次事务读到的值」与「剩余可售数量不少于本次扣减量」，
     * 判断与自减写在同一条 SQL 中，由数据库保证并发下可售数量绝不会变为负数。
     * 调用方在更新前已对该行加了行锁，因此版本号条件不会产生误判，
     * 它作为最后一道不超卖防线保留。</p>
     *
     * @param stockId 库存记录 ID
     * @param version 本事务读到的版本号
     * @param quantity 扣减数量，必须为正数
     * @return 受影响行数：1 表示扣减成功，0 表示版本冲突或库存不足
     */
    @Update("UPDATE stock SET available_qty = available_qty - #{quantity}, "
            + "version = version + 1, updated_at = NOW() "
            + "WHERE stock_id = #{stockId} AND version = #{version} "
            + "AND available_qty >= #{quantity} AND is_deleted = 0")
    int deductAvailable(
            @Param("stockId") Long stockId,
            @Param("version") Integer version,
            @Param("quantity") Integer quantity
    );

    /**
     * 读取当前可售数量。
     *
     * <p>在扣减或回补之后立即调用，用于记录流水的准确前后值：
     * 同一事务内能读到本事务刚写入的结果，不受并发事务影响。</p>
     *
     * @param stockId 库存记录 ID
     * @return 当前可售数量；记录不存在时返回 null
     */
    @Select("SELECT available_qty FROM stock WHERE stock_id = #{stockId}")
    Integer selectAvailableQty(@Param("stockId") Long stockId);

    /**
     * 回补可售数量。
     *
     * <p>回补由 {@code stock_flow} 的 ORDER_RESTORE 流水保证每单每菜品只执行一次，
     * 因此这里直接累加，无需再校验上限。</p>
     *
     * @param stockId 库存记录 ID
     * @param quantity 回补数量，必须为正数
     * @return 受影响行数：1 表示回补成功
     */
    @Update("UPDATE stock SET available_qty = available_qty + #{quantity}, "
            + "version = version + 1, updated_at = NOW() "
            + "WHERE stock_id = #{stockId} AND is_deleted = 0")
    int restoreQuantity(
            @Param("stockId") Long stockId,
            @Param("quantity") Integer quantity
    );
}
