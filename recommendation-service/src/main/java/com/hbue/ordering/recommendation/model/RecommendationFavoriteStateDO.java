package com.hbue.ordering.recommendation.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏当前状态投影；取消墓碑保留源数值版本以防迟到收藏消息复活关系。
 */
@Data
@TableName("recommendation_favorite_states")
public class RecommendationFavoriteStateDO {
    /**
     * 当前用户内部主键。
     */
    private Long userId;
    /**
     * 收藏目标类型，只允许PRODUCT或WINDOW。
     */
    private String targetType;
    /**
     * 收藏目标菜品或窗口主键。
     */
    private Long targetId;
    /**
     * user-service Outbox 自增主键；使用数值比较判断事件新旧。
     */
    private Long sourceVersion;
    /**
     * false 表示保留的取消墓碑，不参与当前兴趣分。
     */
    private Boolean active;
    /**
     * 源关系最后一次真实变化时间。
     */
    private LocalDateTime changedAt;
}
