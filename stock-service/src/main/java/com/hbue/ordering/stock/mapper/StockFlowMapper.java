package com.hbue.ordering.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.stock.model.StockFlowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存流水数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的基础 CRUD 能力，并提供幂等判断与对账所需的统计查询。
 * 幂等由数据库唯一键 {@code (biz_type, biz_ref, stock_id)} 保证：重复插入会抛出
 * 唯一键冲突，由业务层识别为「已处理过」并转换为幂等成功。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
public interface StockFlowMapper extends BaseMapper<StockFlowDO> {

    /**
     * 统计某个业务单据对某条库存是否已按指定类型记过流水。
     *
     * @param bizType 业务类型：ORDER_DEDUCT / ORDER_RESTORE
     * @param bizRef 业务单据号（订单号）
     * @param stockId 库存记录 ID
     * @return 匹配的流水条数，0 表示尚未记账
     */
    @Select("SELECT COUNT(*) FROM stock_flow WHERE biz_type = #{bizType} "
            + "AND biz_ref = #{bizRef} AND stock_id = #{stockId}")
    int countByBiz(
            @Param("bizType") String bizType,
            @Param("bizRef") String bizRef,
            @Param("stockId") Long stockId
    );

    /**
     * 汇总某条库存的全部流水变动数量，供定时对账校验主表与流水是否自洽。
     *
     * <p>扣减为负、回补为正，因此「总可售上限 + 流水累计」应等于当前可售数量。</p>
     *
     * @param stockId 库存记录 ID
     * @return 流水变动数量之和；没有流水时返回 0
     */
    @Select("SELECT COALESCE(SUM(change_qty), 0) FROM stock_flow WHERE stock_id = #{stockId}")
    int sumChangeQty(@Param("stockId") Long stockId);
}
