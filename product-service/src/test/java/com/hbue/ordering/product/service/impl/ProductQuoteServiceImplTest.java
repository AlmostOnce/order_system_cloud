package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductQuoteServiceImplTest {

    @Mock
    private DiningWindowMapper diningWindowMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductCategoryMapper productCategoryMapper;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldReturnCurrentPricesForProductsInActiveWindow() {
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(3L);
        window.setStatus(1);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);
        when(productMapper.selectList(any())).thenReturn(List.of(
                product(101L, 3L, "A101", "鸡肉饭", "18.50"),
                product(202L, 3L, "B202", "豆浆", "3.00")
        ));

        ProductQuoteResponse response = service().quote(request(101L, 202L));

        assertTrue(response.isWindowActive());
        assertEquals(2, response.getProducts().size());
        assertEquals("鸡肉饭", response.getProducts().getFirst().getProductName());
        assertEquals(0, new BigDecimal("18.50").compareTo(
                response.getProducts().getFirst().getPrice()
        ));

        ArgumentCaptor<QueryWrapper<ProductDO>> wrapperCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(productMapper).selectList(wrapperCaptor.capture());
        String conditions = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(conditions.contains("window_id"));
        assertTrue(conditions.contains("sale_status"));
        assertTrue(conditions.contains("product_id"));
    }

    @Test
    void shouldReturnInactiveWhenWindowIsDisabled() {
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(3L);
        window.setStatus(0);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);

        ProductQuoteResponse response = service().quote(request(101L));

        assertFalse(response.isWindowActive());
        assertTrue(response.getProducts().isEmpty());
        verify(productMapper, never()).selectList(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldExcludeProductsInDisabledCategoriesFromQuote() {
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(3L);
        window.setStatus(1);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);
        ProductDO product = product(101L, 3L, "A101", "鸡肉饭", "18.50");
        product.setCategoryId(9L);
        when(productMapper.selectList(any())).thenReturn(List.of(product));
        when(productCategoryMapper.selectList(any())).thenReturn(List.of());

        ProductQuoteResponse response = service().quote(request(101L));

        assertTrue(response.isWindowActive());
        assertTrue(response.getProducts().isEmpty());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldQuoteProductsInActiveCategories() {
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(3L);
        window.setStatus(1);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);
        ProductDO product = product(101L, 3L, "A101", "鸡肉饭", "18.50");
        product.setCategoryId(9L);
        when(productMapper.selectList(any())).thenReturn(List.of(product));
        ProductCategoryDO category = new ProductCategoryDO();
        category.setCategoryId(9L);
        category.setWindowId(3L);
        category.setStatus(1);
        when(productCategoryMapper.selectList(any())).thenReturn(List.of(category));

        ProductQuoteResponse response = service().quote(request(101L));

        assertTrue(response.isWindowActive());
        assertEquals(1, response.getProducts().size());
    }

    @Test
    void shouldRejectDuplicateIdsInInternalQuoteRequest() {
        ProductQuoteRequest request = request(101L, 101L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().quote(request)
        );

        assertEquals(CommonErrorCode.BAD_REQUEST.code(), exception.getErrorCode().code());
        verify(diningWindowMapper, never()).selectById(any());
    }

    private ProductQuoteServiceImpl service() {
        return new ProductQuoteServiceImpl(
                diningWindowMapper,
                productCategoryMapper,
                productMapper
        );
    }

    private ProductQuoteRequest request(Long... productIds) {
        ProductQuoteRequest request = new ProductQuoteRequest();
        request.setWindowId(3L);
        request.setProductIds(List.of(productIds));
        return request;
    }

    private ProductDO product(
            Long id,
            Long windowId,
            String code,
            String name,
            String price
    ) {
        ProductDO product = new ProductDO();
        product.setProductId(id);
        product.setWindowId(windowId);
        product.setProductCode(code);
        product.setProductName(name);
        product.setPrice(new BigDecimal(price));
        product.setSaleStatus(1);
        return product;
    }
}
