package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.feign.StatisticsRankingClient;
import com.hbue.ordering.order.vo.SalesRankingVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/rankings")
@Tag(name = "排行榜")
public class SalesRankingController {
    private final StatisticsRankingClient statistics;
    public SalesRankingController(StatisticsRankingClient statistics) { this.statistics = statistics; }

    /** 登录用户查询全站菜品销量，不返回任何订单和用户信息。 */
    @GetMapping("/sales")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<SalesRankingVO> sales(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestHeader("Authorization") String authorization
    ) {
        // 兼容旧客户端路径但不在订单服务重复查询订单库、缓存或计算排名。
        // 让统计服务再次校验原始 CUSTOMER JWT，不能用订单服务身份替代终端用户权限。
        return statistics.ranking(limit, authorization);
    }
}
