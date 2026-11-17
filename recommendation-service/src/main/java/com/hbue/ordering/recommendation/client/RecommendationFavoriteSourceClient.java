package com.hbue.ordering.recommendation.client;

import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

/** 用户服务为推荐服务提供的受保护当前收藏快照游标契约。 */
@FeignClient(name = "user-service", contextId = "recommendationFavoriteSourceClient")
public interface RecommendationFavoriteSourceClient {
    /**
     * 读取首屏固定上界内的一页收藏；watermark 只限制收藏 ID 范围，不是事件过滤水位。
     *
     * @param token 项目内部共享令牌
     * @param afterFavoriteId 上一页末尾收藏主键
     * @param watermark 首次为空，后续沿用响应中的收藏扫描上界
     * @param limit 每页最大收藏数，调用方固定为100
     * @return 收藏状态与每条关系各自的最新可见事件版本
     */
    @GetMapping("/internal/users/recommendations/favorites")
    ApiResponse<RecommendationFavoriteSnapshotPage> page(
            @RequestHeader("X-Internal-Token") String token,
            @RequestParam("afterFavoriteId") long afterFavoriteId,
            @RequestParam(value = "watermark", required = false) Long watermark,
            @RequestParam("limit") int limit);

    /** 与用户服务内部 DTO 同字段，以便 Feign 按现有 ApiResponse 反序列化。 */
    record RecommendationFavoriteSnapshotPage(long watermark, Long observedOutboxWatermark,
            List<Item> items, Long nextCursor, boolean hasMore) {
        /** 当前仍存在的一条关系和其独立数值版本。 */
        public record Item(Long favoriteId, Long userId, String targetType, Long targetId,
                boolean active, LocalDateTime createdAt, Long sourceVersion) { }
    }
}
