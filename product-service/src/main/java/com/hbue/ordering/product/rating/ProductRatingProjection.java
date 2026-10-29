package com.hbue.ordering.product.rating;
import com.hbue.ordering.comment.api.ProductRatingChangedEvent;
import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.product.mapper.ProductRatingMapper;
import org.springframework.stereotype.Service;

/** 商品库中的评分读模型；按源版本覆盖快照，不对消息增量累加。 */
@Service
public class ProductRatingProjection {
    private final ProductRatingMapper mapper;
    public ProductRatingProjection(ProductRatingMapper mapper) {this.mapper=mapper;}
    public void accept(ProductRatingChangedEvent event) {
        if(event==null||!event.valid())throw new IllegalArgumentException("评分事件格式不正确");
        mapper.upsert(event.productId(), event.reviewCount(), event.scoreTotal(), event.revision());
    }
    public ProductRatingVO get(Long id) {
        var row=mapper.get(id);
        return row==null?ProductRatingVO.of(id,0,0):ProductRatingVO.of(id,row.reviewCount(),row.scoreTotal());
    }
}
