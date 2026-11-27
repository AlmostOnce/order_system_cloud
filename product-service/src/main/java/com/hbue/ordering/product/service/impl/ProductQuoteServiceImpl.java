package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.product.api.dto.ProductQuoteItemRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import com.hbue.ordering.product.cache.ProductBloomFilterManager;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.mapper.RestaurantMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.model.RestaurantDO;
import com.hbue.ordering.product.service.ProductQuoteService;
import com.hbue.ordering.product.service.ProductSpecificationService;
import com.hbue.ordering.product.vo.ProductSpecificationGroupVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于商品数据库提供当前窗口可售菜品和价格的内部核价实现。
 *
 * <p>本服务是订单服务创建订单前的商品事实来源，不信任调用方提交的价格；
 * 对窗口启用状态、菜品所属窗口、售卖状态和分类启用状态进行数据库核验。
 * 核价结果不使用菜单缓存，布隆过滤器只排除确定不存在的菜品 ID，不能替代数据库校验。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Service
public class ProductQuoteServiceImpl implements ProductQuoteService {

    /**
     * 单次核价允许的最大菜品数量。
     */
    private static final int MAX_PRODUCTS_PER_QUOTE =
            ProductQuoteRequest.MAX_PRODUCT_IDS;

    /**
     * 启用状态值。
     */
    private static final int ACTIVE_STATUS = 1;

    /**
     * 窗口营业状态：1 营业，0 休息。休息中的档口允许浏览菜单，但不允许下单。
     */
    private static final int OPEN_STATUS = 1;

    /**
     * 在售状态值。
     */
    private static final int ON_SALE_STATUS = 1;

    /**
     * 与商品及订单明细 DECIMAL(10,2) 一致的最高可报价单价。
     */
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("99999999.99");

    /**
     * 窗口数据 Mapper。
     */
    private final DiningWindowMapper diningWindowMapper;

    /**
     * 餐厅数据 Mapper，用于确认档口所属餐厅仍处于启用状态。
     */
    private final RestaurantMapper restaurantMapper;

    /**
     * 分类数据 Mapper。
     */
    private final ProductCategoryMapper productCategoryMapper;

    /**
     * 菜品数据 Mapper。
     */
    private final ProductMapper productMapper;

    /**
     * 商品布隆过滤器管理器。
     */
    private final ProductBloomFilterManager bloomFilterManager;

    /**
     * 当前启用规格的数据库读取能力，不使用可能过期的菜单缓存。
     */
    private final ProductSpecificationService specifications;

    /**
     * 创建商品核价服务。
     *
     * @param diningWindowMapper    窗口数据 Mapper
     * @param restaurantMapper      餐厅数据 Mapper
     * @param productCategoryMapper 分类数据 Mapper
     * @param productMapper         菜品数据 Mapper
     * @param bloomFilterManager    商品布隆过滤器管理器
     * @param specifications        规格批量查询服务
     */
    public ProductQuoteServiceImpl(
            DiningWindowMapper diningWindowMapper,
            RestaurantMapper restaurantMapper,
            ProductCategoryMapper productCategoryMapper,
            ProductMapper productMapper,
            ProductBloomFilterManager bloomFilterManager,
            ProductSpecificationService specifications
    ) {
        this.diningWindowMapper = diningWindowMapper;
        this.restaurantMapper = restaurantMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.productMapper = productMapper;
        this.bloomFilterManager = bloomFilterManager;
        this.specifications = specifications;
    }

    /**
     * 批量返回所选窗口内可下单菜品的当前价格。
     *
     * <p>核价结果不缓存：价格与售罄状态必须实时反映数据库；可重复读使商品和规格来自同一快照。
     * 查询前先用布隆过滤器拦掉一定不存在的菜品 ID，避免无效 ID 穿透数据库。</p>
     *
     * @param request 核价请求
     * @return 窗口状态和有效菜品核价结果
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductQuoteResponse quote(ProductQuoteRequest request) {
        List<ProductQuoteItemRequest> items = validate(request);

        DiningWindowDO window = diningWindowMapper.selectById(request.getWindowId());
        if (window == null
                || !Integer.valueOf(ACTIVE_STATUS).equals(window.getStatus())
                // 停用与休息都必须拒绝：前端禁用只是体验，下单正确性以这里为准。
                || !Integer.valueOf(OPEN_STATUS).equals(window.getIsOpen())
                // 所属餐厅停用后，即使档口状态残留为启用也必须拒绝核价，避免对停用餐厅下单。
                || !restaurantActive(window.getRestaurantId())) {
            // 用窗口状态标记拒绝本次核价，调用方据此中止订单创建。
            return new ProductQuoteResponse(false, List.of());
        }

        // 用布隆过滤器过滤掉一定不存在的菜品 ID，保留可能存在的候选集合。
        List<Long> candidateIds = new ArrayList<>();
        for (Long productId : items.stream().map(ProductQuoteItemRequest::getProductId).distinct().toList()) {
            if (bloomFilterManager.mightContainProduct(productId)) {
                candidateIds.add(productId);
            }
        }
        // 候选集合为空时直接返回，绝不能用空集合拼装 in 条件查库。
        if (candidateIds.isEmpty()) {
            // 空集合不应进入 IN 查询，避免 ORM 对空条件的处理产生非预期 SQL。
            return new ProductQuoteResponse(true, List.of());
        }

        List<ProductDO> availableProducts = productMapper.selectList(
                new QueryWrapper<ProductDO>()
                        .eq("window_id", request.getWindowId())
                        .eq("sale_status", ON_SALE_STATUS)
                        .in("product_id", candidateIds)
        );

        HashSet<Long> categoryIds = new HashSet<>();
        for (ProductDO product : availableProducts) {
            if (product.getCategoryId() != null) {
                categoryIds.add(product.getCategoryId());
            }
        }

        HashSet<Long> activeCategoryIds = new HashSet<>();
        if (!categoryIds.isEmpty()) {
            List<ProductCategoryDO> activeCategories =
                    productCategoryMapper.selectList(
                            new QueryWrapper<ProductCategoryDO>()
                                    .eq("window_id", request.getWindowId())
                                    .eq("status", ACTIVE_STATUS)
                                    .in("category_id", categoryIds)
                    );
            for (ProductCategoryDO category : activeCategories) {
                activeCategoryIds.add(category.getCategoryId());
            }
        }

        Map<Long, ProductDO> productsById = new HashMap<>();
        for (ProductDO product : availableProducts) {
            productsById.put(product.getProductId(), product);
        }

        // 与商品查询处于同一只读事务，避免一次核价混用规格替换前后的组和选项。
        Map<Long, List<ProductSpecificationGroupVO>> configurations = specifications.loadActive(productsById.keySet());
        List<ProductQuoteVO> quotes = new ArrayList<>();
        for (ProductQuoteItemRequest item : items) {
            ProductDO product = productsById.get(item.getProductId());
            if (product != null
                    && (product.getCategoryId() == null
                    || activeCategoryIds.contains(product.getCategoryId()))) {
                quotes.add(quoteSelection(product, item.getSelectedOptionIds(),
                        configurations.getOrDefault(product.getProductId(), List.of())));
            }
        }

        return new ProductQuoteResponse(true, quotes);
    }

    /**
     * 判断窗口所属餐厅是否可用：未挂餐厅的历史窗口保持可用，挂了餐厅则必须存在且启用。
     *
     * <p>餐厅停用后 `/products/restaurants` 不再返回它，若核价仍放行会产生"列表看不到、却能下单"的
     * 不一致状态，因此这里与菜单读取使用同一判定。</p>
     *
     * @param restaurantId 窗口所属餐厅 ID，可为空
     * @return 可以继续核价时返回 true
     */
    private boolean restaurantActive(Long restaurantId) {
        if (restaurantId == null) return true;
        RestaurantDO restaurant = restaurantMapper.selectById(restaurantId);
        return restaurant != null && Integer.valueOf(ACTIVE_STATUS).equals(restaurant.getStatus());
    }

    /**
     * 核对一行选项归属、每组单选及必选完整性；任何无效选择都拒绝整批核价，不返回猜测价格。
     * 显示文字按规格组顺序生成，选择键仅由排序后的编号生成，输入顺序不会改变购物车身份。
     */
    private ProductQuoteVO quoteSelection(ProductDO product, List<Long> selectedIds,
                                          List<ProductSpecificationGroupVO> configurations) {
        Set<Long> remaining = new HashSet<>(selectedIds);
        BigDecimal price = product.getPrice();
        List<String> descriptions = new ArrayList<>();
        // JSON节点负责转义名称；仅从当前核价事务已读取的组/选项构造，不接受调用方传入的标签。
        var selectionSnapshot = JsonNodeFactory.instance.arrayNode();
        for (ProductSpecificationGroupVO group : configurations) {
            List<ProductSpecificationGroupVO.Option> selected = group.getOptions().stream()
                    .filter(option -> remaining.contains(option.getOptionId())).toList();
            if (selected.size() > 1 || Boolean.TRUE.equals(group.getRequired()) && selected.isEmpty()) {
                throw badRequest("每组规格最多选择一项，且必选规格不能为空");
            }
            if (!selected.isEmpty()) {
                ProductSpecificationGroupVO.Option option = selected.getFirst();
                if (option.getAdditionalPrice() == null || option.getAdditionalPrice().signum() < 0) {
                    throw badRequest("规格加价配置无效，请重新选择");
                }
                price = price.add(option.getAdditionalPrice());
                descriptions.add(group.getName() + "：" + option.getName());
                selectionSnapshot.addObject().put("groupId", group.getGroupId())
                        .put("groupName", group.getName()).put("optionId", option.getOptionId())
                        .put("optionName", option.getName());
                remaining.remove(option.getOptionId());
            }
        }
        if (!remaining.isEmpty()) throw badRequest("选择的规格已停用或不属于该菜品");
        // 基础售价与多组选项加价的总和必须能写入 order_items.unit_price_snapshot DECIMAL(10,2)。
        if (price.compareTo(MAX_UNIT_PRICE) > 0) throw badRequest("所选规格后的菜品单价超出系统金额上限");
        List<Long> normalized = selectedIds.stream().sorted().toList();
        return new ProductQuoteVO(product.getProductId(), product.getProductCode(), product.getProductName(),
                price, selectionKey(normalized), normalized, String.join("；", descriptions), selectionSnapshot.toString());
    }

    /**
     * 兼容旧productIds并在服务边界校验行数、编号、选择数量和组合唯一性。
     * 同菜不同规格可分行，重复选项或同菜重复组合整体拒绝；两种协议同时提交会产生歧义，因此拒绝。
     */
    private List<ProductQuoteItemRequest> validate(ProductQuoteRequest request) {
        if (request == null || request.getWindowId() == null || request.getWindowId() <= 0)
            throw badRequest("核价请求参数无效");
        boolean hasItems = request.getItems() != null && !request.getItems().isEmpty();
        boolean hasLegacyIds = request.getProductIds() != null && !request.getProductIds().isEmpty();
        if (hasItems == hasLegacyIds) throw badRequest("items与productIds必须且只能提交一个");
        List<ProductQuoteItemRequest> items = request.getItems();
        if (!hasItems) {
            if (request.getProductIds().size() > MAX_PRODUCTS_PER_QUOTE) throw badRequest("核价最多100行");
            items = request.getProductIds().stream().map(id -> {
                ProductQuoteItemRequest item = new ProductQuoteItemRequest();
                item.setProductId(id);
                return item;
            }).toList();
        }
        if (items.size() > MAX_PRODUCTS_PER_QUOTE) throw badRequest("核价最多100行");
        Set<String> identities = new HashSet<>();
        for (ProductQuoteItemRequest item : items) {
            if (item == null || item.getProductId() == null || item.getProductId() <= 0
                    || item.getSelectedOptionIds() == null || item.getSelectedOptionIds().size() > 10
                    || item.getSelectedOptionIds().stream().anyMatch(id -> id == null || id <= 0)
                    || new HashSet<>(item.getSelectedOptionIds()).size() != item.getSelectedOptionIds().size())
                throw badRequest("菜品或规格编号无效、重复，或规格选择超过10项");
            if (!identities.add(item.getProductId() + ":" + selectionKey(item.getSelectedOptionIds())))
                throw badRequest("同一菜品的相同规格不可重复核价");
        }
        return items;
    }

    /**
     * 选择键为base或升序ID以短横线连接；不依赖请求顺序、组名或选项名称。
     */
    private String selectionKey(List<Long> ids) {
        return ids.isEmpty() ? "base" : ids.stream().sorted().map(String::valueOf).collect(Collectors.joining("-"));
    }

    /**
     * 核价信任边界校验失败统一返回400，不采纳任何客户端传入金额。
     */
    private BusinessException badRequest(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }
}
