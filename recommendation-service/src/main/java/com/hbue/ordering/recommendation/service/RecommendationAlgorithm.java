package com.hbue.ordering.recommendation.service;

import com.hbue.ordering.recommendation.model.RecommendationFavoriteStateDO;
import com.hbue.ordering.recommendation.model.RecommendationHotProductDO;
import com.hbue.ordering.recommendation.model.RecommendationOrderItemDO;
import com.hbue.ordering.recommendation.model.RecommendationOrderSnapshotDO;
import com.hbue.ordering.recommendation.model.RecommendationProductFeatureDO;
import com.hbue.ordering.recommendation.model.RecommendationSearchIntentDO;
import java.time.LocalDateTime;
import java.util.List;

/** 推荐排序契约；将商品级评分规则与读取、投影及HTTP编排分开。 */
public interface RecommendationAlgorithm {
    /** 算法公式或信号语义变化时同步变更该版本，便于结果诊断。 */
    String ALGORITHM_VERSION = "rule-v1";

    /**
     * 使用调用方已限量的行为和当前候选快照生成分数与理由；本接口只计算，不读取数据库或访问其他服务。
     *
     * @param userId 当前已认证顾客的内部编号，仅用于稳定排序打散
     * @param restaurantId 可选餐厅范围；为null表示不筛餐厅
     * @param limit 首页需要的结果数量，范围为1至10
     * @param candidates 当前可用且已按范围过滤的候选商品
     * @param historicalProducts 只用于形成内容画像的历史商品特征，不允许作为返回候选
     * @param orders 本人近期已支付订单快照
     * @param lines 上述订单的逐明细购买、退款及规格选项快照
     * @param favorites 本人当前菜品/窗口收藏状态
     * @param searches 本人保留期内的主动搜索意图
     * @param hot 统计服务提供的有界累计销量榜
     * @param now 统一评分时点，避免同次请求内不同信号使用不同时间
     * @return 有序商品、内部理由和算法模式；不包含对外展示评分
     */
    RankedResult rank(Long userId, Long restaurantId, int limit,
            List<RecommendationProductFeatureDO> candidates,
            List<RecommendationProductFeatureDO> historicalProducts,
            List<RecommendationOrderSnapshotDO> orders,
            List<RecommendationOrderItemDO> lines,
            List<RecommendationFavoriteStateDO> favorites,
            List<RecommendationSearchIntentDO> searches,
            List<RecommendationHotProductDO> hot,
            LocalDateTime now);

    /** 算法内部排序结果，分数不向学生端开放。 */
    record RankedItem(RecommendationProductFeatureDO product, String reasonCode, double score, long tieKey) { }

    /** 算法模式和有序结果。 */
    record RankedResult(String mode, List<RankedItem> items) { }
}
