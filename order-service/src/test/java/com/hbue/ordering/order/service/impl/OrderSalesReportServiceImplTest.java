package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.WindowSalesAggregate;
import com.hbue.ordering.order.service.OrderWindowNameService;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 经营报表业务测试。
 *
 * <p>覆盖范围收敛、金额与份数合并、客单价计算与失败关闭：档口越权按 404 隐藏，
 * 餐厅范围必须展开为档口，没有范围时不查询全表，商品服务不可用时不返回未经收敛的数据。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
@ExtendWith(MockitoExtension.class)
class OrderSalesReportServiceImplTest {

    private static final Long OPERATOR_ID = 5L;

    private static final String INTERNAL_TOKEN = "test-token";

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 1, 0, 0);

    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 8, 0, 0);

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private WindowInternalClient windowInternalClient;

    @Mock
    private OrderWindowNameService windowNameService;

    private OrderSalesReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderSalesReportServiceImpl(
                orderMapper,
                dataScopeService,
                windowInternalClient,
                windowNameService,
                INTERNAL_TOKEN
        );
    }

    /**
     * 指定档口时汇总订单数、金额、份数并计算客单价。
     */
    @Test
    void shouldAggregateRequestedWindow() {
        when(dataScopeService.canAccessWindow(OPERATOR_ID, 9L)).thenReturn(true);
        when(orderMapper.selectSalesByWindow(FROM, TO, List.of(9L)))
                .thenReturn(List.of(moneyRow(9L, 3L, "90.00")));
        when(orderMapper.selectSalesQuantityByWindow(FROM, TO, List.of(9L)))
                .thenReturn(List.of(quantityRow(9L, 7L)));
        when(windowNameService.loadNames(List.of(9L)))
                .thenReturn(Map.of(9L, "三楼窗口"));

        var report = service.summarize(OPERATOR_ID, 9L, FROM, TO);

        assertEquals(3L, report.orderCount());
        assertEquals(new BigDecimal("90.00"), report.grossAmount());
        assertEquals(new BigDecimal("30.00"), report.averageOrderAmount());
        assertEquals(7L, report.paidQuantity());
        assertEquals("三楼窗口", report.windows().getFirst().windowName());
        assertEquals(9L, report.windowId());
    }

    /**
     * 指定档口越权与不存在统一返回 404，且不执行聚合查询。
     */
    @Test
    void shouldHideUnauthorizedWindow() {
        when(dataScopeService.canAccessWindow(OPERATOR_ID, 9L)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.summarize(OPERATOR_ID, 9L, FROM, TO)
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(), exception.getErrorCode().code());
        verify(orderMapper, never()).selectSalesByWindow(any(), any(), any());
    }

    /**
     * 餐厅范围必须经商品服务展开为档口，再参与聚合。
     */
    @Test
    void shouldExpandRestaurantScopeToWindows() {
        when(dataScopeService.loadScopes(OPERATOR_ID))
                .thenReturn(new UserScopeSnapshot(
                        OPERATOR_ID, false, Set.of(9L), Set.of(1L)));
        when(windowInternalClient.getWindowIdsByRestaurants(
                List.of(1L), INTERNAL_TOKEN
        )).thenReturn(ApiResponse.success(List.of(7L)));
        when(orderMapper.selectSalesByWindow(FROM, TO, List.of(9L, 7L)))
                .thenReturn(List.of());
        when(orderMapper.selectSalesQuantityByWindow(FROM, TO, List.of(9L, 7L)))
                .thenReturn(List.of());

        var report = service.summarize(OPERATOR_ID, null, FROM, TO);

        assertNull(report.windowId());
        assertEquals(0L, report.orderCount());
        verify(orderMapper).selectSalesByWindow(FROM, TO, List.of(9L, 7L));
    }

    /**
     * 全平台范围不加档口过滤，由数据库聚合全部档口。
     */
    @Test
    void shouldAggregateAllWindowsForPlatformScope() {
        when(dataScopeService.loadScopes(OPERATOR_ID))
                .thenReturn(new UserScopeSnapshot(
                        OPERATOR_ID, true, Set.of(), Set.of()));
        when(orderMapper.selectSalesByWindow(FROM, TO, null))
                .thenReturn(List.of(moneyRow(9L, 1L, "10.00")));
        when(orderMapper.selectSalesQuantityByWindow(FROM, TO, null))
                .thenReturn(List.of(quantityRow(9L, 2L)));
        when(windowNameService.loadNames(anyList())).thenReturn(Map.of());

        var report = service.summarize(OPERATOR_ID, null, FROM, TO);

        assertEquals(1L, report.orderCount());
        assertEquals(new BigDecimal("10.00"), report.averageOrderAmount());
        assertNull(report.windows().getFirst().windowName());
        verify(windowInternalClient, never())
                .getWindowIdsByRestaurants(anyList(), any());
    }

    /**
     * 没有任何范围时返回全零报表，不执行全表聚合。
     */
    @Test
    void shouldReturnEmptyReportWhenNoScope() {
        when(dataScopeService.loadScopes(OPERATOR_ID))
                .thenReturn(new UserScopeSnapshot(
                        OPERATOR_ID, false, Set.of(), Set.of()));

        var report = service.summarize(OPERATOR_ID, null, FROM, TO);

        assertEquals(0L, report.orderCount());
        assertEquals(new BigDecimal("0.00"), report.grossAmount());
        assertTrue(report.windows().isEmpty());
        verify(orderMapper, never()).selectSalesByWindow(any(), any(), any());
    }

    /**
     * 时间区间非法或跨度过长时必须拒绝。
     */
    @Test
    void shouldRejectInvalidRange() {
        assertThrows(BusinessException.class,
                () -> service.summarize(OPERATOR_ID, null, TO, FROM));
        assertThrows(BusinessException.class,
                () -> service.summarize(OPERATOR_ID, null, FROM, FROM.plusDays(93)));
    }

    /**
     * 商品服务不可用时失败关闭，不返回可能越权的统计。
     */
    @Test
    void shouldFailClosedWhenProductServiceUnavailable() {
        when(dataScopeService.loadScopes(OPERATOR_ID))
                .thenReturn(new UserScopeSnapshot(
                        OPERATOR_ID, false, Set.of(), Set.of(1L)));
        when(windowInternalClient.getWindowIdsByRestaurants(
                List.of(1L), INTERNAL_TOKEN
        )).thenReturn(ApiResponse.failure(CommonErrorCode.SERVICE_UNAVAILABLE));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.summarize(OPERATOR_ID, null, FROM, TO)
        );

        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.code(),
                exception.getErrorCode().code());
        verify(orderMapper, never()).selectSalesByWindow(any(), any(), any());
    }

    /**
     * 构造金额聚合行。
     */
    private WindowSalesAggregate moneyRow(Long windowId, Long orders, String amount) {
        WindowSalesAggregate row = new WindowSalesAggregate();
        row.setWindowId(windowId);
        row.setOrderCount(orders);
        row.setGrossAmount(new BigDecimal(amount));
        return row;
    }

    /**
     * 构造份数聚合行。
     */
    private WindowSalesAggregate quantityRow(Long windowId, Long quantity) {
        WindowSalesAggregate row = new WindowSalesAggregate();
        row.setWindowId(windowId);
        row.setPaidQuantity(quantity);
        return row;
    }
}
