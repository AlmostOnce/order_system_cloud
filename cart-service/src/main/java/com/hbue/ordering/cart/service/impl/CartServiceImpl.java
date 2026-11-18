package com.hbue.ordering.cart.service.impl;

import com.hbue.ordering.cart.config.CartProperties;
import com.hbue.ordering.cart.dto.request.CartItemAddRequest;
import com.hbue.ordering.cart.model.CartItemDO;
import com.hbue.ordering.cart.model.CartSelectionKey;
import com.hbue.ordering.cart.repository.CartRedisRepository;
import com.hbue.ordering.cart.service.CartService;
import com.hbue.ordering.cart.vo.CartItemVO;
import com.hbue.ordering.cart.vo.CartVO;
import com.hbue.ordering.cart.vo.CartWindowVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.product.api.dto.ProductQuoteItemRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 购物车业务实现，编排商品核价与 Redis 原子存储。
 *
 * <p>一行购物车由菜品 ID 和商品服务生成的规格组合键共同标识；报价、展示、数量修改和下单清理
 * 都按这一对值定位。Redis 不保存可用于结算的价格，也不按规格预留库存；创建订单时仍由订单服务
 * 使用同一规格集合重新核价。</p>
 */
@Service
public class CartServiceImpl implements CartService {
    /**
     * 用户隔离的购物车存储访问层。
     */
    private final CartRedisRepository repository;
    /**
     * 商品事实来源，通过服务名调用。
     */
    private final ProductServiceClient productClient;
    /**
     * 数量与容量配置。
     */
    private final CartProperties properties;

    /**
     * 创建购物车业务服务。
     *
     * @param repository    Redis 持久化访问层
     * @param productClient 商品核价客户端
     * @param properties    购物车约束配置
     */
    public CartServiceImpl(CartRedisRepository repository, ProductServiceClient productClient,
                           CartProperties properties) {
        this.repository = repository;
        this.productClient = productClient;
        this.properties = properties;
    }

    /**
     * 读取购物车快照并按窗口批量核价；若批量请求因一条失效规格返回 400，则逐行隔离失效项。
     *
     * @param userId JWT 中的顾客编号
     * @return 当前报价和失效原因；不可售项仍保留删除入口与加入时快照
     */
    @Override
    public CartVO getCart(Long userId) {
        validateUser(userId);
        List<CartItemDO> storedItems = repository.findAll(userId);
        Map<Long, List<CartItemDO>> groups = new TreeMap<>();
        for (CartItemDO item : storedItems) {
            groups.computeIfAbsent(item.getWindowId(), ignored -> new ArrayList<>()).add(item);
        }

        List<CartWindowVO> windows = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalQuantity = 0;
        long checkedQuantity = 0;
        for (Map.Entry<Long, List<CartItemDO>> group : groups.entrySet()) {
            List<CartItemDO> rows = group.getValue();
            rows.sort(Comparator.comparing(CartItemDO::getProductId).thenComparing(CartItemDO::getSelectionKey));
            QuoteBatch quotes = queryWindowQuotes(group.getKey(), rows);
            List<CartItemVO> views = new ArrayList<>();
            BigDecimal windowAmount = BigDecimal.ZERO;
            for (CartItemDO item : rows) {
                QuoteIdentity identity = new QuoteIdentity(item.getProductId(), item.getSelectionKey());
                CartItemVO view = itemView(item, quotes.windowActive(), quotes.products().get(identity),
                        quotes.rejectedSelections().contains(identity));
                views.add(view);
                totalQuantity += item.getQuantity();
                // 已勾选但当前规格失效的条目留在购物车，且不使用旧价格参与本地结算预览。
                if (view.checked() && view.available()) {
                    windowAmount = windowAmount.add(view.lineAmount());
                    checkedQuantity += view.quantity();
                }
            }
            windows.add(new CartWindowVO(group.getKey(), quotes.windowActive(), views, windowAmount));
            totalAmount = totalAmount.add(windowAmount);
        }
        return new CartVO(windows, totalQuantity, checkedQuantity, totalAmount);
    }

    /**
     * 核验菜品及全部选项，再使用商品服务返回的规范键和规格文字写入 Redis。
     *
     * @param userId   JWT 中的顾客编号
     * @param windowId 窗口编号
     * @param request  菜品、增加数量及选择的选项编号
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
        List<Long> selectedOptionIds = normalizeRequestedOptionIds(request.getSelectedOptionIds());
        ProductQuoteItemRequest quoteLine = quoteLine(request.getProductId(), selectedOptionIds);
        ProductQuoteResponse response;
        try {
            response = callQuote(windowId, List.of(quoteLine));
        } catch (QuoteRejectedException exception) {
            // 添加操作不能把无效选择写入购物车；调用方应重新打开商品规格选项。
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "所选规格已失效，请重新选择");
        }
        Map<QuoteIdentity, ProductQuoteVO> indexed = indexProducts(response, Set.of(
                new QuoteIdentity(request.getProductId(), requestedKey(selectedOptionIds))));
        ProductQuoteVO product = indexed.get(new QuoteIdentity(request.getProductId(), requestedKey(selectedOptionIds)));
        if (!response.isWindowActive() || product == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "窗口、菜品或所选规格不存在、已停用或不可售");
        }
        // Redis 只使用商品服务核验后的键、名称、编码和展示文字；前端不提交金额或规格键。
        repository.add(userId, windowId, request.getProductId(), product.getSelectionKey(), request.getQuantity(),
                product.getProductName(), product.getProductCode(), product.getSpecificationText());
    }

    /**
     * 旧路由只修改无规格 base 行，不能根据菜品 ID 猜测规格。
     */
    @Override
    public void updateQuantity(Long userId, Long windowId, Long productId, Integer quantity) {
        updateQuantity(userId, windowId, productId, CartSelectionKey.BASE, quantity);
    }

    /**
     * 设置指定规格行数量；规格失效后仍允许修改或删除购物车本地记录。
     */
    @Override
    public void updateQuantity(Long userId, Long windowId, Long productId, String selectionKey, Integer quantity) {
        validateUser(userId);
        positiveId(windowId);
        positiveId(productId);
        validateSelectionKey(selectionKey);
        validateQuantity(quantity);
        repository.updateQuantity(userId, windowId, productId, selectionKey, quantity);
    }

    /**
     * 旧路由只修改无规格 base 行。
     */
    @Override
    public void updateChecked(Long userId, Long windowId, Long productId, Boolean checked) {
        updateChecked(userId, windowId, productId, CartSelectionKey.BASE, checked);
    }

    /**
     * 设置指定规格行勾选状态，允许取消失效规格的勾选。
     */
    @Override
    public void updateChecked(Long userId, Long windowId, Long productId, String selectionKey, Boolean checked) {
        validateUser(userId);
        positiveId(windowId);
        positiveId(productId);
        validateSelectionKey(selectionKey);
        if (checked == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "勾选状态不能为空");
        }
        repository.updateChecked(userId, windowId, productId, selectionKey, checked);
    }

    /**
     * 旧路由幂等删除 base 行，不会删除同菜的有规格行。
     */
    @Override
    public void removeItem(Long userId, Long windowId, Long productId) {
        removeItem(userId, windowId, productId, CartSelectionKey.BASE);
    }

    /**
     * 删除指定规格行；旧失效项仍可删除，不依赖商品服务恢复。
     */
    @Override
    public void removeItem(Long userId, Long windowId, Long productId, String selectionKey) {
        validateUser(userId);
        positiveId(windowId);
        positiveId(productId);
        validateSelectionKey(selectionKey);
        repository.remove(userId, windowId, productId, selectionKey);
    }

    /**
     * 清空当前用户一个窗口的所有规格行，其他窗口保留。
     */
    @Override
    public void clearWindow(Long userId, Long windowId) {
        validateUser(userId);
        positiveId(windowId);
        repository.clearWindow(userId, windowId);
    }

    /**
     * 先批量查询以减少服务间请求；如果商品服务因一个失效规格拒绝整批，则退化为逐行报价。
     * 逐行只有 400 的行被标记不可售，其它行仍显示实时价格；网络、降级或契约异常仍返回 503。
     */
    private QuoteBatch queryWindowQuotes(Long windowId, List<CartItemDO> rows) {
        List<ProductQuoteItemRequest> quoteLines = rows.stream()
                .map(row -> quoteLine(row.getProductId(), CartSelectionKey.optionIds(row.getSelectionKey())))
                .toList();
        Set<QuoteIdentity> requested = new HashSet<>();
        for (CartItemDO row : rows) {
            if (!requested.add(new QuoteIdentity(row.getProductId(), row.getSelectionKey()))) {
                throw productUnavailable();
            }
        }
        try {
            ProductQuoteResponse response = callQuote(windowId, quoteLines);
            return new QuoteBatch(response.isWindowActive(), indexProducts(response, requested), Set.of());
        } catch (QuoteRejectedException exception) {
            Map<QuoteIdentity, ProductQuoteVO> products = new HashMap<>();
            Set<QuoteIdentity> rejected = new HashSet<>();
            boolean windowActive = true;
            for (int index = 0; index < rows.size(); index++) {
                CartItemDO row = rows.get(index);
                QuoteIdentity identity = new QuoteIdentity(row.getProductId(), row.getSelectionKey());
                try {
                    ProductQuoteResponse response = callQuote(windowId, List.of(quoteLines.get(index)));
                    windowActive &= response.isWindowActive();
                    products.putAll(indexProducts(response, Set.of(identity)));
                } catch (QuoteRejectedException invalidSelection) {
                    // 只隔离这行失效规格；保留 Redis 快照，让顾客可以移除或重新选择规格后再加入。
                    rejected.add(identity);
                }
            }
            return new QuoteBatch(windowActive, products, rejected);
        }
    }

    /**
     * 调用商品服务的逐行核价接口；商品服务始终是规格键与价格的权威来源。
     */
    private ProductQuoteResponse callQuote(Long windowId, List<ProductQuoteItemRequest> items) {
        ProductQuoteRequest request = new ProductQuoteRequest();
        request.setWindowId(windowId);
        request.setItems(items);
        try {
            ApiResponse<ProductQuoteResponse> response = productClient.quote(request);
            if (response == null) {
                throw productUnavailable();
            }
            if (CommonErrorCode.BAD_REQUEST.code().equals(response.getCode())) {
                throw new QuoteRejectedException();
            }
            if (!CommonErrorCode.SUCCESS.code().equals(response.getCode()) || response.getData() == null) {
                throw productUnavailable();
            }
            return response.getData();
        } catch (FeignException exception) {
            // HTTP 400 表示规格或请求契约拒绝；其它状态多为服务故障，不能误显示成菜品不可售。
            if (exception.status() == 400) {
                throw new QuoteRejectedException();
            }
            throw productUnavailable();
        }
    }

    /**
     * 校验商品报价响应契约并用菜品+规范规格键建立索引，拒绝越界、重复、金额或选项不一致的响应。
     * 缺失的请求行留给调用方按下架/不可售处理，绝不使用请求端价格兜底。
     */
    private Map<QuoteIdentity, ProductQuoteVO> indexProducts(ProductQuoteResponse quote, Set<QuoteIdentity> requested) {
        Map<QuoteIdentity, ProductQuoteVO> products = new HashMap<>();
        if (quote == null) {
            throw productUnavailable();
        }
        if (!quote.isWindowActive()) {
            if (quote.getProducts() != null && !quote.getProducts().isEmpty()) {
                throw productUnavailable();
            }
            return products;
        }
        if (quote.getProducts() == null) {
            throw productUnavailable();
        }
        for (ProductQuoteVO product : quote.getProducts()) {
            if (product == null || product.getProductId() == null || product.getProductId() < 1
                    || product.getPrice() == null || product.getPrice().signum() < 0
                    || product.getProductName() == null || product.getProductName().isBlank()
                    || product.getProductCode() == null || product.getProductCode().isBlank()
                    || product.getSelectedOptionIds() == null || product.getSpecificationText() == null) {
                throw productUnavailable();
            }
            String selectionKey;
            List<Long> optionIds;
            try {
                selectionKey = CartSelectionKey.normalize(product.getSelectionKey());
                optionIds = CartSelectionKey.optionIds(selectionKey);
            } catch (IllegalArgumentException exception) {
                throw productUnavailable();
            }
            if (!optionIds.equals(product.getSelectedOptionIds())) {
                throw productUnavailable();
            }
            QuoteIdentity identity = new QuoteIdentity(product.getProductId(), selectionKey);
            if (!requested.contains(identity) || products.putIfAbsent(identity, product) != null) {
                throw productUnavailable();
            }
        }
        return products;
    }

    /**
     * 将 Redis 行和当前商品报价合成响应；失效行使用加入时快照且不参与金额。
     */
    private CartItemVO itemView(CartItemDO item, boolean windowActive, ProductQuoteVO product, boolean rejected) {
        boolean available = windowActive && !rejected && product != null;
        BigDecimal price = available ? product.getPrice() : null;
        BigDecimal amount = available ? price.multiply(BigDecimal.valueOf(item.getQuantity())) : null;
        String reason = available ? null : (!windowActive ? "窗口已停用或不存在"
                : (rejected ? "规格已失效，请返回菜单重新选择" : "菜品或规格已下架、暂不可售"));
        String name = available ? product.getProductName() : item.getProductNameSnapshot();
        String specificationText = available ? product.getSpecificationText() : item.getSpecificationTextSnapshot();
        return new CartItemVO(item.getProductId(), name,
                available ? product.getProductCode() : item.getProductCodeSnapshot(), item.getQuantity(),
                item.getChecked(), item.getRevision(), available, reason, price, amount,
                item.getSelectionKey(), CartSelectionKey.optionIds(item.getSelectionKey()), specificationText);
    }

    /**
     * 生成商品服务核价 DTO；主键数组只在 HTTP JSON 层传递，Redis/Lua 只保存字符串规范键。
     */
    private ProductQuoteItemRequest quoteLine(Long productId, List<Long> optionIds) {
        ProductQuoteItemRequest request = new ProductQuoteItemRequest();
        request.setProductId(productId);
        request.setSelectedOptionIds(optionIds);
        return request;
    }

    /**
     * 校验前端规格选项集合，不接受重复 ID 或超过单选组选项数量的请求。
     */
    private List<Long> normalizeRequestedOptionIds(List<Long> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return List.of();
        }
        if (optionIds.size() > 10 || optionIds.stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(optionIds).size() != optionIds.size()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "规格选项编号重复或无效");
        }
        // 商品服务接受任意顺序，但购物车身份只能使用升序规范键。
        return optionIds.stream().sorted().toList();
    }

    /**
     * 根据选项编号本地构造比较键，仅用于匹配输入；最终落库键仍采用商品服务返回值。
     */
    private String requestedKey(List<Long> optionIds) {
        return optionIds.isEmpty() ? CartSelectionKey.BASE : String.join("-", optionIds.stream()
                .map(String::valueOf).toList());
    }

    /**
     * 严格拒绝不规范规格键，避免客户端通过路径参数指向错误 Redis Field。
     */
    private void validateSelectionKey(String selectionKey) {
        try {
            CartSelectionKey.normalize(selectionKey);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "购物车规格键格式不正确");
        }
    }

    /**
     * 在远程调用前拒绝无效用户身份。
     */
    private void validateUser(Long userId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录用户身份无效");
        }
    }

    /**
     * 统一校验窗口或菜品 ID；路径参数错误应返回 400。
     */
    private void positiveId(Long id) {
        if (id == null || id < 1) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "窗口与菜品 ID 必须大于零");
        }
    }

    /**
     * 单次数量由 Java 校验，累加后的最终数量由 Lua 再校验以处理并发。
     */
    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > properties.maxQuantity()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST,
                    "菜品数量必须为 1~" + properties.maxQuantity());
        }
    }

    /**
     * 商品服务故障、降级或响应契约破坏统一返回 503，不伪装成商品下架。
     */
    private BusinessException productUnavailable() {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "商品服务暂时不可用，请稍后重试");
    }

    /**
     * 商品服务明确以 400 拒绝的规格行，用于查询购物车时做逐行隔离。
     */
    private static final class QuoteRejectedException extends RuntimeException {
    }

    /**
     * 菜品与规格组合共同构成购物车行身份。
     */
    private record QuoteIdentity(Long productId, String selectionKey) {
    }

    /**
     * 一窗口的报价结果及被商品服务明确拒绝的规格行。
     */
    private record QuoteBatch(boolean windowActive, Map<QuoteIdentity, ProductQuoteVO> products,
                              Set<QuoteIdentity> rejectedSelections) {
    }
}
