package com.hbue.ordering.recommendation.task;

import com.hbue.ordering.common.redis.ScheduledTaskLock;
import com.hbue.ordering.recommendation.service.RecommendationPopularityService;
import com.hbue.ordering.recommendation.service.RecommendationProductCatalogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 推荐定时任务分布式互斥测试：验证抢不到租约时整轮跳过，抢到后按原逻辑执行并释放。
 *
 * @author order-system
 * @date 2026-11-27
 */
class RecommendationRefreshTaskLockTest {

    /**
     * 其它实例持锁时，目录刷新任务不能访问目录服务。
     */
    @Test
    void catalogTaskShouldSkipWhenLeaseNotAcquired() {
        RecommendationProductCatalogService catalog = mock(RecommendationProductCatalogService.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        when(taskLock.tryAcquire(anyString(), any())).thenReturn(null);

        new RecommendationProductCatalogRefreshTask(catalog, taskLock).refreshAllWindows();

        verifyNoInteractions(catalog);
    }

    /**
     * 抢到租约后逐个窗口刷新，并把完整窗口清单交给撤架逻辑。
     */
    @Test
    void catalogTaskShouldRefreshEveryWindowWithinLease() {
        RecommendationProductCatalogService catalog = mock(RecommendationProductCatalogService.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        ScheduledTaskLock.Lease lease = mock(ScheduledTaskLock.Lease.class);
        when(taskLock.tryAcquire(anyString(), any())).thenReturn(lease);
        when(catalog.windowIds()).thenReturn(List.of(1L, 2L));

        new RecommendationProductCatalogRefreshTask(catalog, taskLock).refreshAllWindows();

        verify(catalog).refreshWindow(1L);
        verify(catalog).refreshWindow(2L);
        verify(catalog).markWindowsMissing(List.of(1L, 2L));
        verify(lease).close();
    }

    /**
     * 其它实例持锁时，热榜刷新任务不能访问榜单服务。
     */
    @Test
    void popularityTaskShouldSkipWhenLeaseNotAcquired() {
        RecommendationPopularityService popularity = mock(RecommendationPopularityService.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        when(taskLock.tryAcquire(anyString(), any())).thenReturn(null);

        new RecommendationPopularityRefreshTask(popularity, taskLock).refresh();

        verifyNoInteractions(popularity);
    }

    /**
     * 抢到租约后刷新一次热榜并释放租约。
     */
    @Test
    void popularityTaskShouldRefreshWithinLease() {
        RecommendationPopularityService popularity = mock(RecommendationPopularityService.class);
        ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
        ScheduledTaskLock.Lease lease = mock(ScheduledTaskLock.Lease.class);
        when(taskLock.tryAcquire(anyString(), any())).thenReturn(lease);

        new RecommendationPopularityRefreshTask(popularity, taskLock).refresh();

        verify(popularity).refresh();
        verify(lease).close();
    }
}
