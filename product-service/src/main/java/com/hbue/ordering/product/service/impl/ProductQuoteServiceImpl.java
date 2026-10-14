package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import com.hbue.ordering.product.cache.ProductBloomFilterManager;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.service.ProductQuoteService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    /** 单次核价允许的最大菜品数量。 */
    private static final int MAX_PRODUCTS_PER_QUOTE =
            ProductQuoteRequest.MAX_PRODUCT_IDS;

    /** 启用状态值。 */
    private static final int ACTIVE_STATUS = 1;

    /** 在售状态值。 */
    private static final int ON_SALE_STATUS = 1;

    /** 窗口数据 Mapper。 */
    private final DiningWindowMapper diningWindowMapper;

    /** 分类数据 Mapper。 */
    private final ProductCategoryMapper productCategoryMapper;

    /** 菜品数据 Mapper。 */
    private final ProductMapper productMapper;

    /** 商品布隆过滤器管理器。 */
    private final ProductBloomFilterManager bloomFilterManager;

    /**
     * 创建商品核价服务。
     *
     * @param diningWindowMapper 窗口数据 Mapper
     * @param productCategoryMapper 分类数据 Mapper
     * @param productMapper 菜品数据 Mapper
     * @param bloomFilterManager 商品布隆过滤器管理器
     */
    public ProductQuoteServiceImpl(
            DiningWindowMapper diningWindowMapper,
            ProductCategoryMapper productCategoryMapper,
            ProductMapper productMapper,
            ProductBloomFilterManager bloomFilterManager
    ) {
        this.diningWindowMapper = diningWindowMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.productMapper = productMapper;
        this.bloomFilterManager = bloomFilterManager;
    }

    /**
     * 批量返回所选窗口内可下单菜品的当前价格。
     *
     * <p>核价结果不缓存：价格与售罄状态必须实时反映数据库。
     * 查询前先用布隆过滤器拦掉一定不存在的菜品 ID，避免无效 ID 穿透数据库。</p>
     *
     * @param request 核价请求
     * @return 窗口状态和有效菜品核价结果
     */
    @Override
    public ProductQuoteResponse quote(ProductQuoteRequest request) {
        validate(request);

        DiningWindowDO window = diningWindowMapper.selectById(request.getWindowId());
        if (window == null
                || !Integer.valueOf(ACTIVE_STATUS).equals(window.getStatus())) {
            // 用窗口状态标记拒绝本次核价，调用方据此中止订单创建。
            return new ProductQuoteResponse(false, List.of());
        }

        // 用布隆过滤器过滤掉一定不存在的菜品 ID，保留可能存在的候选集合。
        List<Long> candidateIds = new ArrayList<>();
        for (Long productId : request.getProductIds()) {
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

        List<ProductQuoteVO> quotes = new ArrayList<>();
        for (Long productId : request.getProductIds()) {
            ProductDO product = productsById.get(productId);
            if (product != null
                    && (product.getCategoryId() == null
                    || activeCategoryIds.contains(product.getCategoryId()))) {
                quotes.add(new ProductQuoteVO(
                        product.getProductId(),
                        product.getProductCode(),
                        product.getProductName(),
                        product.getPrice()
                ));
            }
        }

        return new ProductQuoteResponse(true, quotes);
    }

    /**
     * 校验核价请求和菜品 ID 唯一性。
     *
     * @param request 核价请求
     */
    private void validate(ProductQuoteRequest request) {
        if (request == null
                || request.getWindowId() == null
                || request.getWindowId() <= 0
                || request.getProductIds() == null
                || request.getProductIds().isEmpty()
                || request.getProductIds().size() > MAX_PRODUCTS_PER_QUOTE
                || new HashSet<>(request.getProductIds()).size()
                != request.getProductIds().size()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "核价请求参数无效"
            );
        }
        for (Long productId : request.getProductIds()) {
            if (productId == null || productId <= 0) {
                throw new BusinessException(
                        CommonErrorCode.BAD_REQUEST,
                        "核价请求参数无效"
                );
            }
        }
    }
}
