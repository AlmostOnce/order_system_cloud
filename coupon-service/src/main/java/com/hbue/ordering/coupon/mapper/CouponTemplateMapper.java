package com.hbue.ordering.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.coupon.dto.CouponTemplateRow;
import com.hbue.ordering.coupon.model.CouponTemplateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 优惠券模板访问接口；库存预占和状态变更使用带条件的数据库更新防止并发越权。
 */
public interface CouponTemplateMapper extends BaseMapper<CouponTemplateDO> {

    /**
     * 领取清单最多返回 100 个模板，并由数据库时间与剩余库存共同筛选。
     */
    @Select("""
            SELECT t.coupon_template_id AS templateId, t.title AS title,
                   t.min_spend AS minSpend, t.discount_amount AS discountAmount,
                   t.total_quantity AS totalQuantity,
                   (t.total_quantity - t.claimed_quantity) AS remainingQuantity,
                   t.claim_start_at AS claimStartAt, t.claim_end_at AS claimEndAt,
                   t.valid_from AS validFrom, t.valid_until AS validUntil,
                   t.scope_type AS scopeType, t.status AS status,
                   tw.window_id AS windowId
            FROM (
              SELECT * FROM coupon_template
              WHERE status = 'PUBLISHED'
                AND claim_start_at <= NOW() AND claim_end_at > NOW()
                AND claimed_quantity < total_quantity
              ORDER BY claim_end_at ASC, coupon_template_id ASC
              LIMIT 100
            ) t
            LEFT JOIN coupon_template_window tw
              ON tw.coupon_template_id = t.coupon_template_id
            ORDER BY t.claim_end_at ASC, t.coupon_template_id ASC
            """)
    List<CouponTemplateRow> listClaimableRows();

    /**
     * 管理列表有硬上限，避免无分页参数时读取全部历史模板。
     */
    @Select("""
            SELECT t.coupon_template_id AS templateId, t.title AS title,
                   t.min_spend AS minSpend, t.discount_amount AS discountAmount,
                   t.total_quantity AS totalQuantity,
                   (t.total_quantity - t.claimed_quantity) AS remainingQuantity,
                   t.claim_start_at AS claimStartAt, t.claim_end_at AS claimEndAt,
                   t.valid_from AS validFrom, t.valid_until AS validUntil,
                   t.scope_type AS scopeType, t.status AS status,
                   tw.window_id AS windowId
            FROM (
              SELECT * FROM coupon_template
              ORDER BY created_at DESC, coupon_template_id DESC
              LIMIT 200
            ) t
            LEFT JOIN coupon_template_window tw
              ON tw.coupon_template_id = t.coupon_template_id
            ORDER BY t.created_at DESC, t.coupon_template_id DESC
            """)
    List<CouponTemplateRow> listAdminRows();

    /**
     * 对模板行加锁，串行化同模板的停止发放、停止使用和领取确认。
     */
    @Select("SELECT * FROM coupon_template WHERE coupon_template_id = #{templateId} FOR UPDATE")
    CouponTemplateDO findByIdForUpdate(@Param("templateId") Long templateId);

    /**
     * 使用单条带条件更新预占发放量；并发领取最后一张券时最多一条请求成功。
     */
    @Update("""
            UPDATE coupon_template
            SET claimed_quantity = claimed_quantity + 1
            WHERE coupon_template_id = #{templateId}
              AND status = 'PUBLISHED'
              AND claim_start_at <= NOW() AND claim_end_at > NOW()
              AND claimed_quantity < total_quantity
            """)
    int reserveClaim(@Param("templateId") Long templateId);
}
