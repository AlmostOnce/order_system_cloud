package com.hbue.ordering.recommendation.service;

import com.hbue.ordering.recommendation.dto.SearchIntentRequest;

/**
 * 处理由搜索服务异步转发的成功首屏搜索弱信号。
 */
public interface RecommendationSearchIntentService {
    /**
     * 规范化并有界存储一条本人搜索意图；重复或超过采集上限的意图不影响搜索响应。
     *
     * @param request 由搜索服务内部凭据保护，userId已经过服务端身份解析且不由学生客户端指定
     */
    void record(SearchIntentRequest request);

    /**
     * 删除已超过约定14天保留期的搜索原文及结果快照。
     */
    void purgeExpired();
}
