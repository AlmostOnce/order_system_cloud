package com.hbue.ordering.recommendation.service.impl;

import com.hbue.ordering.recommendation.client.RecommendationStatisticsClient;
import com.hbue.ordering.recommendation.mapper.RecommendationHotProductMapper;
import com.hbue.ordering.recommendation.model.RecommendationHotProductDO;
import com.hbue.ordering.recommendation.service.RecommendationHotSalesWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 原子替换统计服务销量榜本地投影，源端故障不会清空上次可用榜单。 */
@Service
public class RecommendationHotSalesWriterImpl implements RecommendationHotSalesWriter {
    private final RecommendationHotProductMapper hotProducts;

    /** 注入推荐库热门读模型 Mapper。 */
    public RecommendationHotSalesWriterImpl(RecommendationHotProductMapper hotProducts) {
        this.hotProducts = hotProducts;
    }

    /**
     * 校验并写入最多50项销量榜；所有行在同一事务替换，SQL失败时旧榜保持可读。
     * 空榜是统计服务的真实成功结果，允许清除过期热门候选。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void replace(List<RecommendationStatisticsClient.Item> items) {
        if (items == null || items.size() > 50) throw new IllegalArgumentException("统计服务热门榜格式无效");
        Set<Long> productIds = new HashSet<>();
        for (var item : items) {
            if (item == null || item.rank() <= 0 || item.productId() == null || item.productId() <= 0
                    || item.windowId() == null || item.windowId() <= 0 || item.sales() < 0
                    || !productIds.add(item.productId())) throw new IllegalArgumentException("统计服务热门项无效");
        }
        hotProducts.deleteAll();
        LocalDateTime now = LocalDateTime.now();
        for (var item : items) {
            RecommendationHotProductDO row = new RecommendationHotProductDO();
            row.setProductId(item.productId());
            row.setWindowId(item.windowId());
            row.setSales(item.sales());
            row.setRankNo(item.rank());
            row.setUpdatedAt(now);
            if (hotProducts.insertRankingItem(row) != 1) throw new IllegalStateException("热门榜投影写入失败");
        }
    }
}
