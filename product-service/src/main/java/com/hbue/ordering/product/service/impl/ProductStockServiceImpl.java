package com.hbue.ordering.product.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.service.ProductMenuService;
import com.hbue.ordering.product.service.ProductStockService;
import com.hbue.ordering.product.vo.MenuProductVO;
import com.hbue.ordering.product.vo.ProductStockVO;
import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.stock.api.client.StockServiceClient;
import com.hbue.ordering.stock.api.vo.StockVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 聚合菜单可售菜品与库存，不把动态库存写进菜单缓存。 */
@Service
public class ProductStockServiceImpl implements ProductStockService {
    private static final Logger log = LoggerFactory.getLogger(ProductStockServiceImpl.class);
    private final ProductMenuService productMenuService;
    private final StockServiceClient stockServiceClient;
    private final String internalToken;

    public ProductStockServiceImpl(ProductMenuService productMenuService,
                                   StockServiceClient stockServiceClient,
                                   @Value("${internal.scope.token:}") String internalToken) {
        this.productMenuService = productMenuService;
        this.stockServiceClient = stockServiceClient;
        this.internalToken = internalToken;
    }

    @Override
    public List<ProductStockVO> getWindowStocks(Long windowId) {
        // 复用现有窗口可见性校验，仅返回当前菜单中可售菜品的库存。
        WindowMenuVO menu = productMenuService.getWindowMenu(windowId);
        Set<Long> productIds = new LinkedHashSet<>();
        menu.getCategories().forEach(category -> category.getProducts().stream()
                .map(MenuProductVO::getProductId).forEach(productIds::add));
        menu.getUncategorizedProducts().stream().map(MenuProductVO::getProductId).forEach(productIds::add);
        if (productIds.isEmpty()) {
            return List.of();
        }
        ApiResponse<List<StockVO>> response;
        try {
            response = stockServiceClient.getWindowStocks(windowId, internalToken);
        } catch (RuntimeException exception) {
            // 即使 Feign 降级未启用，也不能把查询失败误报为零库存。
            log.warn("查询窗口库存失败：窗口 {}", windowId, exception);
            throw unavailable();
        }
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            throw unavailable();
        }
        Map<Long, StockVO> stocks = new HashMap<>();
        for (StockVO stock : response.getData()) {
            if (stock == null || !windowId.equals(stock.getWindowId())
                    || stock.getProductId() == null || stock.getAvailableQty() == null
                    || stock.getAvailableQty() < 0 || stocks.putIfAbsent(stock.getProductId(), stock) != null) {
                throw unavailable();
            }
        }
        List<ProductStockVO> result = new ArrayList<>();
        for (Long productId : productIds) {
            StockVO stock = stocks.get(productId);
            result.add(new ProductStockVO(productId, stock == null ? 0 : stock.getAvailableQty(), stock != null));
        }
        return result;
    }

    private BusinessException unavailable() {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "库存暂时无法查询，请稍后重试");
    }
}
