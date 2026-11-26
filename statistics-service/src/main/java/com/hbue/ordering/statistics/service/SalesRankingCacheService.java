package com.hbue.ordering.statistics.service;

import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;
import com.hbue.ordering.statistics.messaging.SalesPaidEvent;
import com.hbue.ordering.statistics.vo.SalesRankingVO;

/**
 * 累计销量榜缓存边界。
 *
 * <p>读取面向学生端，缓存缺失或落后时由实现自行按 MySQL 权威汇总重建；写入侧在销量事实事务
 * 提交后增量维护缓存，失败必须向上抛出，让 MQ 重投而不是静默丢消息。</p>
 *
 * <p>调用方只依赖本接口，不感知 Redis Key 布局、Lua 脚本与分布式锁细节。</p>
 */
public interface SalesRankingCacheService {

    /**
     * 读取累计榜。
     *
     * @param limit 榜单数量，限定 1 至 100，避免无界缓存读取
     * @return 按销量降序排列的榜单
     */
    SalesRankingVO list(int limit);

    /**
     * 在销量事实事务提交后增量维护榜单缓存。
     *
     * @param event                  已验证的稳定订单事件
     * @param snapshot               事件对应的原始购买快照
     * @param eventProjectionVersion 持久化事件事务提交后的 MySQL 版本
     */
    void apply(SalesPaidEvent event, OrderPaidSalesSnapshotVO snapshot, long eventProjectionVersion);

    /**
     * 周期对账：从权威 MySQL 汇总全量重建缓存，修复 MQ 死信或缓存丢失造成的投影遗漏。
     *
     * <p>实现内部吞掉并记录异常，避免调度线程因单次 Redis 故障被终止。</p>
     */
    void reconcile();
}
