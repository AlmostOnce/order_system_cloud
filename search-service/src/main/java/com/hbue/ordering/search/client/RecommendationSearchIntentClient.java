package com.hbue.ordering.search.client;

import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 搜索服务向推荐服务采集短期搜索意图的内部客户端。
 * 仅由有界后台任务调用，不透传终端JWT；专用超时及禁用请求日志配置避免拖慢搜索或泄露关键词。
 */
@FeignClient(name = "recommendation-service", contextId = "recommendationSearchIntentClient")
public interface RecommendationSearchIntentClient {

    /**
     * 提交一次成功首屏搜索，接收方按queryId幂等；失败由后台任务丢弃，不同步重试用户搜索。
     *
     * @param internalToken 内部共享密钥，不能从前端获取
     * @param intent 服务端解析身份及搜索结果后构造的意图，不接受用户指定userId
     * @return 推荐服务统一受理响应，非成功业务码同样视为采集失败
     */
    @PostMapping("/internal/recommendations/search-intents")
    ApiResponse<Void> record(@RequestHeader("X-Internal-Token") String internalToken,
                             @RequestBody SearchIntent intent);

    /**
     * 只保留推荐需要的最小搜索快照；搜索词不写日志，结果编号保留原搜索的前20名顺序。
     *
     * @param userId 由验签JWT与JwtUserIdentity解析出的内部身份
     * @param queryId 前端主动提交搜索的唯一标识，重试同一次搜索复用
     * @param keyword 实际成功执行的规范化搜索词
     * @param restaurantId 本次搜索的餐厅约束，未指定时为空
     * @param windowId 本次搜索的档口约束，未指定时为空
     * @param categoryId 本次搜索的分类约束，未指定时为空
     * @param rankedProductIds 首屏最多20个结果编号；零结果搜索使用空数组
     */
    record SearchIntent(Long userId, String queryId, String keyword, Long restaurantId, Long windowId,
                        Long categoryId, List<Long> rankedProductIds) {}
}
