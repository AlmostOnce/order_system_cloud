package com.hbue.ordering.product.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductRatingMapper {
    record RatingRow(long reviewCount, long scoreTotal) {}
    @Insert("""
        INSERT INTO product_rating_stats(product_id,review_count,score_total,revision)
        SELECT product_id,#{reviewCount},#{scoreTotal},#{revision} FROM products WHERE product_id=#{productId}
        ON DUPLICATE KEY UPDATE
          review_count=IF(VALUES(revision)>revision,VALUES(review_count),review_count),
          score_total=IF(VALUES(revision)>revision,VALUES(score_total),score_total),
          revision=GREATEST(revision,VALUES(revision))
        """)
    int upsert(@Param("productId") Long productId, @Param("reviewCount") long reviewCount,
            @Param("scoreTotal") long scoreTotal, @Param("revision") long revision);
    @Select("SELECT review_count,score_total FROM product_rating_stats WHERE product_id=#{id}")
    RatingRow get(@Param("id") Long id);
}
