package com.hbue.ordering.cart.service.impl;

import com.hbue.ordering.cart.config.CartProperties;
import com.hbue.ordering.cart.dto.request.CartItemAddRequest;
import com.hbue.ordering.cart.model.CartItemDO;
import com.hbue.ordering.cart.repository.CartRedisRepository;
import com.hbue.ordering.cart.vo.CartVO;
import com.hbue.ordering.cart.vo.CartItemVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 购物车业务规则测试，隔离 Redis 和远程商品服务，验证金额与失败边界。
 *
 * @author order-system
 * @date 2026-10-17
 */
class CartServiceImplTest {
    /** 持久化访问层测试替身。 */
    private CartRedisRepository repository;
    /** 商品服务测试替身。 */
    private ProductServiceClient products;
    /** 被测业务实现。 */
    private CartServiceImpl service;

    /** 创建独立业务测试环境，避免跨用例共享购物车状态。 */
    @BeforeEach
    void setUp() {
        repository = mock(CartRedisRepository.class);
        products = mock(ProductServiceClient.class);
        service = new CartServiceImpl(repository, products,
                new CartProperties(100, 99, Duration.ofDays(30), "cart:user:"));
    }

    /** 空购物车金额为零且不需要调用商品服务。 */
    @Test
    void shouldReturnEmptyCartWithoutRemoteCall() {
        when(repository.findAll(5L)).thenReturn(List.of());
        CartVO cart = service.getCart(5L);
        assertTrue(cart.windows().isEmpty());
        assertEquals(0, cart.checkedAmount().signum());
        assertEquals(0L, cart.totalQuantity());
        verifyNoInteractions(products);
    }

    /** 添加时只能使用商品服务确认的名称和编码，不信任客户端展示数据。 */
    @Test
    void shouldValidateProductBeforeAdding() {
        when(products.quote(any())).thenReturn(quote(true, product(11L, "18.50")));
        service.addItem(5L, 1L, add(11L, 2));
        verify(repository).add(5L, 1L, 11L, 2, "商品11", "P11");
    }

    /** 非法数量在访问远程服务和存储前拒绝。 */
    @Test
    void shouldRejectInvalidQuantityBeforeAnyDependency() {
        for (Integer quantity : new Integer[]{null, 0, -1, 100}) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.addItem(5L, 1L, add(11L, quantity)));
            assertEquals(CommonErrorCode.BAD_REQUEST, error.getErrorCode());
        }
        verifyNoInteractions(products, repository);
    }

    /** 下架菜品或不属于窗口的菜品不能被添加。 */
    @Test
    void shouldRejectUnavailableProductWithoutWriting() {
        when(products.quote(any())).thenReturn(quote(true));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.addItem(5L, 1L, add(11L, 1)));
        assertEquals(CommonErrorCode.NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(repository);
    }

    /** 窗口停用时拒绝添加，即使响应中意外出现菜品数据。 */
    @Test
    void shouldRejectDisabledWindow() {
        when(products.quote(any())).thenReturn(quote(false, product(11L, "18.50")));
        assertEquals(CommonErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.addItem(5L, 1L, add(11L, 1))).getErrorCode());
        verifyNoInteractions(repository);
    }

    /** 商品依赖故障返回 503，不能伪造成功或修改购物车。 */
    @Test
    void shouldFailClosedWhenProductServiceIsUnavailable() {
        when(products.quote(any())).thenReturn(ApiResponse.failure(CommonErrorCode.SERVICE_UNAVAILABLE));
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> service.addItem(5L, 1L, add(11L, 1))).getErrorCode());
        verifyNoInteractions(repository);
    }

    /** 查询使用当前核价，失效和未勾选项不计入可结算合计。 */
    @Test
    void shouldGroupAndCalculateOnlyCheckedAvailableItems() {
        when(repository.findAll(5L)).thenReturn(List.of(
                item(2L, 21L, 1, false), item(1L, 12L, 3, true), item(1L, 11L, 2, true)));
        when(products.quote(any())).thenAnswer(invocation -> {
            ProductQuoteRequest request = invocation.getArgument(0);
            return request.getWindowId().equals(1L)
                    ? quote(true, product(11L, "20.50")) : quote(true, product(21L, "8.00"));
        });
        CartVO cart = service.getCart(5L);
        assertEquals(2, cart.windows().size());
        assertEquals(1L, cart.windows().getFirst().windowId());
        assertEquals(6L, cart.totalQuantity());
        assertEquals(2L, cart.checkedQuantity());
        assertEquals(0, new BigDecimal("41.00").compareTo(cart.checkedAmount()));
        assertTrue(cart.windows().getFirst().items().getFirst().available());
        CartItemVO invalid = cart.windows().getFirst().items().get(1);
        assertFalse(invalid.available());
        assertEquals("旧名称", invalid.productName());
        assertNull(invalid.unitPrice());
        assertNull(invalid.lineAmount());
        verify(products, times(2)).quote(any());
        verify(repository, never()).remove(any(), any(), any());
    }

    /** 窗口停用的已有条目仍显示，但总可结算金额为零。 */
    @Test
    void shouldKeepItemsFromDisabledWindowAsUnavailable() {
        when(repository.findAll(5L)).thenReturn(List.of(item(1L, 11L, 2, true)));
        when(products.quote(any())).thenReturn(quote(false));
        CartVO cart = service.getCart(5L);
        assertFalse(cart.windows().getFirst().windowActive());
        assertFalse(cart.windows().getFirst().items().getFirst().available());
        assertEquals(0L, cart.checkedQuantity());
        assertEquals(0, cart.checkedAmount().signum());
    }

    /** 无效商品价格不能被当作零元商品展示。 */
    @Test
    void shouldRejectMalformedQuote() {
        when(repository.findAll(5L)).thenReturn(List.of(item(1L, 11L, 2, true)));
        ProductQuoteVO malformed = product(11L, "1.00");
        malformed.setPrice(null);
        when(products.quote(any())).thenReturn(quote(true, malformed));
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> service.getCart(5L)).getErrorCode());
    }

    /** 数量、勾选及删除操作无需商品服务即可清理失效项。 */
    @Test
    void shouldAllowLocalEditsWithoutProductService() {
        service.updateQuantity(5L, 1L, 11L, 3);
        service.updateChecked(5L, 1L, 11L, false);
        service.removeItem(5L, 1L, 11L);
        service.clearWindow(5L, 1L);
        verify(repository).updateQuantity(5L, 1L, 11L, 3);
        verify(repository).updateChecked(5L, 1L, 11L, false);
        verify(repository).remove(5L, 1L, 11L);
        verify(repository).clearWindow(5L, 1L);
        verifyNoInteractions(products);
    }

    /** 畸形身份和路径 ID 必须在访问任何数据前被拒绝。 */
    @Test
    void shouldRejectInvalidIdentityAndIds() {
        assertEquals(CommonErrorCode.UNAUTHORIZED,
                assertThrows(BusinessException.class, () -> service.getCart(0L)).getErrorCode());
        assertEquals(CommonErrorCode.BAD_REQUEST,
                assertThrows(BusinessException.class, () -> service.clearWindow(5L, -1L)).getErrorCode());
        verifyNoInteractions(repository, products);
    }

    /**
     * 创建添加 DTO。
     *
     * @param productId 菜品 ID
     * @param quantity 增加数量
     * @return 请求对象
     */
    private CartItemAddRequest add(Long productId, Integer quantity) {
        CartItemAddRequest request = new CartItemAddRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        return request;
    }

    /**
     * 创建可售商品事实。
     *
     * @param id 菜品 ID
     * @param price 当前价格字符串
     * @return 核价结果
     */
    private ProductQuoteVO product(Long id, String price) {
        return new ProductQuoteVO(id, "P" + id, "商品" + id, new BigDecimal(price));
    }

    /**
     * 创建商品响应。
     *
     * @param active 窗口是否启用
     * @param products 本次返回的可售商品
     * @return 统一核价响应
     */
    private ApiResponse<ProductQuoteResponse> quote(boolean active, ProductQuoteVO... products) {
        return ApiResponse.success(new ProductQuoteResponse(active, List.of(products)));
    }

    /**
     * 创建已有购物车存储条目。
     *
     * @param window 窗口 ID
     * @param product 菜品 ID
     * @param quantity 购买数量
     * @param checked 是否勾选
     * @return 存储对象
     */
    private CartItemDO item(Long window, Long product, int quantity, boolean checked) {
        return new CartItemDO(window, product, quantity, checked, "revision", "旧名称", "OLD");
    }
}
