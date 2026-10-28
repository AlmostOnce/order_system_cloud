package com.hbue.ordering.comment.api;
import java.math.BigDecimal;
import java.math.RoundingMode;
public record ProductRatingVO(Long productId, long reviewCount, BigDecimal averageScore) {
    public static ProductRatingVO of(Long productId, long count, long sum) {
        return new ProductRatingVO(productId, count, count == 0 ? null :
                BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
    }
}
