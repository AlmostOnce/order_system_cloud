package com.hbue.ordering.statistics.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.client.OrderSalesInternalClient;
import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;
import com.hbue.ordering.statistics.messaging.SalesPaidEvent;
import com.hbue.ordering.statistics.service.SalesProjectionService;
import com.hbue.ordering.statistics.service.SalesRankingCacheService;
import com.hbue.ordering.statistics.service.SalesStatisticsService;
import com.hbue.ordering.statistics.vo.SalesRankingVO;
import com.hbue.ordering.statistics.vo.WindowMonthlySalesVO;
import com.hbue.ordering.statistics.vo.WindowProductMonthlySalesVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 统计业务编排实现。
 *
 * <p>HTTP 查询只读本地投影；RocketMQ 新事件使用消息携带的快照，只有旧版 ID-only 事件才通过
 * 受内部令牌保护的订单接口回读支付快照，避免为兼容历史消息而长期保留跨服务同步调用。</p>
 *
 * <p>本类不注入任何 Mapper：所有数据访问都经 {@link SalesProjectionService} 与
 * {@link SalesRankingCacheService}，保证同一模块只有一个数据访问入口。</p>
 */
@Service
public class SalesStatisticsServiceImpl implements SalesStatisticsService {

    /**
     * 销量投影读写服务。
     */
    private final SalesProjectionService projectionService;

    /**
     * 累计榜单缓存服务。
     */
    private final SalesRankingCacheService rankingCache;

    /**
     * 订单服务内部快照客户端，仅为旧版 ID-only 消息保留。
     */
    private final OrderSalesInternalClient orders;

    /**
     * 服务间共享内部令牌，调用订单内部接口时通过参数传递。
     */
    private final String internalToken;

    /**
     * 注入本地投影、榜单缓存及仅供历史消息兼容的受保护订单快照客户端。
     */
    public SalesStatisticsServiceImpl(SalesProjectionService projectionService,
                                     SalesRankingCacheService rankingCache,
                                     OrderSalesInternalClient orders,
                                     @Value("${internal.scope.token}") String internalToken) {
        this.projectionService = projectionService;
        this.rankingCache = rankingCache;
        this.orders = orders;
        this.internalToken = internalToken;
    }

    /**
     * 查询累计榜，limit 的范围由缓存服务层再做一次边界校验。
     */
    @Override
    public SalesRankingVO listRanking(int limit) {
        return rankingCache.list(limit);
    }

    /**
     * 消费已支付订单并持久化其事实与汇总，再更新可重建的 Redis ZSet。
     *
     * <p>顺序不可颠倒：先写 MySQL 并拿到版本号，事务提交后才更新 Redis。Redis 更新失败会让
     * 异常向外传播触发 Broker 重投，届时事件幂等判重保证汇总不会被重复累加。</p>
     *
     * @param event RocketMQ 收到的 V1 或 V2 事件
     * @throws RuntimeException MySQL、Redis 或订单兼容读取失败时必须向上抛出，让 Broker 重试
     */
    @Override
    public void consume(SalesPaidEvent event) {
        if (event == null || !event.valid()) throw new IllegalArgumentException("销量事件格式不正确");
        if (event.version() == 1 && projectionService.alreadyRecorded(event.eventId())) {
            // 历史迁移已先写入事实和汇总；跳过老队列重放，后续榜单缺失时由 MySQL 全量重建。
            return;
        }
        OrderPaidSalesSnapshotVO snapshot = event.version() == 2 ? event.snapshot() : readLegacySnapshot(event.orderId());
        long version = projectionService.record(event, snapshot);
        rankingCache.apply(event, snapshot, version);
    }

    /**
     * 首页批量读取所有窗口当前自然月总份数，数据访问全部下沉到投影服务。
     */
    @Override
    public WindowMonthlySalesVO listWindowMonthlySales() {
        return projectionService.listWindowMonthlySales();
    }

    /**
     * 点餐页读取单个窗口总月售与菜品月售，窗口编号边界校验由投影服务负责。
     */
    @Override
    public WindowProductMonthlySalesVO getWindowMonthlySales(Long windowId) {
        return projectionService.getWindowMonthlySales(windowId);
    }

    /**
     * 仅为旧版 ID-only 消息回读订单快照；任何服务不可用或响应缺失都抛错，不能确认丢弃事件。
     */
    private OrderPaidSalesSnapshotVO readLegacySnapshot(Long orderId) {
        ApiResponse<OrderPaidSalesSnapshotVO> response = orders.getPaidSalesSnapshot(orderId, internalToken);
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode()) || response.getData() == null) {
            throw new IllegalStateException("旧版销量消息无法从订单服务读取支付快照");
        }
        return response.getData();
    }
}
