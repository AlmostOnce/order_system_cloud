package com.hbue.ordering.product.rating;
import com.hbue.ordering.comment.api.ProductRatingChangedEvent;
import com.hbue.ordering.comment.api.ProductRatingVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 商品库中的评分读模型；按源版本覆盖快照，不对消息增量累加。 */
@Service
public class ProductRatingProjection {
    private final JdbcTemplate jdbc;
    public ProductRatingProjection(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public void accept(ProductRatingChangedEvent event) {
        if(event==null||!event.valid())throw new IllegalArgumentException("评分事件格式不正确");
        jdbc.update("""
            INSERT INTO product_rating_stats(product_id,review_count,score_total,revision)
            SELECT product_id,?,?,? FROM products WHERE product_id=?
            ON DUPLICATE KEY UPDATE
                review_count=IF(VALUES(revision)>revision,VALUES(review_count),review_count),
                score_total=IF(VALUES(revision)>revision,VALUES(score_total),score_total),
                revision=GREATEST(revision,VALUES(revision))
            """,event.reviewCount(),event.scoreTotal(),event.revision(),event.productId());
    }
    public ProductRatingVO get(Long id) {
        var rows=jdbc.query("SELECT review_count,score_total FROM product_rating_stats WHERE product_id=?",
                (r,n)->ProductRatingVO.of(id,r.getLong(1),r.getLong(2)),id);
        return rows.isEmpty()?ProductRatingVO.of(id,0,0):rows.getFirst();
    }
}
