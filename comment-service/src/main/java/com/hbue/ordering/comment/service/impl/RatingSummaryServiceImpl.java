package com.hbue.ordering.comment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.service.RatingSummaryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 评分读模型短缓存，包含零评论结果；Redis故障回源，不返回伪造的零评分。
 */
@Service
public class RatingSummaryServiceImpl implements RatingSummaryService {
    /**
     * 评分缓存键前缀；统一在此定义，读写与失效共用，避免键格式漂移导致删不到。
     */
    private static final String KEY_PREFIX = "comment:rating:v1:";
    private final CommentMapper rows;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RatingSummaryServiceImpl(CommentMapper rows, StringRedisTemplate redis, ObjectMapper json) {
        this.rows = rows;
        this.redis = redis;
        this.json = json;
    }

    public ProductRatingVO get(Long productId) {
        String key = KEY_PREFIX + productId;
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                ProductRatingVO cached = json.readValue(value, ProductRatingVO.class);
                if (productId.equals(cached.productId()) && cached.reviewCount() >= 0) return cached;
            }
        } catch (Exception ignored) { /* 缓存不影响事实源查询。 */ }
        ProductRatingVO result = rows.summary(productId);
        try {
            redis.opsForValue().set(key, json.writeValueAsString(result), Duration.ofSeconds(ThreadLocalRandom.current().nextInt(30, 61)));
        } catch (Exception ignored) { /* 数据库结果仍然有效。 */ }
        return result;
    }

    /**
     * 删除评分缓存；Redis 故障只记录在吞掉的异常里，不把已提交的评价写入变成失败。
     *
     * <p>删除失败最坏结果是旧评分保留到 TTL 到期，属于可接受的短暂不一致；若向上抛出，
     * 会污染事务提交后的调用链，反而让用户看到写入失败。</p>
     */
    @Override
    public void evict(Long productId) {
        if (productId == null) return;
        try {
            redis.delete(KEY_PREFIX + productId);
        } catch (Exception ignored) { /* 缓存删除失败由 TTL 兜底。 */ }
    }
}
