package com.hbue.ordering.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.user.model.BalanceRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 余额流水数据访问接口。
 *
 * <p>负责访问 balance_records 余额流水表。幂等依赖表上的唯一键
 * {@code (user_id, biz_type, biz_ref, change_type)}，因此不需要额外查询方法。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Mapper
public interface BalanceRecordMapper extends BaseMapper<BalanceRecordDO> {

    /**
     * 回填流水的变动后余额。
     *
     * <p>流水先插入作为幂等占位（此时余额已知但尚未扣减），扣款成功后再回填，
     * 避免出现"流水已记账但余额未扣"的中间状态被外部读到。</p>
     *
     * @param recordId 流水记录 ID
     * @param balanceAfter 扣款后余额
     * @return 受影响行数
     */
    @Update("""
        UPDATE balance_records
        SET balance_after = #{balanceAfter}
        WHERE record_id = #{recordId}
        """)
    int updateBalanceAfter(
            @Param("recordId") Long recordId,
            @Param("balanceAfter") BigDecimal balanceAfter
    );
}
