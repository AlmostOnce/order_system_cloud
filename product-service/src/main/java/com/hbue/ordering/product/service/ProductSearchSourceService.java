package com.hbue.ordering.product.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.web.media.FileReferences;
import com.hbue.ordering.product.api.dto.ProductSearchDocument;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.mapper.RestaurantMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.model.RestaurantDO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** 从商品主库构造搜索服务可消费的当前菜单快照。 */
@Service
public class ProductSearchSourceService {
    private final DiningWindowMapper windows;
    private final ProductMapper products;
    private final ProductCategoryMapper categories;
    private final RestaurantMapper restaurants;

    public ProductSearchSourceService(DiningWindowMapper windows, ProductMapper products,
                                      ProductCategoryMapper categories, RestaurantMapper restaurants) {
        this.windows = windows;
        this.products = products;
        this.categories = categories;
        this.restaurants = restaurants;
    }

    public List<Long> searchableWindowIds() {
        List<RestaurantDO> activeRestaurants = restaurants.selectList(new QueryWrapper<RestaurantDO>().eq("status", 1));
        if (activeRestaurants.isEmpty()) return List.of();
        Set<Long> restaurantIds = activeRestaurants.stream().map(RestaurantDO::getRestaurantId).collect(Collectors.toSet());
        return windows.selectList(new QueryWrapper<DiningWindowDO>().eq("status", 1).in("restaurant_id", restaurantIds)
                        .orderByAsc("restaurant_id", "sort_order", "window_id"))
                .stream().map(DiningWindowDO::getWindowId).toList();
    }

    public List<ProductSearchDocument> documents(Long windowId) {
        DiningWindowDO window = windows.selectById(windowId);
        if (window == null || !Integer.valueOf(1).equals(window.getStatus())) return List.of();
        RestaurantDO restaurant = restaurants.selectById(window.getRestaurantId());
        if (restaurant == null || !Integer.valueOf(1).equals(restaurant.getStatus())) return List.of();
        List<ProductCategoryDO> enabledCategories = categories.selectList(new QueryWrapper<ProductCategoryDO>()
                .eq("window_id", windowId).eq("status", 1));
        Map<Long, ProductCategoryDO> categoryById = enabledCategories.stream()
                .collect(Collectors.toMap(ProductCategoryDO::getCategoryId, c -> c));
        return products.selectList(new QueryWrapper<ProductDO>().eq("window_id", windowId).eq("sale_status", 1)
                        .orderByAsc("category_id", "sort_order", "product_id")).stream()
                .filter(p -> p.getCategoryId() == null || categoryById.containsKey(p.getCategoryId()))
                .map(p -> new ProductSearchDocument(p.getProductId(), p.getCategoryId(), windowId, restaurant.getRestaurantId(),
                        restaurant.getRestaurantName(), restaurant.getLocation(), window.getWindowName(),
                        p.getCategoryId() == null ? null : categoryById.get(p.getCategoryId()).getCategoryName(),
                        p.getProductName(), p.getDescription(), FileReferences.url(p.getImageFileKey()),
                        p.getPrice(), p.getSortOrder()))
                .toList();
    }
}
