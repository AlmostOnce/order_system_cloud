package com.hbue.ordering.product.service;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.vo.MenuProductVO;
import com.hbue.ordering.product.vo.ProductStockVO;
import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.service.impl.ProductStockServiceImpl;
import com.hbue.ordering.stock.api.client.StockServiceClient;
import com.hbue.ordering.stock.api.vo.StockVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockServiceTest {
    @Mock private ProductMenuService menuService;
    @Mock private StockServiceClient stockClient;

    @Test
    void shouldReturnOnlyVisibleProductsAndDistinguishMissingFromSoldOut() {
        when(menuService.getWindowMenu(1L)).thenReturn(menu(101L, 102L, 103L));
        when(stockClient.getWindowStocks(1L, "internal-test")).thenReturn(ApiResponse.success(List.of(
                stock(101L, 5), stock(102L, 0), stock(999L, 50))));

        assertEquals(List.of(new ProductStockVO(101L, 5, true),
                        new ProductStockVO(102L, 0, true), new ProductStockVO(103L, 0, false)),
                service().getWindowStocks(1L));
        verify(stockClient).getWindowStocks(1L, "internal-test");
    }

    @Test
    void shouldFailClosedWhenStockServiceUnavailable() {
        when(menuService.getWindowMenu(1L)).thenReturn(menu(101L));
        when(stockClient.getWindowStocks(1L, "internal-test")).thenReturn(
                ApiResponse.failure(CommonErrorCode.SERVICE_UNAVAILABLE, "不可用"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().getWindowStocks(1L));
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.code(), exception.getErrorCode().code());
    }

    @Test
    void shouldNotTreatMissingResponseAsEmptyStock() {
        when(menuService.getWindowMenu(1L)).thenReturn(menu(101L));
        assertThrows(BusinessException.class, () -> service().getWindowStocks(1L));
    }

    @Test
    void shouldRejectMalformedOrWrongWindowStock() {
        when(menuService.getWindowMenu(1L)).thenReturn(menu(101L));
        StockVO wrongWindow = stock(101L, 5);
        wrongWindow.setWindowId(2L);
        when(stockClient.getWindowStocks(1L, "internal-test"))
                .thenReturn(ApiResponse.success(List.of(wrongWindow)));
        assertThrows(BusinessException.class, () -> service().getWindowStocks(1L));
    }

    @Test
    void shouldSkipStockLookupForEmptyMenu() {
        when(menuService.getWindowMenu(1L)).thenReturn(menu());
        assertEquals(List.of(), service().getWindowStocks(1L));
        verifyNoInteractions(stockClient);
    }

    @Test
    void shouldCheckWindowVisibilityBeforeStockLookup() {
        when(menuService.getWindowMenu(1L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        assertThrows(BusinessException.class, () -> service().getWindowStocks(1L));
        verifyNoInteractions(stockClient);
    }

    @Test
    void shouldTranslateTransportFailureToUnavailable() {
        when(menuService.getWindowMenu(1L)).thenReturn(menu(101L));
        when(stockClient.getWindowStocks(1L, "internal-test")).thenThrow(new IllegalStateException("offline"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().getWindowStocks(1L));
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.code(), exception.getErrorCode().code());
    }

    private ProductStockService service() {
        return new ProductStockServiceImpl(menuService, stockClient, "internal-test");
    }

    private WindowMenuVO menu(Long... ids) {
        WindowMenuVO menu = new WindowMenuVO();
        menu.setWindowId(1L);
        menu.setCategories(List.of());
        menu.setUncategorizedProducts(java.util.Arrays.stream(ids).map(id -> {
            MenuProductVO product = new MenuProductVO();
            product.setProductId(id);
            return product;
        }).toList());
        return menu;
    }

    private StockVO stock(Long productId, int availableQty) {
        return StockVO.builder().windowId(1L).productId(productId).availableQty(availableQty).build();
    }
}
