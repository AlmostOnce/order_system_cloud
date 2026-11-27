package com.hbue.ordering.coupon.task;

import com.hbue.ordering.common.redis.ScheduledTaskLock;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.client.CouponNotificationClient;
import com.hbue.ordering.coupon.client.vo.CouponExpiryNotificationRequest;
import com.hbue.ordering.coupon.dto.CouponExpiryReminderRow;
import com.hbue.ordering.coupon.mapper.UserCouponMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 优惠券到期提醒任务测试：验证多实例互斥、批量扫描与提醒成功后打标记的顺序。
 *
 * @author order-system
 * @date 2026-11-27
 */
class CouponExpiryReminderTaskTest {

    /**
     * 另一实例持锁时必须整轮跳过，不访问数据库也不调用通知服务。
     */
    @Test
    void shouldSkipWholeRoundWhenLeaseNotAcquired() {
        UserCouponMapper mapper = mock(UserCouponMapper.class);
        CouponNotificationClient notificationClient = mock(CouponNotificationClient.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        when(taskLock.tryAcquire(anyString(), any())).thenReturn(null);

        new CouponExpiryReminderTask(mapper, notificationClient, "internal-token", taskLock)
                .remindExpiring();

        verifyNoInteractions(mapper, notificationClient);
    }

    /**
     * 抢到租约但当天没有到期券时，仍需正常释放租约。
     */
    @Test
    void shouldScanWithinLeaseAndReleaseIt() {
        UserCouponMapper mapper = mock(UserCouponMapper.class);
        CouponNotificationClient notificationClient = mock(CouponNotificationClient.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        ScheduledTaskLock.Lease lease = mock(ScheduledTaskLock.Lease.class);
        when(taskLock.tryAcquire(anyString(), any())).thenReturn(lease);
        when(mapper.findExpiryReminderCandidates(200)).thenReturn(List.of());

        new CouponExpiryReminderTask(mapper, notificationClient, "internal-token", taskLock)
                .remindExpiring();

        verify(mapper).findExpiryReminderCandidates(200);
        verify(lease).close();
        verifyNoInteractions(notificationClient);
    }

    /**
     * 发送成功后必须写入提醒标记；通知失败时不能提前标记，保证下一轮还能重试。
     */
    @Test
    void shouldMarkExpiryRemindedOnlyAfterNotificationSucceeds() {
        UserCouponMapper mapper = mock(UserCouponMapper.class);
        CouponNotificationClient notificationClient = mock(CouponNotificationClient.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        when(taskLock.tryAcquire(anyString(), any())).thenReturn(mock(ScheduledTaskLock.Lease.class));
        CouponExpiryReminderRow success = reminderRow(7L, 9L);
        CouponExpiryReminderRow failure = reminderRow(8L, 10L);
        when(mapper.findExpiryReminderCandidates(200)).thenReturn(List.of(success, failure));
        when(notificationClient.sendCouponExpiring(eq("internal-token"), any(CouponExpiryNotificationRequest.class)))
                .thenAnswer(invocation -> {
                    CouponExpiryNotificationRequest request = invocation.getArgument(1);
                    // 第一张券成功，第二张券模拟通知服务失败。
                    if (request.userCouponId().equals(8L)) {
                        throw new IllegalStateException("notification down");
                    }
                    return ApiResponse.success(null);
                });
        when(mapper.markExpiryReminded(7L)).thenReturn(1);

        new CouponExpiryReminderTask(mapper, notificationClient, "internal-token", taskLock)
                .remindExpiring();

        // 只有成功的那张券被打标记，失败券保留给下一轮。
        verify(mapper).markExpiryReminded(7L);
        verify(mapper, never()).markExpiryReminded(8L);
    }

    /**
     * 构造一条待提醒券记录。
     *
     * @param userCouponId 用户券记录编号
     * @param userId       接收人编号
     * @return 扫描行
     */
    private CouponExpiryReminderRow reminderRow(Long userCouponId, Long userId) {
        CouponExpiryReminderRow row = new CouponExpiryReminderRow();
        row.setUserCouponId(userCouponId);
        row.setUserId(userId);
        row.setTitle("测试券");
        row.setValidUntil(LocalDateTime.now().plusHours(1));
        return row;
    }
}
