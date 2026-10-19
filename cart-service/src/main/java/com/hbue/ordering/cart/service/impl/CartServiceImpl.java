package com.hbue.ordering.cart.service.impl;

import com.hbue.ordering.cart.config.CartProperties;
import com.hbue.ordering.cart.dto.request.CartItemAddRequest;
import com.hbue.ordering.cart.model.CartItemDO;
import com.hbue.ordering.cart.repository.CartRedisRepository;
import com.hbue.ordering.cart.service.CartService;
import com.hbue.ordering.cart.vo.CartItemVO;
import com.hbue.ordering.cart.vo.CartVO;
import com.hbue.ordering.cart.vo.CartWindowVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 购物车业务实现，编排商品核验与 Redis 原子存储。
 *
 * <p>添加时核验菜品属于指定窗口且可售；查询时按窗口批量核价。
 * 失效项保留用于用户清理，价格为 null 且不计入结算预览。
 * Redis 保存的数量不预留库存，订单仍独立核价。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@Service
public class CartServiceImpl implements CartService {
    /** 用户隔离的购物车存储访问层。 */
    private final CartRedisRepository repository;
    /** 商品事实来源，通过服务名调用。 */
    private final ProductServiceClient productClient;
    /** 数量与容量配置。 */
    private final CartProperties properties;

    /**
     * 创建购物车业务服务。
     *
     * @param repository Redis 持久化访问层
     * @param productClient 商品核价客户端
     * @param properties 购物车约束配置
     */
    public CartServiceImpl(CartRedisRepository repository, ProductServiceClient productClient,
                           CartProperties properties) {
        this.repository = repository;
        this.productClient = productClient;
        this.properties = properties;
    }

    /**
     * 读取存储快照后按窗口核价，聚合当前可结算的勾选金额。
     *
     * @param userId 当前用户 ID
     * @return 包含失效项标记和预览金额的购物车
     */
    @Override
    public CartVO getCart(Long userId) {
        validateUser(userId);
        List<CartItemDO> storedItems = repository.findAll(userId);
        Map<Long, List<CartItemDO>> groups = new TreeMap<>();
        for (CartItemDO item : storedItems) {
            // 先分组再批量调用商品服务，单个窗口只发出一次请求。
            List<CartItemDO> group = groups.get(item.getWindowId());
            if (group == null) {
                group = new ArrayList<>();
                groups.put(item.getWindowId(), group);
            }
            group.add(item);
        }

        List<CartWindowVO> windows = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalQuantity = 0;
        long checkedQuantity = 0;
        for (Map.Entry<Long, List<CartItemDO>> group : groups.entrySet()) {
            group.getValue().sort(Comparator.comparing(CartItemDO::getProductId));
            List<Long> productIds = new ArrayList<>();
            for (CartItemDO item : group.getValue()) {
                productIds.add(item.getProductId());
            }
            ProductQuoteResponse quote = queryProducts(group.getKey(), productIds);
            Map<Long, ProductQuoteVO> products = indexProducts(quote, productIds);
            List<CartItemVO> views = new ArrayList<>();
            BigDecimal windowAmount = BigDecimal.ZERO;
            for (CartItemDO item : group.getValue()) {
                CartItemVO view = itemView(item, quote.isWindowActive(), products.get(item.getProductId()));
                views.add(view);
                totalQuantity += item.getQuantity();
                // 勾选但不可售的条目仍保留，但不按旧价格或零元价格参与金额计算。
                if (view.checked() && view.available()) {
                    windowAmount = windowAmount.add(view.lineAmount());
                    checkedQuantity += view.quantity();
                }
            }
            windows.add(new CartWindowVO(group.getKey(), quote.isWindowActive(), views, windowAmount));
            totalAmount = totalAmount.add(windowAmount);
        }
        return new CartVO(windows, totalQuantity, checkedQuantity, totalAmount);
    }

    /**
     * 校验菜品后原子添加；读取商品失败时不得产生购物车写入。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param request 待添加菜品与数量
     */
    @Override
    public void addItem(Long userId, Long windowId, CartItemAddRequest request) {
        validateUser(userId);
        positiveId(windowId);
        if (request == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "添加请求不能为空");
        }
        positiveId(request.getProductId());
        validateQuantity(request.getQuantity());
        List<Long> ids = List.of(request.getProductId());
        ProductQuoteResponse quote = queryProducts(windowId, ids);
        ProductQuoteVO product = indexProducts(quote, ids).get(request.getProductId());
        if (!quote.isWindowActive() || product == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "窗口或菜品不存在、已停用或不可售");
        }
        // 累加后的最终上限由 Lua 再次校验，避免两个合法请求并发后超过总数量限制。
        repository.add(userId, windowId, request.getProductId(), request.getQuantity(),
                product.getProductName(), product.getProductCode());
    }

    /**
     * 设置目标数量，不覆写其它字段，也不要求商品服务可用。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param quantity 目标数量
     */
    @Override
    public void updateQuantity(Long userId, Long windowId, Long productId, Integer quantity) {
        validateUser(userId);
        positiveId(windowId);
        positiveId(productId);
        validateQuantity(quantity);
        repository.updateQuantity(userId, windowId, productId, quantity);
    }

    /**
     * 设置勾选状态，允许用户取消失效菜品的勾选。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param checked 目标勾选状态
     */
    @Override
    public void updateChecked(Long userId, Long windowId, Long productId, Boolean checked) {
        validateUser(userId);
        positiveId(windowId);
        positiveId(productId);
        if (checked == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "勾选状态不能为空");
        }
        repository.updateChecked(userId, windowId, productId, checked);
    }

    /**
     * 删除当前用户的一项菜品；重复删除仍视为成功。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     */
    @Override
    public void removeItem(Long userId, Long windowId, Long productId) {
        validateUser(userId);
        positiveId(windowId);
        positiveId(productId);
        repository.remove(userId, windowId, productId);
    }

    /**
     * 清空当前用户的一个窗口，保留其它窗口。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     */
    @Override
    public void clearWindow(Long userId, Long windowId) {
        validateUser(userId);
        positiveId(windowId);
        repository.clearWindow(userId, windowId);
    }

    /**
     * 调用商品服务批量核价，服务故障返回 503 而不是伪造商品失效状态。
     *
     * @param windowId 窗口 ID
     * @param productIds 本窗口内不重复的菜品 ID
     * @return 商品服务返回的窗口与核价数据
     */
    private ProductQuoteResponse queryProducts(Long windowId, List<Long> productIds) {
        ProductQuoteRequest request = new ProductQuoteRequest();
        request.setWindowId(windowId);
        request.setProductIds(productIds);
        try {
            ApiResponse<ProductQuoteResponse> response = productClient.quote(request);
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                    || response.getData() == null) {
                throw productUnavailable();
            }
            return response.getData();
        } catch (FeignException exception) {
            // 正常由 Sentinel 降级工厂转换；关闭适配时仍将 HTTP 失败映射为可理解的 503。
            throw productUnavailable();
        }
    }

    /**
     * 检查商品响应的结构和价格，建立 ID 索引；缺失的 ID 表示当前不可售。
     *
     * @param quote 窗口核价响应
     * @param requestedIds 本次请求的菜品 ID
     * @return 商品 ID 索引，停用窗口返回空索引
     */
    private Map<Long, ProductQuoteVO> indexProducts(ProductQuoteResponse quote, List<Long> requestedIds) {
        Map<Long, ProductQuoteVO> products = new HashMap<>();
        if (!quote.isWindowActive()) {
            return products;
        }
        if (quote.getProducts() == null) {
            throw productUnavailable();
        }
        for (ProductQuoteVO product : quote.getProducts()) {
            // 越界 ID、重复 ID 和非法价格均按契约错误处理，不能伪造合法价格。
            if (product == null || !requestedIds.contains(product.getProductId())
                    || product.getPrice() == null || product.getPrice().signum() < 0
                    || product.getProductName() == null || product.getProductName().isBlank()
                    || product.getProductCode() == null || product.getProductCode().isBlank()
                    || products.putIfAbsent(product.getProductId(), product) != null) {
                throw productUnavailable();
            }
        }
        return products;
    }

    /**
     * 将保存状态与当前商品事实合并，失效项只显示快照、不参与金额结算。
     *
     * @param item Redis 中保存的数量和状态
     * @param windowActive 窗口当前是否启用
     * @param product 当前可售商品，缺失表示失效
     * @return 单项展示 VO
     */
    private CartItemVO itemView(CartItemDO item, boolean windowActive, ProductQuoteVO product) {
        boolean available = windowActive && product != null;
        BigDecimal price = available ? product.getPrice() : null;
        BigDecimal amount = available ? price.multiply(BigDecimal.valueOf(item.getQuantity())) : null;
        String reason = available ? null : (windowActive ? "菜品已下架或不可售" : "窗口已停用或不存在");
        String name = available ? product.getProductName() : item.getProductNameSnapshot();
        return new CartItemVO(item.getProductId(), name,
                available ? product.getProductCode() : item.getProductCodeSnapshot(), item.getQuantity(),
                item.getChecked(), item.getRevision(), available, reason, price, amount);
    }

    /**
     * 在调用任何依赖前拒绝无效用户身份。
     *
     * @param userId JWT 中解析出的用户 ID
     */
    private void validateUser(Long userId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录用户身份无效");
        }
    }

    /**
     * 统一校验窗口或菜品 ID；路径参数错误应返回 400。
     *
     * @param id 业务对象 ID
     */
    private void positiveId(Long id) {
        if (id == null || id < 1) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "窗口与菜品 ID 必须大于零");
        }
    }

    /**
     * 校验单次提交数量，原子累加的最终数量由 Lua 再校验。
     *
     * @param quantity 待写入数量
     */
    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > properties.maxQuantity()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST,
                    "菜品数量必须为 1~" + properties.maxQuantity());
        }
    }

    /**
     * 生成商品依赖不可用响应，不把远程服务异常直接暴露给客户端。
     *
     * @return 对应 HTTP 503 的业务异常
     */
    private BusinessException productUnavailable() {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "商品服务暂时不可用，请稍后重试");
    }
}
