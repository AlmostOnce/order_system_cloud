package com.hbue.ordering.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.user.model.BalanceRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 余额流水数据访问接口。
 *
 * <p>负责访问 balance_records 余额流水表。幂等依赖表上的唯一键
 * {@code (user_id, biz_type, biz_ref, change_type)}；唯一键冲突后需要一次当前读回查，
 * 以避免重复读快照看不到对方已提交的流水。</p>
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
     * @param recordId     流水记录 ID
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

    /**
     * 调用方必须先锁用户行，避免两个退款同时读取旧累计金额后一起超退。
     */
    @Select("SELECT COALESCE(SUM(amount),0) FROM balance_records WHERE user_id=#{userId} AND original_biz_ref=#{paymentNo} AND change_type='REFUND'")
    BigDecimal sumRefunded(@Param("userId") Long userId, @Param("paymentNo") String paymentNo);

    /**
     * 唯一键冲突后按当前读回查同一笔扣款流水。
     *
     * <p>必须使用 {@code FOR UPDATE} 当前读：在可重复读隔离级别下，事务内的普通快照读会一直停留在
     * 冲突发生前的读视图，看不到并发事务刚刚提交的流水，从而把可幂等重放的请求误判为真实故障并返回 500。
     * 此时对端已提交，加锁读能返回最新版本；行已被唯一键索引定位，代价很小。</p>
     *
     * @param userId  用户 ID
     * @param bizType 业务类型
     * @param bizRef  业务单据引用
     * @return 同一笔扣款流水，不存在时返回 null
     */
    @Select("""
            SELECT * FROM balance_records
            WHERE user_id = #{userId}
              AND biz_type = #{bizType}
              AND biz_ref = #{bizRef}
              AND change_type = 'PAY'
            FOR UPDATE
            """)
    BalanceRecordDO selectPayForUpdate(
            @Param("userId") Long userId,
            @Param("bizType") String bizType,
            @Param("bizRef") String bizRef
    );
}
