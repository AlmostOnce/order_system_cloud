package com.hbue.ordering.recommendation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.recommendation.dto.SearchIntentRequest;
import com.hbue.ordering.recommendation.mapper.RecommendationSearchIntentMapper;
import com.hbue.ordering.recommendation.model.RecommendationSearchIntentDO;
import com.hbue.ordering.recommendation.service.RecommendationSearchIntentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;

/** 保存搜索服务转发的成功首屏意图并按约定删除过期原文。 */
@Service
public class RecommendationSearchIntentServiceImpl implements RecommendationSearchIntentService {
    private final RecommendationSearchIntentMapper intents;
    private final ObjectMapper json;

    /** 注入短期意图表及列表序列化器，服务端自行生成采集时间。 */
    public RecommendationSearchIntentServiceImpl(RecommendationSearchIntentMapper intents, ObjectMapper json) {
        this.intents = intents;
        this.json = json;
    }

    /**
     * 仅信任 search-service 内部凭据对应的服务调用方。相同用户、queryId 只保存第一次结果，
     * 使超时重试及分页流量不能被重复当成多次主动搜索。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void record(SearchIntentRequest request) {
        if (request == null || request.userId() == null || request.userId() <= 0
                || request.queryId() == null || !request.queryId().matches("[A-Za-z0-9_-]{16,64}")
                || request.keyword() == null || request.keyword().isBlank() || request.keyword().length() > 80
                || !validOptionalId(request.restaurantId()) || !validOptionalId(request.windowId())
                || !validOptionalId(request.categoryId()) || request.rankedProductIds() == null
                || request.rankedProductIds().size() > 20
                || request.rankedProductIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(request.rankedProductIds()).size() != request.rankedProductIds().size()) {
            throw new IllegalArgumentException("搜索推荐意图格式无效");
        }
        try {
            String normalizedKeyword = Normalizer.normalize(request.keyword().strip(), Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalizedKeyword.isBlank() || normalizedKeyword.length() > 80) {
                throw new IllegalArgumentException("规范化搜索词长度无效");
            }
            LocalDateTime now = LocalDateTime.now();
            if (intents.selectRecentDuplicate(request.userId(), normalizedKeyword, request.restaurantId(),
                    request.windowId(), request.categoryId(), now.minusMinutes(10)) != null) return;
            // 每日目标上限10条；shortcut: count与insert并非原子，双worker并发时可能略超，需严格上限时改用配额行。
            if (intents.countSince(request.userId(), now.toLocalDate().atStartOfDay()) >= 10) return;
            RecommendationSearchIntentDO row = new RecommendationSearchIntentDO();
            row.setUserId(request.userId());
            row.setQueryId(request.queryId());
            row.setKeyword(normalizedKeyword);
            row.setRestaurantId(request.restaurantId());
            row.setWindowId(request.windowId());
            row.setCategoryId(request.categoryId());
            row.setRankedProductIds(json.writeValueAsString(request.rankedProductIds()));
            row.setCreatedAt(now);
            // MySQL 唯一键重复时返回0是幂等成功，不能把已经收到的查询变成搜索故障。
            intents.insertIfAbsent(row);
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalStateException("搜索意图持久化失败", exception);
        }
    }

    /** 到期关键词和候选结果均在14天后删除；清理失败由后续定时周期再次执行。 */
    @Override
    public void purgeExpired() {
        intents.deleteExpired(LocalDateTime.now().minusDays(14));
    }

    /** 可选筛选 ID 只接受正数，不把0等哨兵值写进个人搜索范围。 */
    private boolean validOptionalId(Long id) {
        return id == null || id > 0;
    }
}
