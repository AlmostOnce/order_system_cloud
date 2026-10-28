package com.hbue.ordering.product.service.impl;

import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.mapper.RestaurantMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.model.RestaurantDO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证单菜品快照的上下架与关联状态，防止错误保留搜索结果。 */
class ProductSearchSourceServiceImplTest {
    private final DiningWindowMapper windows = mock(DiningWindowMapper.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
    private final RestaurantMapper restaurants = mock(RestaurantMapper.class);
    private final ProductSearchSourceServiceImpl service =
            new ProductSearchSourceServiceImpl(windows, products, categories, restaurants);

    @Test
    void returnsSingleCurrentProductWhenAllParentsAreEnabled() {
        ProductDO product = new ProductDO();
        product.setProductId(7L);
        product.setWindowId(2L);
        product.setCategoryId(4L);
        product.setSaleStatus(1);
        product.setProductName("鸡肉饭");
        product.setPrice(new BigDecimal("18.00"));
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(2L);
        window.setRestaurantId(3L);
        window.setWindowName("窗口");
        window.setStatus(1);
        RestaurantDO restaurant = new RestaurantDO();
        restaurant.setRestaurantId(3L);
        restaurant.setRestaurantName("食堂");
        restaurant.setStatus(1);
        ProductCategoryDO category = new ProductCategoryDO();
        category.setCategoryId(4L);
        category.setWindowId(2L);
        category.setCategoryName("盖饭");
        category.setStatus(1);
        when(products.selectById(7L)).thenReturn(product);
        when(windows.selectById(2L)).thenReturn(window);
        when(restaurants.selectById(3L)).thenReturn(restaurant);
        when(categories.selectById(4L)).thenReturn(category);

        var result = service.document(2L, 7L);
        assertEquals(1, result.size());
        assertEquals("鸡肉饭", result.getFirst().productName());
        assertEquals("盖饭", result.getFirst().categoryName());
    }

    @Test
    void returnsEmptyForAnOffSaleProductWithoutLoadingParents() {
        ProductDO product = new ProductDO();
        product.setProductId(7L);
        product.setWindowId(2L);
        product.setSaleStatus(0);
        when(products.selectById(7L)).thenReturn(product);

        assertTrue(service.document(2L, 7L).isEmpty());
        verifyNoInteractions(windows, restaurants, categories);
    }
}
