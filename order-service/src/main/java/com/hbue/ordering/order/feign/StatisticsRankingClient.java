package com.hbue.ordering.order.feign;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.vo.SalesRankingVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** 旧订单榜单 URL 的短期兼容代理；用户端已改为直达 statistics-service。 */
@FeignClient(name = "statistics-service", contextId = "orderLegacyStatisticsRankingClient")
public interface StatisticsRankingClient {
    /** 透传当前学生 JWT 到统计服务，统计服务仍会独立验签及校验 CUSTOMER 角色。 */
    @GetMapping("/statistics/rankings/sales")
    ApiResponse<SalesRankingVO> ranking(
            @RequestParam("limit") int limit,
            @RequestHeader("Authorization") String authorization
    );
}
