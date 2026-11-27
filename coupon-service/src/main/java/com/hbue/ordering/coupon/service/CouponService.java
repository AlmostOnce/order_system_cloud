package com.hbue.ordering.coupon.service;

import com.hbue.ordering.coupon.api.dto.CouponLockRequest;
import com.hbue.ordering.coupon.api.dto.CouponPaidEvent;
import com.hbue.ordering.coupon.api.vo.CouponLockVO;
import com.hbue.ordering.coupon.dto.CouponTemplateCreateRequest;
import com.hbue.ordering.coupon.vo.CouponTemplateVO;
import com.hbue.ordering.coupon.vo.UserCouponVO;

import java.util.List;

/**
 * 优惠券业务边界，聚合学生领取、管理状态变更和订单内部生命周期操作。
 */
public interface CouponService {
    /**
     * 查询当前处于领取时段且仍有库存的模板。
     */
    List<CouponTemplateVO> listClaimable();

    /**
     * 为当前学生领取一张券；库存、每人限制和持券写入处于同一数据库事务。
     */
    UserCouponVO claim(Long userId, Long templateId);

    /**
     * 查询本人券；传入状态时按有限状态值过滤。
     */
    List<UserCouponVO> listMine(Long userId, String status);

    /**
     * 查询平台券管理清单，结果有硬上限以控制数据库读取量。
     */
    List<CouponTemplateVO> listAdminTemplates(Long operatorId);

    /**
     * 创建未发布的平台预算满减券模板。
     */
    CouponTemplateVO createTemplate(Long operatorId, CouponTemplateCreateRequest request);

    /**
     * 发布已创建模板；领取时间到达后学生即可看到并领取。
     */
    void publishTemplate(Long operatorId, Long templateId);

    /**
     * 停止新增领取，已领取券仍可在原有效期内使用。
     */
    void stopClaims(Long operatorId, Long templateId);

    /**
     * 停止新锁券，并撤销尚未锁定的用户券。
     */
    void stopUse(Long operatorId, Long templateId);

    /**
     * 在订单全局事务内校验并锁定用户券。
     */
    CouponLockVO lock(CouponLockRequest request);

    /**
     * 仅释放关联到订单的锁定券；重复调用是安全空操作。
     */
    void release(String orderNo);

    /**
     * 消费支付成功事件并幂等地核销对应锁定券。
     */
    void markUsed(CouponPaidEvent event);
}
