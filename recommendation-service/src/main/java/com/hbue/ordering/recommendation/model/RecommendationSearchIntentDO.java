package com.hbue.ordering.recommendation.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 一次成功主动搜索的短期推荐信号；不会保存分页重试或推荐内部召回。 */
@Data
@TableName("recommendation_search_intents")
public class RecommendationSearchIntentDO {
    /** 本服务生成的记录主键。 */
    @TableId("intent_id") private Long intentId;
    /** 由搜索服务验签上下文解析出的用户内部主键。 */
    private Long userId;
    /** 客户端为一次明确提交生成的幂等编号。 */
    private String queryId;
    /** 主动提交的规范化搜索关键词，只按短期策略使用。 */
    private String keyword;
    /** 搜索时的可选餐厅范围。 */
    private Long restaurantId;
    /** 搜索时的可选窗口范围。 */
    private Long windowId;
    /** 搜索时的可选分类范围。 */
    private Long categoryId;
    /** 成功首屏菜品ID的有序JSON快照，最多20项。 */
    private String rankedProductIds;
    /** 搜索成功时间，按14天保留和3天半衰期计算。 */
    private LocalDateTime createdAt;
}
