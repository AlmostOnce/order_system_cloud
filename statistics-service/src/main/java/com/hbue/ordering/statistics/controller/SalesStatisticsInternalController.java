package com.hbue.ordering.statistics.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.statistics.service.SalesStatisticsService;
import com.hbue.ordering.statistics.vo.SalesRankingVO;
import org.springframework.web.bind.annotation.*;

/**
 * 仅供内网推荐服务读取既有销量榜；此路由没有网关映射且每次请求检查内部令牌。
 */
@RestController
@RequestMapping("/internal/statistics")
public class SalesStatisticsInternalController {
    private final SalesStatisticsService statistics;
    private final InternalScopeTokenVerifier verifier;

    /**
     * 注入已存在的榜单查询和项目共用的常量时间令牌校验器。
     */
    public SalesStatisticsInternalController(SalesStatisticsService statistics,
                                             InternalScopeTokenVerifier verifier) {
        this.statistics = statistics;
        this.verifier = verifier;
    }

    /**
     * 只返回聚合商品销量，不提供订单、用户或支付明细。
     */
    @GetMapping("/rankings/sales")
    public ApiResponse<SalesRankingVO> ranking(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(defaultValue = "50") int limit) {
        verifier.verify(token);
        return ApiResponse.success(statistics.listRanking(limit));
    }
}
