package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.service.SalesRankingService;
import com.hbue.ordering.order.vo.SalesRankingVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/rankings")
@Tag(name = "排行榜")
public class SalesRankingController {
    private final SalesRankingService service;
    public SalesRankingController(SalesRankingService service) { this.service = service; }

    /** 登录用户查询全站菜品销量，不返回任何订单和用户信息。 */
    @GetMapping("/sales")
    public ApiResponse<SalesRankingVO> sales(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ApiResponse.success(service.list(limit));
    }
}
