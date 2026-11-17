package com.hbue.ordering.recommendation.controller;

import com.hbue.ordering.recommendation.service.RecommendationQueryService;
import com.hbue.ordering.recommendation.vo.RecommendationHomeVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CUSTOMER 首页推荐接口；用户编号只能从已验签 JWT 经身份服务解析，不接受客户端 userId。 */
@Validated
@RestController
@RequestMapping("/recommendations")
public class RecommendationController {
    private final RecommendationQueryService recommendations;

    /** 注入推荐查询；当前 JWT 主体已经由资源服务器解码器转换为内部用户主键。 */
    public RecommendationController(RecommendationQueryService recommendations) {
        this.recommendations = recommendations;
    }

    /** 当前学生首页推荐；餐厅筛选可选，返回数量限制为1至10，默认10。 */
    @GetMapping("/home")
    @PreAuthorize("hasRole('CUSTOMER')")
    public com.hbue.ordering.common.response.ApiResponse<RecommendationHomeVO> home(
            @AuthenticationPrincipal Jwt verifiedJwt,
            @RequestParam(value = "restaurantId", required = false) @Min(1) Long restaurantId,
            @RequestParam(value = "limit", defaultValue = "10") @Min(1) @Max(10) int limit) {
        Long userId = Long.valueOf(verifiedJwt.getSubject());
        return com.hbue.ordering.common.response.ApiResponse.success(
                recommendations.recommend(userId, restaurantId, limit));
    }
}
