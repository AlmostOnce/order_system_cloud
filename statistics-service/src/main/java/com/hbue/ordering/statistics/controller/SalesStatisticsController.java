package com.hbue.ordering.statistics.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.statistics.service.SalesStatisticsService;
import com.hbue.ordering.statistics.vo.SalesRankingVO;
import com.hbue.ordering.statistics.vo.WindowMonthlySalesVO;
import com.hbue.ordering.statistics.vo.WindowProductMonthlySalesVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端销量查询接口。
 *
 * <p>所有入口只允许 CUSTOMER；接口返回商品和窗口的聚合销量，不暴露订单、用户或支付明细。</p>
 */
@RestController
@RequestMapping("/statistics")
public class SalesStatisticsController {
    private final SalesStatisticsService statistics;

    /** Controller 只负责 HTTP 参数和响应封装，统计口径与缓存行为归 Service。 */
    public SalesStatisticsController(SalesStatisticsService statistics) {
        this.statistics = statistics;
    }

    /** 查询累计已支付菜品排行榜；服务端将榜单长度限制为1至100。 */
    @GetMapping("/rankings/sales")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<SalesRankingVO> ranking(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ApiResponse.success(statistics.listRanking(limit));
    }

    /** 首页使用一个有界目录请求读取当前自然月所有窗口销量，避免 N+1 请求。 */
    @GetMapping("/windows/monthly-sales")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<WindowMonthlySalesVO> monthlyWindows() {
        return ApiResponse.success(statistics.listWindowMonthlySales());
    }

    /** 点餐页一次读取单窗口总销量和其菜品月销量，窗口范围按正数主键校验。 */
    @GetMapping("/windows/{windowId}/monthly-sales")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<WindowProductMonthlySalesVO> monthlyWindowProducts(@PathVariable Long windowId) {
        return ApiResponse.success(statistics.getWindowMonthlySales(windowId));
    }
}
