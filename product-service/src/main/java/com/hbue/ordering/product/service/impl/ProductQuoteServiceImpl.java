package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
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
 * 基于商品数据库提供当前窗口可售菜品和价格。
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

    /**
     * 创建商品核价服务。
     *
     * @param diningWindowMapper 窗口数据 Mapper
     * @param productCategoryMapper 分类数据 Mapper
     * @param productMapper 菜品数据 Mapper
     */
    public ProductQuoteServiceImpl(
            DiningWindowMapper diningWindowMapper,
            ProductCategoryMapper productCategoryMapper,
            ProductMapper productMapper
    ) {
        this.diningWindowMapper = diningWindowMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.productMapper = productMapper;
    }

    /**
     * 批量返回所选窗口内可下单菜品的当前价格。
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
            return new ProductQuoteResponse(false, List.of());
        }

        List<ProductDO> availableProducts = productMapper.selectList(
                new QueryWrapper<ProductDO>()
                        .eq("window_id", request.getWindowId())
                        .eq("sale_status", ON_SALE_STATUS)
                        .in("product_id", request.getProductIds())
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
