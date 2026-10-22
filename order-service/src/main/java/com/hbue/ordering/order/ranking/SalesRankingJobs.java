package com.hbue.ordering.order.ranking;

import com.hbue.ordering.order.service.SalesRankingService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SalesRankingJobs {
    private final SalesRankingService rankings;
    public SalesRankingJobs(SalesRankingService rankings) { this.rankings = rankings; }

    /** 冷启动由查询或消费触发；周期重建修复漏消息与 Redis 数据损失。 */
    @Scheduled(initialDelayString = "${ranking.reconcile-ms:300000}", fixedDelayString = "${ranking.reconcile-ms:300000}")
    public void reconcile() { rankings.reconcile(); }
}
