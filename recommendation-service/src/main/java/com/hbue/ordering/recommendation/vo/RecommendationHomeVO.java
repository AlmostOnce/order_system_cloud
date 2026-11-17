package com.hbue.ordering.recommendation.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 学生首页推荐响应；不包含用户身份、评分明细或其他顾客的行为数据。 */
public record RecommendationHomeVO(String recommendationId, String algorithmVersion, String mode,
        LocalDateTime generatedAt, List<Item> items) {
    /** 一张菜品卡片；基础价在有规格时明确标注为起价。 */
    public record Item(Long productId, Long windowId, Long restaurantId, String productName, String imageUrl,
            BigDecimal basePrice, boolean hasSpecifications, String priceType, String reasonCode) { }
}
