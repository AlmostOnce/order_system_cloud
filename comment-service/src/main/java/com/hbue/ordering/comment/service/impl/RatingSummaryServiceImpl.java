package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.service.RatingSummaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.comment.mapper.CommentMapper;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 评分读模型短缓存，包含零评论结果；Redis故障回源，不返回伪造的零评分。 */
@Service
public class RatingSummaryServiceImpl implements RatingSummaryService {
    private final CommentMapper rows;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public RatingSummaryServiceImpl(CommentMapper rows,StringRedisTemplate redis,ObjectMapper json) {
        this.rows=rows;this.redis=redis;this.json=json;
    }
    public ProductRatingVO get(Long productId) {
        String key="comment:rating:v1:"+productId;
        try {
            String value=redis.opsForValue().get(key);
            if(value!=null) {
                ProductRatingVO cached=json.readValue(value,ProductRatingVO.class);
                if(productId.equals(cached.productId())&&cached.reviewCount()>=0)return cached;
            }
        } catch(Exception ignored) { /* 缓存不影响事实源查询。 */ }
        ProductRatingVO result=rows.summary(productId);
        try {redis.opsForValue().set(key,json.writeValueAsString(result),Duration.ofSeconds(ThreadLocalRandom.current().nextInt(30,61)));}
        catch(Exception ignored) { /* 数据库结果仍然有效。 */ }
        return result;
    }
}
