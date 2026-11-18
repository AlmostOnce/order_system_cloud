package com.hbue.ordering.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.coupon.model.CouponUseRecordDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 券使用流水访问接口；唯一订单约束承担消息重放和重复锁券的最终保护。
 */
public interface CouponUseRecordMapper extends BaseMapper<CouponUseRecordDO> {

    /**
     * 读取同券同订单的已有锁定结果，用于订单侧重试返回相同优惠快照。
     */
    @Select("SELECT * FROM coupon_use_record WHERE user_coupon_id = #{userCouponId} AND order_no = #{orderNo} LIMIT 1")
    CouponUseRecordDO findByCouponAndOrder(
            @Param("userCouponId") Long userCouponId,
            @Param("orderNo") String orderNo
    );

    /**
     * 订单级唯一查询，用于拒绝释放后以同一订单号再次锁券。
     */
    @Select("SELECT * FROM coupon_use_record WHERE order_no = #{orderNo} LIMIT 1")
    CouponUseRecordDO findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 核销仅允许 LOCKED 状态推进，重放由调用方先识别 USED 同订单记录。
     */
    @Update("""
            UPDATE coupon_use_record SET status = 'USED', used_at = #{usedAt}
            WHERE user_coupon_id = #{userCouponId} AND order_no = #{orderNo}
              AND status = 'LOCKED'
            """)
    int markUsed(
            @Param("userCouponId") Long userCouponId,
            @Param("orderNo") String orderNo,
            @Param("usedAt") java.time.LocalDateTime usedAt
    );

    /**
     * 释放仅迁移当前订单的 LOCKED 流水，不改动已核销历史。
     */
    @Update("""
            UPDATE coupon_use_record SET status = 'RELEASED', released_at = #{releasedAt}
            WHERE user_coupon_id = #{userCouponId} AND order_no = #{orderNo}
              AND status = 'LOCKED'
            """)
    int markReleased(
            @Param("userCouponId") Long userCouponId,
            @Param("orderNo") String orderNo,
            @Param("releasedAt") java.time.LocalDateTime releasedAt
    );
}
