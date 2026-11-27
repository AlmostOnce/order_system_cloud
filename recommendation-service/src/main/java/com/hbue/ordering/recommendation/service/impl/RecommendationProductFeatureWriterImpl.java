package com.hbue.ordering.recommendation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.product.api.dto.ProductRecommendationDocument;
import com.hbue.ordering.recommendation.mapper.RecommendationProductFeatureMapper;
import com.hbue.ordering.recommendation.model.RecommendationProductFeatureDO;
import com.hbue.ordering.recommendation.service.RecommendationProductFeatureWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 原子替换商品窗口的推荐读模型；只在上游给出完整成功快照后撤下缺失商品。
 */
@Service
public class RecommendationProductFeatureWriterImpl implements RecommendationProductFeatureWriter {
    private final RecommendationProductFeatureMapper products;
    private final ObjectMapper json;

    /**
     * 注入本地商品投影与 JSON 编码器，避免推荐服务直连商品数据库。
     */
    public RecommendationProductFeatureWriterImpl(RecommendationProductFeatureMapper products, ObjectMapper json) {
        this.products = products;
        this.json = json;
    }

    /**
     * 批量写入完整窗口快照；事务覆盖所有 upsert 和撤架，任何序列化/SQL 失败都保留旧目录版本。
     * 空数组只代表源服务成功确认该窗口当前没有可售商品。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void replaceWindow(Long windowId, List<ProductRecommendationDocument> documents) {
        if (windowId == null || windowId <= 0 || documents == null || documents.size() > 5000) {
            throw new IllegalArgumentException("推荐商品目录窗口快照无效或超出5000项");
        }
        Set<Long> ids = new HashSet<>();
        LocalDateTime updatedAt = LocalDateTime.now();
        for (ProductRecommendationDocument document : documents) {
            validate(document, windowId, ids);
            RecommendationProductFeatureDO row = toRow(document, updatedAt);
            products.upsertDocument(row);
        }
        if (ids.isEmpty()) products.markWindowUnavailable(windowId);
        else products.markMissingUnavailable(windowId, List.copyOf(ids));
    }

    /**
     * 单菜刷新根据商品域当前结果更新或撤架；其余窗口目录不受影响。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void replaceProduct(Long windowId, Long productId, List<ProductRecommendationDocument> documents) {
        if (windowId == null || windowId <= 0 || productId == null || productId <= 0
                || documents == null || documents.size() > 1) {
            throw new IllegalArgumentException("推荐单菜快照参数无效");
        }
        if (documents.isEmpty()) {
            products.markProductUnavailable(windowId, productId);
            return;
        }
        ProductRecommendationDocument document = documents.getFirst();
        if (!productId.equals(document.productId())) throw new IllegalArgumentException("商品服务返回了错误菜品编号");
        validate(document, windowId, new HashSet<>());
        products.upsertDocument(toRow(document, LocalDateTime.now()));
    }

    /**
     * 只在商品服务成功返回完整窗口清单后清理已消失窗口，避免单个窗口读取失败清空有效候选。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markWindowsMissing(List<Long> activeWindowIds) {
        if (activeWindowIds == null || activeWindowIds.size() > 5000
                || activeWindowIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("当前推荐窗口清单无效");
        }
        if (activeWindowIds.isEmpty()) products.markAllUnavailable();
        else products.markWindowsMissingUnavailable(activeWindowIds.stream().distinct().toList());
    }

    /**
     * 上游快照需与请求窗口、商品价格和规格选项保持一致；不接收推荐服务自行推导的商品状态。
     */
    private void validate(ProductRecommendationDocument document, Long windowId, Set<Long> ids) {
        if (document == null || document.productId() == null || document.productId() <= 0
                || !windowId.equals(document.windowId()) || document.restaurantId() == null
                || document.restaurantId() <= 0 || document.productName() == null || document.productName().isBlank()
                || document.basePrice() == null || document.basePrice().signum() < 0
                || document.specificationGroups() == null || !ids.add(document.productId())) {
            throw new IllegalArgumentException("商品服务返回的推荐快照无效或存在重复菜品");
        }
        if (document.hasSpecifications() != !document.specificationGroups().isEmpty()) {
            throw new IllegalArgumentException("商品规格标识与启用规格组不一致");
        }
        Set<Long> groupIds = new HashSet<>();
        Set<Long> optionIds = new HashSet<>();
        for (ProductRecommendationDocument.SpecificationGroup group : document.specificationGroups()) {
            if (group == null || group.groupId() == null || group.groupId() <= 0
                    || group.groupName() == null || group.groupName().isBlank() || !groupIds.add(group.groupId())
                    || group.options() == null) throw new IllegalArgumentException("商品规格组快照无效");
            for (ProductRecommendationDocument.SpecificationOption option : group.options()) {
                if (option == null || option.optionId() == null || option.optionId() <= 0
                        || option.optionName() == null || option.optionName().isBlank()
                        || !optionIds.add(option.optionId()))
                    throw new IllegalArgumentException("商品规格选项快照无效");
            }
        }
    }

    /**
     * 保存当前基础售价和规格配置；未保存规格加价，避免推荐投影被误用作权威核价数据。
     */
    private RecommendationProductFeatureDO toRow(ProductRecommendationDocument document, LocalDateTime updatedAt) {
        try {
            RecommendationProductFeatureDO row = new RecommendationProductFeatureDO();
            row.setProductId(document.productId());
            row.setCategoryId(document.categoryId());
            row.setWindowId(document.windowId());
            row.setRestaurantId(document.restaurantId());
            row.setWindowName(document.windowName() == null ? "" : document.windowName());
            row.setCategoryName(document.categoryName());
            row.setProductName(document.productName());
            row.setDescription(document.description());
            row.setImageUrl(document.imageUrl());
            row.setBasePrice(document.basePrice());
            row.setSortOrder(document.sortOrder() == null ? 0 : document.sortOrder());
            row.setHasSpecifications(document.hasSpecifications());
            row.setSpecificationGroups(json.writeValueAsString(document.specificationGroups()));
            row.setAvailable(true);
            row.setUpdatedAt(updatedAt);
            return row;
        } catch (Exception exception) {
            throw new IllegalStateException("商品规格快照编码失败", exception);
        }
    }
}
