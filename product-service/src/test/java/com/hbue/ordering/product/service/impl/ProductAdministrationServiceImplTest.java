package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductAdministrationServiceImplTest {

    @Mock
    private DiningWindowMapper diningWindowMapper;

    @Mock
    private ProductCategoryMapper productCategoryMapper;

    @Mock
    private ProductMapper productMapper;

    @Test
    void shouldSoftDisableWindowInsteadOfDeletingIt() {
        when(diningWindowMapper.selectById(7L)).thenReturn(window(7L, 1));

        service().disableWindow(7L);

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
                () -> service().createWindow(windowRequest())
        );

        assertEquals(CommonErrorCode.CONFLICT.code(), exception.getErrorCode().code());
    }

    @Test
    void shouldRejectCategoryWhenWindowDoesNotExist() {
        when(diningWindowMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().createCategory(9L, categoryRequest())
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

        service().disableCategory(12L);

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

        ProductAdminVO result = service().createProduct(7L, productRequest(12L));

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
                () -> service().createProduct(7L, productRequest(12L))
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
                () -> service().createProduct(7L, productRequest(12L))
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

        service().disableProduct(31L);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper<ProductDO>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(productMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(0));
    }

    private ProductAdministrationService service() {
        return new ProductAdministrationServiceImpl(
                diningWindowMapper,
                productCategoryMapper,
                productMapper
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
        request.setImageUrl("");
        request.setPrice(new BigDecimal("8.50"));
        request.setSaleStatus(1);
        request.setSortOrder(10);
        return request;
    }
}
