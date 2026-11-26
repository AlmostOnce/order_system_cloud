package com.hbue.ordering.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.coupon.dto.CouponExpiryReminderRow;
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

    /**
     * 扫描进入"到期前 24 小时"窗口且尚未提醒的可用券。
     *
     * <p>条件刻意收紧为 {@code valid_until > NOW()}：已经过期但尚未被惰性标记的券不提醒，
     * 否则会向学生推送"即将过期"却点进去发现已失效的矛盾提醒。</p>
     *
     * <p>按截止时间升序返回，最紧急的券优先发送；数量由调用方限制，避免一次扫描把整表
     * 拉进内存。已提醒记录由 {@code expiry_reminded_at} 排除，天然幂等。</p>
     *
     * @param limit 单次最多扫描条数
     * @return 待提醒的券行
     */
    @Select("""
            SELECT uc.user_coupon_id AS userCouponId, uc.user_id AS userId,
                   t.title AS title, uc.valid_until AS validUntil
            FROM user_coupon uc
            JOIN coupon_template t ON t.coupon_template_id = uc.coupon_template_id
            WHERE uc.status = 'AVAILABLE'
              AND uc.expiry_reminded_at IS NULL
              AND uc.valid_until > NOW()
              AND uc.valid_until <= DATE_ADD(NOW(), INTERVAL 24 HOUR)
            ORDER BY uc.valid_until ASC, uc.user_coupon_id ASC
            LIMIT #{limit}
            """)
    List<CouponExpiryReminderRow> findExpiryReminderCandidates(@Param("limit") int limit);

    /**
     * 标记某张券已发送到期提醒。
     *
     * <p>条件更新带 {@code expiry_reminded_at IS NULL}：多实例并发扫描时只有一次能写入成功，
     * 未命中说明已被其它实例处理，不视为错误。</p>
     *
     * @param userCouponId 用户券记录编号
     * @return 受影响行数，首次标记为 1
     */
    @Update("""
            UPDATE user_coupon SET expiry_reminded_at = NOW(3)
            WHERE user_coupon_id = #{userCouponId} AND expiry_reminded_at IS NULL
            """)
    int markExpiryReminded(@Param("userCouponId") Long userCouponId);
}
