package com.hbue.ordering.coupon.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 订单服务保存优惠快照所需的券规则结果，不暴露券服务持久化对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponLockVO {

    /** 成功锁定的用户券记录主键。 */
    private Long userCouponId;

    /** 券模板主键，便于订单历史和统计关联。 */
    private Long templateId;

    /** 下单时固定的优惠券名称快照。 */
    private String title;

    /** 服务端依门槛规则计算并锁定的优惠金额。 */
    private BigDecimal discountAmount;
}
