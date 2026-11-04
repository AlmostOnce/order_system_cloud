package com.hbue.ordering.user.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.user.model.BalanceSettlementGuardDO;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;
@Mapper
public interface BalanceSettlementGuardMapper extends BaseMapper<BalanceSettlementGuardDO> {
    @Insert("""
        INSERT INTO balance_settlement_guard(user_id,biz_type,biz_ref,amount)
        VALUES(#{id},#{type},#{ref},#{amount}) ON DUPLICATE KEY UPDATE user_id=user_id
        """)
    int ensure(@Param("id") Long id,@Param("type") String type,@Param("ref") String ref,@Param("amount") BigDecimal amount);
    @Select("SELECT * FROM balance_settlement_guard WHERE user_id=#{id} AND biz_type=#{type} AND biz_ref=#{ref} FOR UPDATE")
    BalanceSettlementGuardDO lock(@Param("id") Long id,@Param("type") String type,@Param("ref") String ref);
    @Update("UPDATE balance_settlement_guard SET closed=1 WHERE user_id=#{id} AND biz_type=#{type} AND biz_ref=#{ref}")
    int close(@Param("id") Long id,@Param("type") String type,@Param("ref") String ref);
}
