package com.hbue.ordering.recommendation.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一个权威来源的断点游标；只在整页投影成功后推进，重复页由事实投影自身幂等吸收。
 */
@Data
@TableName("recommendation_source_cursors")
public class RecommendationSourceCursorDO {
    /**
     * 固定来源名，例如订单购买或收藏快照；不是用户可控值。
     */
    @TableId
    private String sourceName;
    /**
     * 最后完整处理的源主键；重复消费一页不会导致倒退。
     */
    private Long cursorValue;
    /**
     * 收藏扫描首屏捕获的最大 favoriteId；订单来源不使用此字段。
     */
    private Long upperBound;
    /**
     * 所有启动时历史事实已读完；之后继续信任实时 Outbox/RocketMQ 补变化。
     */
    private Boolean completed;
    /**
     * 最近一次断点推进时间。
     */
    private LocalDateTime updatedAt;
}
