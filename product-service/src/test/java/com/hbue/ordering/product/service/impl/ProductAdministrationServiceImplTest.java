package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.product.cache.ProductBloomFilterManager;
import com.hbue.ordering.product.cache.ProductMenuCacheService;
import com.hbue.ordering.product.dto.request.ProductCategorySaveRequest;
import com.hbue.ordering.product.dto.request.ProductSaveRequest;
import com.hbue.ordering.product.dto.request.WindowSaveRequest;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.service.ProductAdministrationService;
import com.hbue.ordering.product.vo.ProductAdminVO;
import com.hbue.ordering.product.vo.ProductCategoryAdminVO;
import com.hbue.ordering.product.vo.WindowAdminVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductAdministrationServiceImplTest {

    /** 测试用操作者用户 ID。 */
    private static final Long OPERATOR_ID = 99L;

    @Mock
    private DiningWindowMapper diningWindowMapper;

    @Mock
    private ProductCategoryMapper productCategoryMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private ProductMenuCacheService menuCacheService;

    @Mock
    private ProductBloomFilterManager bloomFilterManager;

    @Test
    void shouldCreateWindowWithDescriptionAndDefaultSortOrder() {
        WindowSaveRequest request = windowRequest();
        request.setDescription("现点现做的小炒");
        when(diningWindowMapper.insert(any(DiningWindowDO.class))).thenAnswer(invocation -> {
            DiningWindowDO created = invocation.getArgument(0);
            created.setWindowId(7L);
            return 1;
        });

        WindowAdminVO result = service().createWindow(OPERATOR_ID, request);

        assertEquals("现点现做的小炒", result.getDescription());
        assertEquals(0, result.getSortOrder());
        verify(menuCacheService).evictActiveWindows();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldUpdateDescriptionAndSortAndEvictBothCaches() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));
        WindowSaveRequest request = windowRequest();
        request.setDescription("");
        request.setSortOrder(25);

        service().updateWindow(OPERATOR_ID, 7L, request);

        ArgumentCaptor<UpdateWrapper<DiningWindowDO>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(diningWindowMapper).update(isNull(), captor.capture());
        String sql = captor.getValue().getSqlSet();
        assertTrue(sql.contains("description="));
        assertTrue(sql.contains("sort_order="));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(""));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(25));
        verify(menuCacheService).evictActiveWindows();
        verify(menuCacheService).evictWindowMenu(7L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldPreserveNewFieldsWhenOldClientOmitsThem() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));

        service().updateWindow(OPERATOR_ID, 7L, windowRequest());

        ArgumentCaptor<UpdateWrapper<DiningWindowDO>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(diningWindowMapper).update(isNull(), captor.capture());
        assertFalse(captor.getValue().getSqlSet().contains("description="));
        assertFalse(captor.getValue().getSqlSet().contains("sort_order="));
    }

    @Test
    void shouldCreateWindowWithExplicitSortOrder() {
        WindowSaveRequest request = windowRequest();
        request.setSortOrder(15);
        when(diningWindowMapper.insert(any(DiningWindowDO.class))).thenAnswer(invocation -> {
            DiningWindowDO created = invocation.getArgument(0);
            created.setWindowId(7L);
            return 1;
        });

        WindowAdminVO result = service().createWindow(OPERATOR_ID, request);

        assertEquals(15, result.getSortOrder());
    }

    @Test
    void shouldSoftDisableWindowInsteadOfDeletingIt() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));

        service().disableWindow(OPERATOR_ID, 7L);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper<DiningWindowDO>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(diningWindowMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void shouldMapDuplicateWindowCodeToConflict() {
        when(diningWindowMapper.insert(any(DiningWindowDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate window code"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().createWindow(OPERATOR_ID, windowRequest())
        );

        assertEquals(CommonErrorCode.CONFLICT.code(), exception.getErrorCode().code());
    }

    @Test
    void shouldRejectCategoryWhenWindowDoesNotExist() {
        when(diningWindowMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().createCategory(OPERATOR_ID, 9L, categoryRequest())
        );

        assertEquals(CommonErrorCode.NOT_FOUND.code(), exception.getErrorCode().code());
    }

    @Test
    void shouldSoftDisableCategoryInsteadOfDeletingIt() {
        ProductCategoryDO category = new ProductCategoryDO();
        category.setCategoryId(12L);
        category.setWindowId(7L);
        category.setStatus(1);
        when(productCategoryMapper.selectById(12L)).thenReturn(category);

        service().disableCategory(OPERATOR_ID, 12L);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper<ProductCategoryDO>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(productCategoryMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void shouldCreateProductOnlyWithCategoryFromSameWindow() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));
        ProductCategoryDO category = new ProductCategoryDO();
        category.setCategoryId(12L);
        category.setWindowId(7L);
        category.setStatus(1);
        when(productCategoryMapper.selectById(12L)).thenReturn(category);
        when(productMapper.insert(any(ProductDO.class))).thenAnswer(invocation -> {
            ProductDO product = invocation.getArgument(0);
            product.setProductId(31L);
            return 1;
        });

        ProductAdminVO result = service().createProduct(
                OPERATOR_ID,
                7L,
                productRequest(12L)
        );

        assertEquals(31L, result.getProductId());
        assertEquals(7L, result.getWindowId());
        assertEquals(12L, result.getCategoryId());
        assertEquals("DEMO-31", result.getProductCode());
        assertEquals(0, new BigDecimal("8.50").compareTo(result.getPrice()));
    }

    @Test
    void shouldRejectProductCategoryFromAnotherWindow() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));
        ProductCategoryDO category = new ProductCategoryDO();
        category.setCategoryId(12L);
        category.setWindowId(8L);
        category.setStatus(1);
        when(productCategoryMapper.selectById(12L)).thenReturn(category);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().createProduct(OPERATOR_ID, 7L, productRequest(12L))
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(), exception.getErrorCode().code());
    }

    @Test
    void shouldRejectOnSaleProductInDisabledCategory() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));
        ProductCategoryDO category = new ProductCategoryDO();
        category.setCategoryId(12L);
        category.setWindowId(7L);
        category.setStatus(0);
        when(productCategoryMapper.selectById(12L)).thenReturn(category);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().createProduct(OPERATOR_ID, 7L, productRequest(12L))
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(), exception.getErrorCode().code());
    }

    @Test
    void shouldSoftDisableProductByTakingItOffSale() {
        ProductDO product = new ProductDO();
        product.setProductId(31L);
        product.setWindowId(7L);
        product.setSaleStatus(1);
        when(productMapper.selectById(31L)).thenReturn(product);

        service().disableProduct(OPERATOR_ID, 31L);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper<ProductDO>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(productMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void shouldDeferCacheEvictionUntilTransactionCommit() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service().disableWindow(OPERATOR_ID, 7L);

            // 事务提交前不得删除缓存，否则并发读可能把旧值回写造成脏缓存。
            verify(menuCacheService, never()).evictWindowMenu(anyLong());
            verify(menuCacheService, never()).evictActiveWindows();

            // 模拟事务提交，此时才应执行失效。
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(menuCacheService).evictWindowMenu(7L);
            verify(menuCacheService).evictActiveWindows();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldUpdateOnlyWindowImageAndEvictBothCaches() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));
        when(diningWindowMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        service().updateWindowImage(OPERATOR_ID, 7L, "0123456789abcdef0123456789abcdef");
        ArgumentCaptor<UpdateWrapper<DiningWindowDO>> update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(diningWindowMapper).update(isNull(), update.capture());
        assertTrue(update.getValue().getSqlSet().contains("image_file_key="));
        assertTrue(!update.getValue().getSqlSet().contains("window_name="));
        verify(dataScopeService).checkWindowAccess(OPERATOR_ID, 7L);
        verify(menuCacheService).evictActiveWindows();
        verify(menuCacheService).evictWindowMenu(7L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldRejectImageChangeOutsideManagedWindow() {
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN, "无管理权限"))
                .when(dataScopeService).checkWindowAccess(OPERATOR_ID, 7L);
        assertThrows(BusinessException.class, () -> service().updateWindowImage(OPERATOR_ID, 7L, "0123456789abcdef0123456789abcdef"));
        verify(diningWindowMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(menuCacheService, never()).evictWindowMenu(7L);
    }

    private ProductAdministrationService service() {
        var images = org.mockito.Mockito.mock(com.hbue.ordering.common.web.media.ManagedImageClient.class);
        org.mockito.Mockito.lenient().when(images.requireImageKey(anyString(), anyLong(), anyString(), anyLong())).thenAnswer(a -> a.getArgument(0));
        return new ProductAdministrationServiceImpl(
                diningWindowMapper,
                productCategoryMapper,
                productMapper,
                dataScopeService,
                menuCacheService,
                bloomFilterManager,
                images
        );
    }

    private DiningWindowDO window(Long id, Integer status) {
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(id);
        window.setWindowCode("W-" + id);
        window.setWindowName("测试窗口");
        window.setLocation("一楼");
        window.setStatus(status);
        return window;
    }

    private WindowSaveRequest windowRequest() {
        WindowSaveRequest request = new WindowSaveRequest();
        request.setRestaurantId(1L);
        request.setWindowCode("W-7");
        request.setWindowName("测试窗口");
        request.setLocation("一楼");
        request.setStatus(1);
        return request;
    }

    private ProductCategorySaveRequest categoryRequest() {
        ProductCategorySaveRequest request = new ProductCategorySaveRequest();
        request.setCategoryName("主食");
        request.setSortOrder(10);
        request.setStatus(1);
        return request;
    }

    private ProductSaveRequest productRequest(Long categoryId) {
        ProductSaveRequest request = new ProductSaveRequest();
        request.setCategoryId(categoryId);
        request.setProductCode("DEMO-31");
        request.setProductName("测试商品");
        request.setDescription("开发测试商品");
        request.setImageFileKey("");
        request.setPrice(new BigDecimal("8.50"));
        request.setSaleStatus(1);
        request.setSortOrder(10);
        return request;
    }
}
