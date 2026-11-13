package com.hbue.ordering.order.ranking;

import com.hbue.ordering.order.service.SalesRankingService;
/** 已停用的订单服务旧榜单对账任务，源码保留以便整理旧用例时核对迁移前行为。 */
@Deprecated(forRemoval = false)
public class SalesRankingJobs {
    private final SalesRankingService rankings;
    public SalesRankingJobs(SalesRankingService rankings) { this.rankings = rankings; }

    /** 冷启动由查询或消费触发；周期重建修复漏消息与 Redis 数据损失。 */
    public void reconcile() { rankings.reconcile(); }
}
