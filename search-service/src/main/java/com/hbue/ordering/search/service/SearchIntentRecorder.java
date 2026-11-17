package com.hbue.ordering.search.service;

import com.hbue.ordering.search.vo.ProductSearchPageVO;
import org.springframework.security.oauth2.jwt.Jwt;

/** 搜索成功后采集推荐弱信号的异步边界，不参与真实搜索结果查询。 */
public interface SearchIntentRecorder {
    /**
     * 仅入队成功首屏和显式queryId；调用期间不得同步访问用户或推荐服务。
     *
     * @param verified 搜索资源服务器已验签、校验过期与黑名单的JWT
     * @param queryId 本次主动查询的幂等编号；分页不能生成新编号触发采集
     * @param result 搜索服务已经成功返回的当前页，空结果允许记录
     * @param restaurantId 搜索请求已校验的可选餐厅范围
     * @param windowId 搜索请求已校验的可选窗口范围
     * @param categoryId 搜索请求已校验的可选分类范围
     */
    void recordSuccessfulFirstPage(Jwt verified, String queryId, ProductSearchPageVO result,
            Long restaurantId, Long windowId, Long categoryId);
}
