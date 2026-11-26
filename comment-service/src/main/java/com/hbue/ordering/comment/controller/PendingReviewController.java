package com.hbue.ordering.comment.controller;

import com.hbue.ordering.comment.service.PendingReviewService;
import com.hbue.ordering.comment.vo.PendingReviewCountVO;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端待评价数量接口。
 *
 * <p>只允许 CUSTOMER 访问：商家与管理员没有“我的订单”语义，也不需要学生端角标。
 * 用户身份从 JWT 解析后传给统计服务，接口不接受客户端指定他人编号。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@RestController
@RequestMapping("/comments/me/pending-reviews")
public class PendingReviewController {

    /**
     * 待评价统计服务。
     */
    private final PendingReviewService pendingReviews;

    /**
     * 创建待评价数量接口。
     *
     * @param pendingReviews 待评价统计服务
     */
    public PendingReviewController(PendingReviewService pendingReviews) {
        this.pendingReviews = pendingReviews;
    }

    /**
     * 统计当前学生的待评价订单数。
     *
     * <p>订单服务不可用或返回异常时统计服务抛 503，前端隐藏角标而不是显示 0；
     * 没有待评价订单时正常返回 0，由前端决定不展示角标。</p>
     *
     * @param jwt 当前登录令牌
     * @return 待评价订单数
     */
    @GetMapping("/count")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PendingReviewCountVO> count(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(new PendingReviewCountVO(
                pendingReviews.count(Long.valueOf(jwt.getSubject()))));
    }
}
