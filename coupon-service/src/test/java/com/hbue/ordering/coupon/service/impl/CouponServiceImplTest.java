package com.hbue.ordering.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.coupon.api.dto.CouponLockRequest;
import com.hbue.ordering.coupon.api.dto.CouponPaidEvent;
import com.hbue.ordering.coupon.enums.CouponTemplateStatus;
import com.hbue.ordering.coupon.enums.UserCouponStatus;
import com.hbue.ordering.coupon.mapper.*;
import com.hbue.ordering.coupon.model.CouponAdminActionDO;
import com.hbue.ordering.coupon.model.CouponTemplateDO;
import com.hbue.ordering.coupon.model.CouponUseRecordDO;
import com.hbue.ordering.coupon.model.UserCouponDO;
import com.hbue.ordering.coupon.service.CouponAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 券锁定边界的轻量单元断言，不依赖数据库或 RocketMQ。
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {
    @Mock
    private CouponTemplateMapper templateMapper;
    @Mock
    private CouponTemplateWindowMapper templateWindowMapper;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private CouponUseRecordMapper useRecordMapper;
    @Mock
    private CouponAdminActionMapper adminActionMapper;
    @Mock
    private CouponAuthorizationService authorizationService;

    private CouponServiceImpl service;

    /**
     * 为当前测试创建仅使用 mock mapper 的券业务实现。
     */
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

    /**
     * 窗口范围不匹配时必须在任何状态写入前拒绝锁券。
     */
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
        verify(useRecordMapper, never()).insert(any(CouponUseRecordDO.class));
    }

    /**
     * 低于满减门槛的订单不得锁券或写入成本流水。
     */
    @Test
    void lockRejectsAmountBelowThresholdBeforeChangingState() {
        UserCouponDO userCoupon = availableCoupon();
        CouponTemplateDO template = activeCampusTemplate();
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);

        assertThrows(BusinessException.class, () -> service.lock(lockRequest(21L, "ORDER-1", "29.99")));
        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).insert(any(CouponUseRecordDO.class));
    }

    /**
     * 不能将其他学生的持券记录绑定到当前订单。
     */
    @Test
    void lockRejectsCouponOwnedByAnotherStudentBeforeChangingState() {
        UserCouponDO userCoupon = availableCoupon();
        userCoupon.setUserId(99L);
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);

        assertThrows(BusinessException.class, () -> service.lock(lockRequest(21L, "ORDER-1")));

        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).insert(any(CouponUseRecordDO.class));
    }

    /**
     * 已过期持券不能因订单请求到达而重新锁定或写入成本流水。
     */
    @Test
    void lockRejectsExpiredCouponBeforeChangingState() {
        UserCouponDO userCoupon = availableCoupon();
        userCoupon.setValidUntil(LocalDateTime.now().minusSeconds(1));
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);

        assertThrows(BusinessException.class, () -> service.lock(lockRequest(21L, "ORDER-1")));

        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).insert(any(CouponUseRecordDO.class));
    }

    /**
     * 合格满减券只向订单返回服务端模板金额，不使用调用方提交的折扣额。
     */
    @Test
    void lockReturnsTemplateDiscountForEligibleOrder() {
        UserCouponDO userCoupon = availableCoupon();
        CouponTemplateDO template = activeCampusTemplate();
        when(userCouponMapper.selectById(8L)).thenReturn(userCoupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);
        when(userCouponMapper.findByIdForUpdate(8L)).thenReturn(userCoupon);
        when(userCouponMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(useRecordMapper.insert(any(CouponUseRecordDO.class))).thenReturn(1);

        var locked = service.lock(lockRequest(21L, "ORDER-1", "30.00"));

        assertEquals(new BigDecimal("5.00"), locked.getDiscountAmount());
        verify(useRecordMapper).insert(any(CouponUseRecordDO.class));
    }

    /**
     * 同一订单重试必须复用原锁券流水和面额，不创建第二条成本记录。
     */
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
        verify(useRecordMapper, never()).insert(any(CouponUseRecordDO.class));
    }

    /**
     * 库存条件更新失败时不插入用户券，避免最后一张券并发超发。
     */
    @Test
    void claimDoesNotInsertWhenTemplateInventoryIsExhausted() {
        when(templateMapper.selectById(3L)).thenReturn(activeCampusTemplate());
        when(templateMapper.reserveClaim(3L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.claim(12L, 3L));

        verify(userCouponMapper, never()).insert(any(UserCouponDO.class));
    }

    /**
     * 每人每模板唯一键冲突时拒绝重复领取，由事务回滚此前的库存预占。
     */
    @Test
    void claimRejectsDuplicateUserTemplateConstraint() {
        when(templateMapper.selectById(3L)).thenReturn(activeCampusTemplate());
        when(templateMapper.reserveClaim(3L)).thenReturn(1);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(userCouponMapper).insert(any(UserCouponDO.class));

        assertThrows(BusinessException.class, () -> service.claim(12L, 3L));

        verify(templateMapper).reserveClaim(3L);
        verify(userCouponMapper).insert(any(UserCouponDO.class));
    }

    /**
     * 已核销的同订单消息重放按幂等成功，不重复修改持券状态或成本流水。
     */
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

    /**
     * 释放仍在有效期且模板未停用的券：应回到 AVAILABLE 并写入释放流水。
     */
    @Test
    void releaseRestoresAvailableWhenStillValidAndTemplatePublished() {
        UserCouponDO coupon = lockedCoupon(LocalDateTime.now().plusDays(1));
        stubRelease(coupon, activeCampusTemplate());

        service.release("ORDER-1");

        assertTrue(setsValue(capturedCouponUpdate(), UserCouponStatus.AVAILABLE.name()));
        verify(useRecordMapper).markReleased(eq(8L), eq("ORDER-1"), any(LocalDateTime.class));
    }

    /**
     * 释放时券已过期：即使模板仍启用也必须落到 EXPIRED，不能恢复成可用。
     */
    @Test
    void releaseTransitionsExpiredCouponToExpired() {
        UserCouponDO coupon = lockedCoupon(LocalDateTime.now().minusSeconds(1));
        stubRelease(coupon, activeCampusTemplate());

        service.release("ORDER-1");

        assertTrue(setsValue(capturedCouponUpdate(), UserCouponStatus.EXPIRED.name()));
    }

    /**
     * 模板已停止使用：释放时不得恢复 AVAILABLE，必须撤销为 REVOKED。
     */
    @Test
    void releaseTransitionsCouponToRevokedWhenTemplateStopped() {
        UserCouponDO coupon = lockedCoupon(LocalDateTime.now().plusDays(1));
        CouponTemplateDO template = activeCampusTemplate();
        template.setStatus(CouponTemplateStatus.STOPPED.name());
        stubRelease(coupon, template);

        service.release("ORDER-1");

        assertTrue(setsValue(capturedCouponUpdate(), UserCouponStatus.REVOKED.name()));
    }

    /**
     * 过期与模板停用同时成立时以过期优先，保持"时间事实先于管理动作"的分支顺序。
     */
    @Test
    void releasePrefersExpiredOverRevokedWhenBothApply() {
        UserCouponDO coupon = lockedCoupon(LocalDateTime.now().minusSeconds(1));
        CouponTemplateDO template = activeCampusTemplate();
        template.setStatus(CouponTemplateStatus.STOPPED.name());
        stubRelease(coupon, template);

        service.release("ORDER-1");

        assertTrue(setsValue(capturedCouponUpdate(), UserCouponStatus.EXPIRED.name()));
    }

    /**
     * 订单从未锁券（或已释放）时重复释放必须幂等返回，不写任何状态或流水。
     */
    @Test
    void releaseIsNoOpWhenNoCouponIsLockedByOrder() {
        when(userCouponMapper.selectOne(any())).thenReturn(null);

        service.release("ORDER-1");

        verify(templateMapper, never()).findByIdForUpdate(any());
        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).markReleased(any(), any(), any());
    }

    /**
     * 并发下持券已被其它事务释放（状态不再是 LOCKED）时不重复迁移，保证释放幂等。
     */
    @Test
    void releaseIsNoOpWhenCouponAlreadyLeftLockedState() {
        UserCouponDO snapshot = lockedCoupon(LocalDateTime.now().plusDays(1));
        UserCouponDO released = lockedCoupon(LocalDateTime.now().plusDays(1));
        released.setStatus(UserCouponStatus.AVAILABLE.name());
        when(userCouponMapper.selectOne(any())).thenReturn(snapshot);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(userCouponMapper.findByLockedOrderForUpdate("ORDER-1")).thenReturn(released);

        service.release("ORDER-1");

        verify(userCouponMapper, never()).update(isNull(), any(Wrapper.class));
        verify(useRecordMapper, never()).markReleased(any(), any(), any());
    }

    /**
     * 条件更新未命中说明状态被并发改动，必须报冲突并回滚，不能留下不一致的释放流水。
     */
    @Test
    void releaseThrowsWhenConditionalUpdateLosesRace() {
        UserCouponDO coupon = lockedCoupon(LocalDateTime.now().plusDays(1));
        when(userCouponMapper.selectOne(any())).thenReturn(coupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(userCouponMapper.findByLockedOrderForUpdate("ORDER-1")).thenReturn(coupon);
        when(userCouponMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.release("ORDER-1"));

        verify(useRecordMapper, never()).markReleased(any(), any(), any());
    }

    /**
     * 停止使用：模板置 STOPPED、撤销未锁定券并写审计，三者必须同一事务完成。
     */
    @Test
    void stopUseRevokesUnlockedCouponsAndRecordsAudit() {
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(templateMapper.updateById(any(CouponTemplateDO.class))).thenReturn(1);
        when(adminActionMapper.insert(any(CouponAdminActionDO.class))).thenReturn(1);

        service.stopUse(77L, 3L);

        ArgumentCaptor<CouponTemplateDO> template = ArgumentCaptor.forClass(CouponTemplateDO.class);
        verify(templateMapper).updateById(template.capture());
        assertEquals(CouponTemplateStatus.STOPPED.name(), template.getValue().getStatus());
        verify(userCouponMapper).revokeAvailable(3L);
        ArgumentCaptor<CouponAdminActionDO> audit = ArgumentCaptor.forClass(CouponAdminActionDO.class);
        verify(adminActionMapper).insert(audit.capture());
        assertEquals("STOP_USE", audit.getValue().getActionType());
        assertEquals(CouponTemplateStatus.STOPPED.name(), audit.getValue().getNewStatus());
    }

    /**
     * 模板已停止使用时重复调用必须幂等，不再撤销或重复写审计。
     */
    @Test
    void stopUseIsIdempotentWhenTemplateAlreadyStopped() {
        CouponTemplateDO template = activeCampusTemplate();
        template.setStatus(CouponTemplateStatus.STOPPED.name());
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);

        service.stopUse(77L, 3L);

        verify(templateMapper, never()).updateById(any(CouponTemplateDO.class));
        verify(userCouponMapper, never()).revokeAvailable(any());
        verify(adminActionMapper, never()).insert(any(CouponAdminActionDO.class));
    }

    /**
     * 已发布模板重复发布必须幂等返回，不重复写审计。
     */
    @Test
    void publishTemplateIsIdempotentWhenAlreadyPublished() {
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());

        service.publishTemplate(77L, 3L);

        verify(templateMapper, never()).update(isNull(), any(Wrapper.class));
        verify(adminActionMapper, never()).insert(any(CouponAdminActionDO.class));
    }

    /**
     * 草稿以外且非已发布的状态不能发布，条件更新与审计都不得发生。
     */
    @Test
    void publishTemplateRejectsStoppedTemplate() {
        CouponTemplateDO template = activeCampusTemplate();
        template.setStatus(CouponTemplateStatus.STOPPED.name());
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);

        assertThrows(BusinessException.class, () -> service.publishTemplate(77L, 3L));

        verify(templateMapper, never()).update(isNull(), any(Wrapper.class));
        verify(adminActionMapper, never()).insert(any(CouponAdminActionDO.class));
    }

    /**
     * 停止领取只阻止新领取，已发券不会被撤销；状态与审计必须一起写入。
     */
    @Test
    void stopClaimsTransitionsPublishedTemplateOnly() {
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(activeCampusTemplate());
        when(templateMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(adminActionMapper.insert(any(CouponAdminActionDO.class))).thenReturn(1);

        service.stopClaims(77L, 3L);

        ArgumentCaptor<Wrapper> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(templateMapper).update(isNull(), update.capture());
        assertTrue(setsValue(update.getValue(), CouponTemplateStatus.CLAIM_STOPPED.name()));
        // 停止领取不是停止使用：不得撤销任何已领取的券。
        verify(userCouponMapper, never()).revokeAvailable(any());
    }

    /**
     * 建立锁定状态、指定有效期的学生券测试夹具。
     */
    private UserCouponDO lockedCoupon(LocalDateTime validUntil) {
        UserCouponDO coupon = availableCoupon();
        coupon.setStatus(UserCouponStatus.LOCKED.name());
        coupon.setLockedOrderNo("ORDER-1");
        coupon.setValidUntil(validUntil);
        return coupon;
    }

    /**
     * 为释放路径准备快照查询、模板锁、用户券锁与条件更新。
     */
    private void stubRelease(UserCouponDO coupon, CouponTemplateDO template) {
        when(userCouponMapper.selectOne(any())).thenReturn(coupon);
        when(templateMapper.findByIdForUpdate(3L)).thenReturn(template);
        when(userCouponMapper.findByLockedOrderForUpdate("ORDER-1")).thenReturn(coupon);
        when(userCouponMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(useRecordMapper.markReleased(eq(8L), eq("ORDER-1"), any(LocalDateTime.class))).thenReturn(1);
    }

    /**
     * 捕获释放时提交给用户券表的条件更新包装器。
     */
    @SuppressWarnings("rawtypes")
    private Wrapper capturedCouponUpdate() {
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userCouponMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    /**
     * 判断条件更新包装器是否以指定值设置字段；用于断言状态迁移的目标状态，而不是只看是否调用。
     */
    private static boolean setsValue(Wrapper<?> wrapper, Object expected) {
        return ((UpdateWrapper<?>) wrapper).getParamNameValuePairs().containsValue(expected);
    }

    /**
     * 建立有效的学生券记录测试夹具。
     */
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

    /**
     * 建立适用于指定窗口的有效满减模板测试夹具。
     */
    private CouponTemplateDO activeWindowTemplate() {
        CouponTemplateDO template = activeCampusTemplate();
        template.setScopeType("WINDOWS");
        return template;
    }

    /**
     * 建立全校适用、满 30 减 5 的模板测试夹具。
     */
    private CouponTemplateDO activeCampusTemplate() {
        CouponTemplateDO template = new CouponTemplateDO();
        template.setCouponTemplateId(3L);
        template.setMinSpend(new BigDecimal("30.00"));
        template.setDiscountAmount(new BigDecimal("5.00"));
        template.setScopeType("CAMPUS");
        template.setStatus(CouponTemplateStatus.PUBLISHED.name());
        return template;
    }

    /**
     * 创建服务间锁券请求，金额由订单服务核实后提供。
     */
    private CouponLockRequest lockRequest(Long windowId, String orderNo) {
        return lockRequest(windowId, orderNo, "30.00");
    }

    /**
     * 创建带指定适用金额的服务间锁券请求。
     */
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
