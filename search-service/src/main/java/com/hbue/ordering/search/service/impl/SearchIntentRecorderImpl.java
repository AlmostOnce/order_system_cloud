package com.hbue.ordering.search.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.JwtUserIdentity;
import com.hbue.ordering.search.client.RecommendationSearchIntentClient;
import com.hbue.ordering.search.client.RecommendationSearchIntentClient.SearchIntent;
import com.hbue.ordering.search.service.SearchIntentRecorder;
import com.hbue.ordering.search.vo.ProductSearchPageVO;
import com.hbue.ordering.search.vo.ProductSearchResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

/**
 * 成功搜索之后采集弱意图；请求线程只复制有界结果，不做用户服务或推荐服务网络调用。
 * 记录失败不改变搜索结果，不把关键词、令牌、用户编号或远程异常正文写入日志。
 */
@Service
public class SearchIntentRecorderImpl implements SearchIntentRecorder {
    /** 只接受显式主动搜索标识，避免旧客户端、分页或空标识被误记为新意图。 */
    private static final Pattern QUERY_ID = Pattern.compile("[A-Za-z0-9_-]{16,64}");
    /** 仅记录失败类别，不包含请求/响应详情或搜索原文。 */
    private static final Logger log = LoggerFactory.getLogger(SearchIntentRecorderImpl.class);
    /** 已验签UUID到当前有效内部身份的既有解析器，调用发生在后台工作线程。 */
    private final JwtUserIdentity identities;
    /** 使用内部凭据的推荐采集客户端，不读取Servlet上下文。 */
    private final RecommendationSearchIntentClient recommendations;
    /** 任务数量有界且拒绝后不在请求线程运行的专用执行器。 */
    private final ThreadPoolTaskExecutor executor;
    /** 后端共享凭据只用于内部请求头，不进入日志和终端返回值。 */
    private final String internalToken;

    /** 注入已有身份解析和内部客户端，线程池及远程超时由搜索服务独立限定。 */
    public SearchIntentRecorderImpl(JwtUserIdentity identities, RecommendationSearchIntentClient recommendations,
            @Qualifier("searchIntentExecutor") ThreadPoolTaskExecutor executor,
            @Value("${internal.scope.token:}") String internalToken) {
        this.identities = identities;
        this.recommendations = recommendations;
        this.executor = executor;
        this.internalToken = internalToken;
    }

    /**
     * 仅将成功首屏且显式带queryId的搜索入队；分页、旧客户端以及无效输入不采集。
     * 队列只保存UUID、凭据版本和有界搜索快照，不保存JWT原始令牌，也不依赖请求线程的身份上下文。
     *
     * @param verified 已由资源服务器验签、issuer/过期和注销黑名单校验的JWT
     * @param queryId 本次主动搜索标识，重试沿用该标识；没有则不采集
     * @param result 搜索成功返回的页，空结果允许采集，异常搜索不得调用本方法
     * @param restaurantId 已校验的搜索餐厅范围
     * @param windowId 已校验的搜索档口范围
     * @param categoryId 已校验的搜索分类范围
     */
    @Override
    public void recordSuccessfulFirstPage(Jwt verified, String queryId, ProductSearchPageVO result,
            Long restaurantId, Long windowId, Long categoryId) {
        if (queryId == null || result == null || result.page() != 1) return;
        try {
            if (!QUERY_ID.matcher(queryId).matches() || verified == null || result.items() == null) {
                log.warn("推荐搜索意图输入无效，已丢弃");
                return;
            }
            String uuid = verified.getSubject();
            if (uuid == null || !JwtUserIdentity.PUBLIC_UUID.matcher(uuid).matches()) {
                log.warn("推荐搜索意图身份格式无效，已丢弃");
                return;
            }
            long credentialVersion = JwtUserIdentity.credentialVersion(verified);
            String keyword = result.keyword();
            if (keyword == null || keyword.isBlank() || keyword.length() > 80) {
                log.warn("推荐搜索意图关键词格式无效，已丢弃");
                return;
            }
            List<Long> ranked = new ArrayList<>();
            for (ProductSearchResultVO item : result.items().subList(0, Math.min(20, result.items().size()))) {
                if (item == null || item.productId() == null || item.productId() <= 0) {
                    log.warn("推荐搜索意图结果编号无效，已丢弃");
                    return;
                }
                ranked.add(item.productId());
            }
            // 网络和身份解析只在工作线程中执行，队列满不能通过CallerRuns反向阻塞搜索。
            List<Long> snapshot = List.copyOf(ranked);
            executor.execute(() -> send(uuid, credentialVersion, queryId, keyword,
                    restaurantId, windowId, categoryId, snapshot));
        } catch (RejectedExecutionException exception) {
            log.warn("推荐搜索意图队列已满或停止，已丢弃");
        } catch (RuntimeException exception) {
            // 不记录exception正文或堆栈，HTTP客户端异常可能包含带关键词的请求内容。
            log.warn("推荐搜索意图入队失败，已丢弃：{}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 从原始已验签UUID及凭据版本确认当前内部用户后再发送；任一远程失败均为弱信号丢失。
     * 队列任务不重试、不向终端报错；推荐服务成功入库前不宣称已经采集成功。
     */
    private void send(String uuid, long credentialVersion, String queryId, String keyword,
            Long restaurantId, Long windowId, Long categoryId, List<Long> rankedProductIds) {
        try {
            Long userId = identities.resolveCurrentIdentity(uuid, credentialVersion);
            if (userId == null || userId <= 0 || internalToken == null || internalToken.isBlank()) {
                log.warn("推荐搜索意图身份或内部凭据不可用，已丢弃");
                return;
            }
            ApiResponse<Void> response = recommendations.record(internalToken, new SearchIntent(userId, queryId,
                    keyword, restaurantId, windowId, categoryId, rankedProductIds));
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
                log.warn("推荐搜索意图未获成功受理，已丢弃");
            }
        } catch (RuntimeException exception) {
            log.warn("推荐搜索意图发送失败，已丢弃：{}", exception.getClass().getSimpleName());
        }
    }
}
