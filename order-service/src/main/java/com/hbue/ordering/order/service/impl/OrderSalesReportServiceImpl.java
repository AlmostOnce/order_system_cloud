package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.WindowSalesAggregate;
import com.hbue.ordering.order.service.OrderSalesReportService;
import com.hbue.ordering.order.service.OrderWindowNameService;
import com.hbue.ordering.order.vo.AdminSalesReportVO;
import com.hbue.ordering.order.vo.AdminSalesWindowVO;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端经营报表业务实现。
 *
 * <p>数据范围在服务端解析：全平台范围不加档口过滤；餐厅范围通过商品服务内部接口
 * 展开为下属档口；档口范围只统计直接授权的档口。范围为空时直接返回全零报表，不执行
 * 全表聚合。金额与份数分两条聚合查询执行，避免订单头与明细连接后金额被重复累计。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
@Service
public class OrderSalesReportServiceImpl implements OrderSalesReportService {

    /**
     * 单次报表允许的最大自然日跨度；防止一次请求扫描过长历史。
     */
    private static final int MAX_RANGE_DAYS = 92;

    /**
     * 订单主表数据访问接口。
     */
    private final OrderMapper orderMapper;

    /**
     * 数据范围判定服务。
     */
    private final DataScopeService dataScopeService;

    /**
     * 商品服务内部客户端，用于把餐厅范围展开为档口集合。
     */
    private final WindowInternalClient windowInternalClient;

    /**
     * 档口名称补全服务。
     */
    private final OrderWindowNameService windowNameService;

    /**
     * 服务间共享密钥。
     */
    private final String internalScopeToken;

    /**
     * 创建经营报表业务实现。
     *
     * @param orderMapper          订单主表数据访问接口
     * @param dataScopeService     数据范围判定服务
     * @param windowInternalClient 商品服务内部客户端
     * @param windowNameService    档口名称补全服务
     * @param internalScopeToken   服务间共享密钥
     */
    public OrderSalesReportServiceImpl(
            OrderMapper orderMapper,
            DataScopeService dataScopeService,
            WindowInternalClient windowInternalClient,
            OrderWindowNameService windowNameService,
            @Value("${internal.scope.token:}") String internalScopeToken
    ) {
        this.orderMapper = orderMapper;
        this.dataScopeService = dataScopeService;
        this.windowInternalClient = windowInternalClient;
        this.windowNameService = windowNameService;
        this.internalScopeToken = internalScopeToken;
    }

    /**
     * 汇总指定时间区间的经营数据。
     *
     * @param operatorId 当前登录操作者 ID
     * @param windowId   可选档口 ID
     * @param from       区间起点（含）
     * @param to         区间终点（不含）
     * @return 汇总值与分档口明细
     */
    @Override
    public AdminSalesReportVO summarize(
            Long operatorId,
            Long windowId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        requireValidRange(operatorId, from, to);

        Long requestedWindowId = null;
        List<Long> scopeWindowIds;
        if (windowId != null) {
            // 指定档口时按私有数据语义隐藏存在性：越权与不存在都返回 404。
            if (windowId <= 0 || !dataScopeService.canAccessWindow(operatorId, windowId)) {
                throw new BusinessException(
                        CommonErrorCode.NOT_FOUND,
                        "档口不存在或不在数据范围内"
                );
            }
            requestedWindowId = windowId;
            scopeWindowIds = List.of(windowId);
        } else {
            scopeWindowIds = resolveScopeWindowIds(operatorId);
        }

        // 范围为空：没有可统计的档口，返回零值而不执行可能昂贵的全表聚合。
        if (scopeWindowIds != null && scopeWindowIds.isEmpty()) {
            return emptyReport(requestedWindowId, from, to);
        }

        List<WindowSalesAggregate> moneyRows =
                orderMapper.selectSalesByWindow(from, to, scopeWindowIds);
        if (moneyRows == null || moneyRows.isEmpty()) {
            return emptyReport(requestedWindowId, from, to);
        }
        Map<Long, Long> quantityByWindow = loadQuantityByWindow(from, to, scopeWindowIds);

        List<Long> windowIds = moneyRows.stream()
                .map(WindowSalesAggregate::getWindowId)
                .toList();
        // 名称失败不影响金额与份数，前端按编号兜底展示。
        Map<Long, String> names = windowNameService.loadNames(windowIds);

        List<AdminSalesWindowVO> windows = new ArrayList<>(moneyRows.size());
        long totalOrders = 0L;
        long totalQuantity = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (WindowSalesAggregate row : moneyRows) {
            long orders = row.getOrderCount() == null ? 0L : row.getOrderCount();
            BigDecimal amount = row.getGrossAmount() == null ? BigDecimal.ZERO : row.getGrossAmount();
            long quantity = quantityByWindow.getOrDefault(row.getWindowId(), 0L);
            totalOrders += orders;
            totalAmount = totalAmount.add(amount);
            totalQuantity += quantity;
            windows.add(new AdminSalesWindowVO(
                    row.getWindowId(),
                    names.get(row.getWindowId()),
                    orders,
                    amount,
                    quantity
            ));
        }

        BigDecimal average = totalOrders == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalAmount.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        return new AdminSalesReportVO(
                from,
                to,
                requestedWindowId,
                totalOrders,
                totalAmount,
                average,
                totalQuantity,
                windows
        );
    }

    /**
     * 校验操作者与时间区间。
     *
     * @param operatorId 操作者 ID
     * @param from       起点
     * @param to         终点
     */
    private void requireValidRange(Long operatorId, LocalDateTime from, LocalDateTime to) {
        if (operatorId == null || from == null || to == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "报表时间区间不正确");
        }
        if (!to.isAfter(from)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "报表结束时间必须晚于开始时间");
        }
        if (Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "报表区间不能超过 " + MAX_RANGE_DAYS + " 天"
            );
        }
    }

    /**
     * 解析操作者可统计的档口集合。
     *
     * @param operatorId 操作者 ID
     * @return null 表示全平台不限档口；空列表表示没有任何范围
     */
    private List<Long> resolveScopeWindowIds(Long operatorId) {
        UserScopeSnapshot scopes = dataScopeService.loadScopes(operatorId);
        if (scopes.isPlatformScope()) {
            return null;
        }
        Set<Long> windowIds = new LinkedHashSet<>(scopes.getWindowIds());
        if (!scopes.getRestaurantIds().isEmpty()) {
            windowIds.addAll(expandRestaurantWindows(
                    new ArrayList<>(scopes.getRestaurantIds())
            ));
        }
        return new ArrayList<>(windowIds);
    }

    /**
     * 把餐厅范围展开为档口集合。
     *
     * @param restaurantIds 餐厅 ID 列表，保证非空
     * @return 档口 ID 列表
     */
    private List<Long> expandRestaurantWindows(List<Long> restaurantIds) {
        ApiResponse<List<Long>> response =
                windowInternalClient.getWindowIdsByRestaurants(
                        restaurantIds,
                        internalScopeToken
                );
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            // 无法确认餐厅下属档口时失败关闭，避免统计到范围外数据。
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "商品服务暂时不可用，无法确认餐厅档口范围"
            );
        }
        return response.getData() == null ? List.of() : response.getData();
    }

    /**
     * 读取各档口的原始购买份数。
     *
     * @param from        区间起点
     * @param to          区间终点
     * @param windowIds   档口集合；null 表示全平台
     * @return 档口 ID 到份数的映射
     */
    private Map<Long, Long> loadQuantityByWindow(
            LocalDateTime from,
            LocalDateTime to,
            List<Long> windowIds
    ) {
        Map<Long, Long> quantities = new LinkedHashMap<>();
        List<WindowSalesAggregate> rows =
                orderMapper.selectSalesQuantityByWindow(from, to, windowIds);
        if (rows == null) {
            return quantities;
        }
        for (WindowSalesAggregate row : rows) {
            quantities.put(
                    row.getWindowId(),
                    row.getPaidQuantity() == null ? 0L : row.getPaidQuantity()
            );
        }
        return quantities;
    }

    /**
     * 构造全零报表。
     *
     * @param windowId 请求指定的档口；按范围汇总时为 null
     * @param from     区间起点
     * @param to       区间终点
     * @return 全零报表
     */
    private AdminSalesReportVO emptyReport(
            Long windowId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return new AdminSalesReportVO(
                from,
                to,
                windowId,
                0L,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                0L,
                List.of()
        );
    }
}
