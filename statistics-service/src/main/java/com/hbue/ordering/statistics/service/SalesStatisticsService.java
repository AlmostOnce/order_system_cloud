package com.hbue.ordering.statistics.service;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.client.OrderSalesInternalClient;
import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;
import com.hbue.ordering.statistics.mapper.SalesProjectionMapper;
import com.hbue.ordering.statistics.messaging.SalesPaidEvent;
import com.hbue.ordering.statistics.model.SalesProductTotalDO;
import com.hbue.ordering.statistics.model.SalesWindowTotalDO;
import com.hbue.ordering.statistics.vo.SalesRankingVO;
import com.hbue.ordering.statistics.vo.WindowMonthlySalesVO;
import com.hbue.ordering.statistics.vo.WindowProductMonthlySalesVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 统计服务的业务编排层。
 *
 * <p>HTTP 查询只读本地汇总；RocketMQ 新事件使用消息快照，旧版 ID-only 事件才经内部令牌读取订单快照。</p>
 */
@Service
public class SalesStatisticsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final SalesProjectionService projectionService;
    private final SalesProjectionMapper mapper;
    private final SalesRankingCacheService rankingCache;
    private final OrderSalesInternalClient orders;
    private final String internalToken;

    /** 注入本地投影、榜单缓存及仅供历史消息兼容的受保护订单快照客户端。 */
    public SalesStatisticsService(SalesProjectionService projectionService, SalesProjectionMapper mapper,
                                  SalesRankingCacheService rankingCache, OrderSalesInternalClient orders,
                                  @Value("${internal.scope.token}") String internalToken) {
        this.projectionService = projectionService;
        this.mapper = mapper;
        this.rankingCache = rankingCache;
        this.orders = orders;
        this.internalToken = internalToken;
    }

    /** 查询累计榜，limit 的范围由 Redis 服务层再做一次边界校验。 */
    public SalesRankingVO listRanking(int limit) {
        return rankingCache.list(limit);
    }

    /**
     * 消费已支付订单并持久化其事实与汇总，再更新可重建的 Redis ZSet。
     *
     * @param event RocketMQ 收到的 V1 或 V2 事件
     * @throws RuntimeException MySQL、Redis 或订单兼容读取失败时必须向上抛出，让 Broker 重试
     */
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

    /** 首页批量读取所有窗口当前自然月总份数，避免客户端对每个窗口发一次请求。 */
    public WindowMonthlySalesVO listWindowMonthlySales() {
        String month = currentMonthKey();
        List<SalesWindowTotalDO> rows = mapper.selectMonthlyWindowTotals(month);
        List<WindowMonthlySalesVO.Item> items = new ArrayList<>();
        for (SalesWindowTotalDO row : rows) items.add(new WindowMonthlySalesVO.Item(row.getWindowId(), row.getPaidQuantity()));
        return new WindowMonthlySalesVO(items);
    }

    /** 点餐页读取单个窗口总月售与其菜品月售，当前自然月以支付时刻而非订单创建时间归属。 */
    public WindowProductMonthlySalesVO getWindowMonthlySales(Long windowId) {
        if (windowId == null || windowId <= 0) throw new BusinessException(CommonErrorCode.BAD_REQUEST, "窗口编号无效");
        String month = currentMonthKey();
        SalesWindowTotalDO window = mapper.selectMonthlyWindowTotal(month, windowId);
        long windowSales = window == null ? 0 : window.getPaidQuantity();
        List<SalesProductTotalDO> rows = mapper.selectMonthlyProductTotals(month, windowId);
        List<WindowProductMonthlySalesVO.ProductItem> products = new ArrayList<>();
        for (SalesProductTotalDO row : rows) products.add(new WindowProductMonthlySalesVO.ProductItem(row.getProductId(), row.getPaidQuantity()));
        return new WindowProductMonthlySalesVO(windowId, windowSales, products);
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

    /** 当前月份由部署时区明确固定为 Asia/Shanghai，避免服务主机默认时区变化导致跨月错位。 */
    private String currentMonthKey() {
        return YearMonth.now(BUSINESS_ZONE).toString();
    }
}
