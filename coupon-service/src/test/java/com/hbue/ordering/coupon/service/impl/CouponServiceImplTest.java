package com.hbue.ordering.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.coupon.api.dto.CouponLockRequest;
import com.hbue.ordering.coupon.api.dto.CouponPaidEvent;
import com.hbue.ordering.coupon.enums.CouponTemplateStatus;
import com.hbue.ordering.coupon.enums.UserCouponStatus;
import com.hbue.ordering.coupon.mapper.CouponAdminActionMapper;
import com.hbue.ordering.coupon.mapper.CouponTemplateMapper;
import com.hbue.ordering.coupon.mapper.CouponTemplateWindowMapper;
import com.hbue.ordering.coupon.mapper.CouponUseRecordMapper;
import com.hbue.ordering.coupon.mapper.UserCouponMapper;
import com.hbue.ordering.coupon.model.CouponTemplateDO;
import com.hbue.ordering.coupon.model.CouponUseRecordDO;
import com.hbue.ordering.coupon.model.UserCouponDO;
import com.hbue.ordering.coupon.service.CouponAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 券锁定边界的轻量单元断言，不依赖数据库或 RocketMQ。 */
@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {
    @Mock private CouponTemplateMapper templateMapper;
    @Mock private CouponTemplateWindowMapper templateWindowMapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private CouponUseRecordMapper useRecordMapper;
    @Mock private CouponAdminActionMapper adminActionMapper;
    @Mock private CouponAuthorizationService authorizationService;

    private CouponServiceImpl service;

    /** 为当前测试创建仅使用 mock mapper 的券业务实现。 */
    @BeforeEach
    void setUp() {
        service = new CouponServiceImpl(
                templateMapper,
                templateWindowMapper,
                userCouponMapper,
                useRecordMapper,
                adminActionMapper,
                authorizationService
        );
    }

    /** 窗口范围不匹配时必须在任何状态写入前拒绝锁券。 */
    @Test
    void lockRejectsCouponOutsideWindowBeforeChangingState() {
        UserCouponDO userCoupon = availableCoupon();
        CouponTemplateDO template = activeWindowTemplate();
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);
        when(templateWindowMapper.countWindow(3L, 21L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.lock(lockRequest(21L, "ORDER-1")));
        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).insert(any());
    }

    /** 低于满减门槛的订单不得锁券或写入成本流水。 */
    @Test
    void lockRejectsAmountBelowThresholdBeforeChangingState() {
        UserCouponDO userCoupon = availableCoupon();
        CouponTemplateDO template = activeCampusTemplate();
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);

        assertThrows(BusinessException.class, () -> service.lock(lockRequest(21L, "ORDER-1", "29.99")));
        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).insert(any());
    }

    /** 不能将其他学生的持券记录绑定到当前订单。 */
    @Test
    void lockRejectsCouponOwnedByAnotherStudentBeforeChangingState() {
        UserCouponDO userCoupon = availableCoupon();
        userCoupon.setUserId(99L);
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);

        assertThrows(BusinessException.class, () -> service.lock(lockRequest(21L, "ORDER-1")));

        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).insert(any());
    }

    /** 已过期持券不能因订单请求到达而重新锁定或写入成本流水。 */
    @Test
    void lockRejectsExpiredCouponBeforeChangingState() {
        UserCouponDO userCoupon = availableCoupon();
        userCoupon.setValidUntil(LocalDateTime.now().minusSeconds(1));
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);

        assertThrows(BusinessException.class, () -> service.lock(lockRequest(21L, "ORDER-1")));

        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).insert(any());
    }

    /** 合格满减券只向订单返回服务端模板金额，不使用调用方提交的折扣额。 */
    @Test
    void lockReturnsTemplateDiscountForEligibleOrder() {
        UserCouponDO userCoupon = availableCoupon();
        CouponTemplateDO template = activeCampusTemplate();
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);
        when(userCouponMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(useRecordMapper.insert(any())).thenReturn(1);

        var locked = service.lock(lockRequest(21L, "ORDER-1", "30.00"));

        assertEquals(new BigDecimal("5.00"), locked.getDiscountAmount());
        verify(useRecordMapper).insert(any());
    }

    /** 同一订单重试必须复用原锁券流水和面额，不创建第二条成本记录。 */
    @Test
    void lockRetryForSameOrderReturnsOriginalDiscountWithoutWritingAgain() {
        UserCouponDO userCoupon = availableCoupon();
        userCoupon.setStatus(UserCouponStatus.LOCKED.name());
        userCoupon.setLockedOrderNo("ORDER-1");
        CouponTemplateDO template = activeCampusTemplate();
        CouponUseRecordDO existing = new CouponUseRecordDO();
        existing.setStatus("LOCKED");
        existing.setDiscountAmount(new BigDecimal("4.00"));
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);
        when(useRecordMapper.findByCouponAndOrder(8L, "ORDER-1")).thenReturn(existing);

        var result = service.lock(lockRequest(21L, "ORDER-1"));

        assertEquals(new BigDecimal("4.00"), result.getDiscountAmount());
        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).insert(any());
    }

    /** 库存条件更新失败时不插入用户券，避免最后一张券并发超发。 */
    @Test
    void claimDoesNotInsertWhenTemplateInventoryIsExhausted() {
        when(templateMapper.selectById(3L)).thenReturn(activeCampusTemplate());
        when(templateMapper.reserveClaim(3L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.claim(12L, 3L));

        verify(userCouponMapper, never()).insert(any());
    }

    /** 每人每模板唯一键冲突时拒绝重复领取，由事务回滚此前的库存预占。 */
    @Test
    void claimRejectsDuplicateUserTemplateConstraint() {
        when(templateMapper.selectById(3L)).thenReturn(activeCampusTemplate());
        when(templateMapper.reserveClaim(3L)).thenReturn(1);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(userCouponMapper).insert(any());

        assertThrows(BusinessException.class, () -> service.claim(12L, 3L));

        verify(templateMapper).reserveClaim(3L);
        verify(userCouponMapper).insert(any());
    }

    /** 已核销的同订单消息重放按幂等成功，不重复修改持券状态或成本流水。 */
    @Test
    void markUsedReplayForSameLockedOrderIsIdempotent() {
        UserCouponDO userCoupon = availableCoupon();
        userCoupon.setStatus(UserCouponStatus.USED.name());
        userCoupon.setLockedOrderNo("ORDER-1");
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);

        service.markUsed(new CouponPaidEvent("event-1", "ORDER-1", 8L));

        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).markUsed(any(), any(), any());
    }

    /** 建立有效的学生券记录测试夹具。 */
    private UserCouponDO availableCoupon() {
        UserCouponDO coupon = new UserCouponDO();
        coupon.setUserCouponId(8L);
        coupon.setCouponTemplateId(3L);
        coupon.setUserId(12L);
        coupon.setStatus(UserCouponStatus.AVAILABLE.name());
        coupon.setValidFrom(LocalDateTime.now().minusDays(1));
        coupon.setValidUntil(LocalDateTime.now().plusDays(1));
        return coupon;
    }

    /** 建立适用于指定窗口的有效满减模板测试夹具。 */
    private CouponTemplateDO activeWindowTemplate() {
        CouponTemplateDO template = activeCampusTemplate();
        template.setScopeType("WINDOWS");
        return template;
    }

    /** 建立全校适用、满 30 减 5 的模板测试夹具。 */
    private CouponTemplateDO activeCampusTemplate() {
        CouponTemplateDO template = new CouponTemplateDO();
        template.setCouponTemplateId(3L);
        template.setMinSpend(new BigDecimal("30.00"));
        template.setDiscountAmount(new BigDecimal("5.00"));
        template.setScopeType("CAMPUS");
        template.setStatus(CouponTemplateStatus.PUBLISHED.name());
        return template;
    }

    /** 创建服务间锁券请求，金额由订单服务核实后提供。 */
    private CouponLockRequest lockRequest(Long windowId, String orderNo) {
        return lockRequest(windowId, orderNo, "30.00");
    }

    /** 创建带指定适用金额的服务间锁券请求。 */
    private CouponLockRequest lockRequest(Long windowId, String orderNo, String eligibleAmount) {
        return CouponLockRequest.builder()
                .userCouponId(8L)
                .userId(12L)
                .orderNo(orderNo)
                .windowId(windowId)
                .eligibleAmount(new BigDecimal(eligibleAmount))
                .build();
    }
}
