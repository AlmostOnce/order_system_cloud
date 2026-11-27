package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.product.api.dto.ProductQuoteItemRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.cache.ProductBloomFilterManager;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.mapper.RestaurantMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.model.RestaurantDO;
import com.hbue.ordering.product.service.ProductSpecificationService;
import com.hbue.ordering.product.vo.ProductSpecificationGroupVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductQuoteServiceImplTest {

    @Mock
    private DiningWindowMapper diningWindowMapper;

    @Mock
    private RestaurantMapper restaurantMapper;

    /**
     * 规格服务独立mock，原有菜单和核价用例默认无规格配置。
     */
    @Mock
    private ProductSpecificationService specifications;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductCategoryMapper productCategoryMapper;

    @Mock
    private ProductBloomFilterManager bloomFilterManager;

    @BeforeEach
    void setUp() {
        // 布隆过滤器默认放行，避免拦掉原有用例依赖的菜品 ID。
        lenient().when(bloomFilterManager.mightContainProduct(anyLong()))
                .thenReturn(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldReturnCurrentPricesForProductsInActiveWindow() {
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(3L);
        window.setStatus(1);
        window.setIsOpen(1);
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
        window.setIsOpen(1);
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
        window.setIsOpen(1);
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

    /**
     * 同一菜品两种规格独立核价，编号顺序不能影响组合身份或服务端金额。
     */
    @Test
    void shouldQuoteDifferentSelectionsAndNormalizeIds() {
        DiningWindowDO window = new DiningWindowDO();
        window.setStatus(1);
        window.setIsOpen(1);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);
        when(productMapper.selectList(any())).thenReturn(List.of(product(101L, 3L, "P101", "面", "12.00")));
        when(specifications.loadActive(any())).thenReturn(Map.of(101L, List.of(
                new ProductSpecificationGroupVO(1L, "份量", true, 0, List.of(
                        new ProductSpecificationGroupVO.Option(21L, "标准", BigDecimal.ZERO, 0),
                        new ProductSpecificationGroupVO.Option(22L, "大份", new BigDecimal("3.00"), 1))),
                new ProductSpecificationGroupVO(2L, "辣度", false, 1, List.of(
                        new ProductSpecificationGroupVO.Option(31L, "微辣", BigDecimal.ZERO, 0))))));
        ProductQuoteRequest request = new ProductQuoteRequest();
        request.setWindowId(3L);
        ProductQuoteItemRequest first = new ProductQuoteItemRequest();
        first.setProductId(101L);
        first.setSelectedOptionIds(List.of(31L, 22L));
        ProductQuoteItemRequest second = new ProductQuoteItemRequest();
        second.setProductId(101L);
        second.setSelectedOptionIds(List.of(21L));
        request.setItems(List.of(first, second));

        var response = service().quote(request);

        assertEquals(2, response.getProducts().size());
        assertEquals("22-31", response.getProducts().getFirst().getSelectionKey());
        assertEquals(List.of(22L, 31L), response.getProducts().getFirst().getSelectedOptionIds());
        assertEquals(new BigDecimal("15.00"), response.getProducts().getFirst().getPrice());
        assertEquals("份量：大份；辣度：微辣", response.getProducts().getFirst().getSpecificationText());
        assertEquals(new BigDecimal("12.00"), response.getProducts().get(1).getPrice());
    }

    /**
     * 菜品新增必选组后，旧无选择请求不得自动选零加价项，必须整体拒绝。
     */
    @Test
    void shouldRejectMissingRequiredSelectionForLegacyRequest() {
        DiningWindowDO window = new DiningWindowDO();
        window.setStatus(1);
        window.setIsOpen(1);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);
        when(productMapper.selectList(any())).thenReturn(List.of(product(101L, 3L, "P101", "面", "12.00")));
        when(specifications.loadActive(any())).thenReturn(Map.of(101L, List.of(
                new ProductSpecificationGroupVO(1L, "份量", true, 0, List.of(
                        new ProductSpecificationGroupVO.Option(21L, "标准", BigDecimal.ZERO, 0))))));

        assertThrows(BusinessException.class, () -> service().quote(request(101L)));
    }

    /**
     * 重复选项与同组选两项不能通过请求去重静默修复，避免用户实际选择与计价不一致。
     */
    @Test
    void shouldRejectDuplicateOptionBeforeReadingDatabase() {
        ProductQuoteRequest request = new ProductQuoteRequest();
        request.setWindowId(3L);
        ProductQuoteItemRequest item = new ProductQuoteItemRequest();
        item.setProductId(101L);
        item.setSelectedOptionIds(List.of(21L, 21L));
        request.setItems(List.of(item));

        assertThrows(BusinessException.class, () -> service().quote(request));
        verify(diningWindowMapper, never()).selectById(any());
    }

    private ProductQuoteServiceImpl service() {
        return new ProductQuoteServiceImpl(
                diningWindowMapper,
                restaurantMapper,
                productCategoryMapper,
                productMapper,
                bloomFilterManager,
                specifications
        );
    }

    /**
     * 档口状态残留为启用、但所属餐厅已停用时，核价必须拒绝，避免对停用餐厅下单。
     */
    @Test
    void shouldReturnInactiveWhenWindowRestaurantIsDisabled() {
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(3L);
        window.setStatus(1);
        window.setIsOpen(1);
        window.setRestaurantId(5L);
        when(diningWindowMapper.selectById(3L)).thenReturn(window);
        RestaurantDO restaurant = new RestaurantDO();
        restaurant.setRestaurantId(5L);
        restaurant.setStatus(0);
        when(restaurantMapper.selectById(5L)).thenReturn(restaurant);

        ProductQuoteResponse response = service().quote(request(101L));

        assertFalse(response.isWindowActive());
        assertTrue(response.getProducts().isEmpty());
        verify(productMapper, never()).selectList(any());
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
