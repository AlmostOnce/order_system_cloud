package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.product.cache.ProductBloomFilterManager;
import com.hbue.ordering.product.cache.ProductMenuCacheService;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.vo.WindowMenuVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMenuServiceImplTest {

    @Mock
    private DiningWindowMapper diningWindowMapper;

    @Mock
    private ProductCategoryMapper productCategoryMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductMenuCacheService menuCacheService;

    @Mock
    private ProductBloomFilterManager bloomFilterManager;

    @Mock
    private DistributedLockService distributedLockService;

    @BeforeEach
    void setUp() {
        // 布隆过滤器默认放行，避免拦掉原有用例依赖的数据。
        lenient().when(bloomFilterManager.mightContainWindow(anyLong()))
                .thenReturn(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldGroupOnSaleProductsAndKeepUncategorizedProducts() {
        DiningWindowDO window = window(3L, 1);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);
        when(productCategoryMapper.selectList(any())).thenReturn(List.of(
                category(11L, "盖饭", 1),
                category(12L, "饮品", 2)
        ));
        when(productMapper.selectList(any())).thenReturn(List.of(
                product(101L, 11L, "鸡肉饭", "18.50"),
                product(102L, null, "豆浆", "3.00"),
                product(103L, 99L, "停用分类菜品", "9.00")
        ));

        WindowMenuVO menu = service().getWindowMenu(3L);

        assertEquals(3L, menu.getWindowId());
        assertEquals(2, menu.getCategories().size());
        assertEquals("盖饭", menu.getCategories().getFirst().getCategoryName());
        assertEquals("鸡肉饭", menu.getCategories().getFirst()
                .getProducts().getFirst().getProductName());
        assertEquals(0, new BigDecimal("18.50").compareTo(
                menu.getCategories().getFirst().getProducts().getFirst().getPrice()
        ));
        assertTrue(menu.getCategories().get(1).getProducts().isEmpty());
        assertEquals("豆浆", menu.getUncategorizedProducts().getFirst().getProductName());
        assertEquals(1, menu.getUncategorizedProducts().size());

        ArgumentCaptor<QueryWrapper<ProductCategoryDO>> categoryWrapperCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(productCategoryMapper).selectList(categoryWrapperCaptor.capture());
        assertTrue(categoryWrapperCaptor.getValue().getSqlSegment().contains("status"));

        ArgumentCaptor<QueryWrapper<ProductDO>> productWrapperCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(productMapper).selectList(productWrapperCaptor.capture());
        assertTrue(productWrapperCaptor.getValue().getSqlSegment().contains("sale_status"));
    }

    @Test
    void shouldRejectMissingOrDisabledWindow() {
        when(diningWindowMapper.selectById(3L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().getWindowMenu(3L)
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(), exception.getErrorCode().code());
        verify(productCategoryMapper, never()).selectList(any());
        verify(productMapper, never()).selectList(any());
    }

    @Test
    void shouldRejectInvalidWindowId() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().getWindowMenu(0L)
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(), exception.getErrorCode().code());
        verify(diningWindowMapper, never()).selectById(any());
    }

    @Test
    void shouldReturnCachedMenuWithoutQueryingDatabase() {
        WindowMenuVO cachedMenu = new WindowMenuVO(
                3L,
                "W-3",
                "一楼窗口",
                "一楼东侧",
                List.of(),
                List.of()
        );
        when(menuCacheService.findWindowMenu(3L)).thenReturn(cachedMenu);

        WindowMenuVO menu = service().getWindowMenu(3L);

        assertSame(cachedMenu, menu);
        verify(diningWindowMapper, never()).selectById(any());
    }

    @Test
    void shouldRejectWhenWindowMenuMarkedAbsent() {
        when(menuCacheService.isWindowMenuMarkedAbsent(3L)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().getWindowMenu(3L)
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(), exception.getErrorCode().code());
        verify(diningWindowMapper, never()).selectById(any());
        verify(menuCacheService, never()).findWindowMenu(anyLong());
    }

    @Test
    void shouldRejectWhenBloomFilterJudgesWindowAbsent() {
        when(bloomFilterManager.mightContainWindow(3L)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().getWindowMenu(3L)
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(), exception.getErrorCode().code());
        verify(menuCacheService, never()).findWindowMenu(anyLong());
        verify(menuCacheService, never()).isWindowMenuMarkedAbsent(anyLong());
        verify(diningWindowMapper, never()).selectById(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldFallbackToDatabaseWhenLockNotAcquired() {
        DiningWindowDO window = window(3L, 1);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);
        when(productCategoryMapper.selectList(any())).thenReturn(List.of());
        when(productMapper.selectList(any())).thenReturn(List.of());

        WindowMenuVO menu = service().getWindowMenu(3L);

        assertNotNull(menu);
        assertEquals(3L, menu.getWindowId());
        verify(menuCacheService, never()).saveWindowMenu(anyLong(), any());
    }

    @Test
    void shouldMarkAbsentWhenLockAcquiredAndWindowMissing() {
        DistributedLockService.LockHandle lockHandle =
                mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire(anyString())).thenReturn(lockHandle);
        when(diningWindowMapper.selectById(3L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().getWindowMenu(3L)
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(), exception.getErrorCode().code());
        verify(menuCacheService).saveWindowMenuAbsent(3L);
    }

    private ProductMenuServiceImpl service() {
        return new ProductMenuServiceImpl(
                diningWindowMapper,
                productCategoryMapper,
                productMapper,
                menuCacheService,
                bloomFilterManager,
                distributedLockService
        );
    }

    private DiningWindowDO window(Long id, Integer status) {
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(id);
        window.setWindowCode("W-" + id);
        window.setWindowName("一楼窗口");
        window.setLocation("一楼东侧");
        window.setStatus(status);
        return window;
    }

    private ProductCategoryDO category(Long id, String name, Integer sortOrder) {
        ProductCategoryDO category = new ProductCategoryDO();
        category.setCategoryId(id);
        category.setWindowId(3L);
        category.setCategoryName(name);
        category.setSortOrder(sortOrder);
        category.setStatus(1);
        return category;
    }

    private ProductDO product(Long id, Long categoryId, String name, String price) {
        ProductDO product = new ProductDO();
        product.setProductId(id);
        product.setWindowId(3L);
        product.setCategoryId(categoryId);
        product.setProductCode("P-" + id);
        product.setProductName(name);
        product.setPrice(new BigDecimal(price));
        product.setSaleStatus(1);
        return product;
    }
}
