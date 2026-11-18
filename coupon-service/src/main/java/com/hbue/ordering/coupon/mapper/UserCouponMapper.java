package com.hbue.ordering.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.coupon.dto.CouponMineRow;
import com.hbue.ordering.coupon.model.UserCouponDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 学生券数据访问接口；锁券/释放/核销先锁定一行，再用状态条件更新。
 */
public interface UserCouponMapper extends BaseMapper<UserCouponDO> {

    /**
     * 查询自己的券及规则快照；状态为空时不过滤，AVAILABLE 额外校验时效和模板状态，列表最多返回 200 条。
     */
    @Select("""
            SELECT uc.user_coupon_id AS userCouponId,
                   t.coupon_template_id AS templateId, t.title AS title,
                   t.min_spend AS minSpend, t.discount_amount AS discountAmount,
                   t.scope_type AS scopeType, uc.valid_from AS validFrom,
                   uc.valid_until AS validUntil, uc.status AS status,
                   tw.window_id AS windowId
            FROM (
              SELECT * FROM user_coupon
              WHERE user_id = #{userId}
                AND (#{status} IS NULL OR status = #{status})
                AND (#{status} IS NULL OR #{status} <> 'AVAILABLE'
                     OR (valid_from <= NOW() AND valid_until > NOW()))
              ORDER BY valid_until ASC, user_coupon_id ASC
              LIMIT 200
            ) uc
            JOIN coupon_template t ON t.coupon_template_id = uc.coupon_template_id
            LEFT JOIN coupon_template_window tw
              ON tw.coupon_template_id = t.coupon_template_id
            WHERE (#{status} IS NULL OR #{status} <> 'AVAILABLE' OR t.status <> 'STOPPED')
            ORDER BY uc.valid_until ASC, uc.user_coupon_id ASC
            """)
    List<CouponMineRow> listMineRows(
            @Param("userId") Long userId,
            @Param("status") String status
    );

    /**
     * 锁定券记录，避免并发请求为同一张券绑定不同订单。
     */
    @Select("SELECT * FROM user_coupon WHERE user_coupon_id = #{userCouponId} FOR UPDATE")
    UserCouponDO findByIdForUpdate(@Param("userCouponId") Long userCouponId);

    /**
     * 根据订单号锁定当前绑定券；无券订单释放调用将得到 null 并作为幂等空操作。
     */
    @Select("SELECT * FROM user_coupon WHERE locked_order_no = #{orderNo} FOR UPDATE")
    UserCouponDO findByLockedOrderForUpdate(@Param("orderNo") String orderNo);

    /**
     * 管理员停止使用时撤销未进入订单的券，已锁定订单不被静默改写。
     */
    @Update("""
            UPDATE user_coupon SET status = 'REVOKED'
            WHERE coupon_template_id = #{templateId} AND status = 'AVAILABLE'
            """)
    int revokeAvailable(@Param("templateId") Long templateId);

    /**
     * 仅将已经过期且仍可用的本人券改为 EXPIRED，保持查询展示与状态一致。
     */
    @Update("""
            UPDATE user_coupon SET status = 'EXPIRED'
            WHERE user_id = #{userId} AND status = 'AVAILABLE'
              AND valid_until <= NOW()
            """)
    int expireAvailable(@Param("userId") Long userId);
}
