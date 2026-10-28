package com.hbue.ordering.product.service.impl;

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
import com.hbue.ordering.product.service.ProductSearchSourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** 从商品主库构造搜索服务可消费的当前菜单快照。 */
@Service
@Transactional(readOnly = true)
public class ProductSearchSourceServiceImpl implements ProductSearchSourceService {
    private final DiningWindowMapper windows;
    private final ProductMapper products;
    private final ProductCategoryMapper categories;
    private final RestaurantMapper restaurants;

    public ProductSearchSourceServiceImpl(DiningWindowMapper windows, ProductMapper products,
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

    /** 读取单个可售菜品；不存在或不再可售时返回空列表，供搜索服务删除文档。 */
    public List<ProductSearchDocument> document(Long windowId, Long productId) {
        ProductDO product = products.selectById(productId);
        if (product == null || !windowId.equals(product.getWindowId()) || !Integer.valueOf(1).equals(product.getSaleStatus())) {
            return List.of();
        }
        DiningWindowDO window = windows.selectById(windowId);
        if (window == null || !Integer.valueOf(1).equals(window.getStatus())) return List.of();
        RestaurantDO restaurant = restaurants.selectById(window.getRestaurantId());
        if (restaurant == null || !Integer.valueOf(1).equals(restaurant.getStatus())) return List.of();
        ProductCategoryDO category = product.getCategoryId() == null ? null : categories.selectById(product.getCategoryId());
        if (product.getCategoryId() != null && (category == null
                || !windowId.equals(category.getWindowId()) || !Integer.valueOf(1).equals(category.getStatus()))) {
            return List.of();
        }
        return List.of(new ProductSearchDocument(product.getProductId(), product.getCategoryId(), windowId,
                restaurant.getRestaurantId(), restaurant.getRestaurantName(), restaurant.getLocation(),
                window.getWindowName(), category == null ? null : category.getCategoryName(), product.getProductName(),
                product.getDescription(), FileReferences.url(product.getImageFileKey()), product.getPrice(),
                product.getSortOrder()));
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
