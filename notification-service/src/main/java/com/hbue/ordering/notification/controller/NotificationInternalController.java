package com.hbue.ordering.notification.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.notification.dto.CouponExpiryNotificationRequest;
import com.hbue.ordering.notification.dto.ReviewDiscussionNotificationRequest;
import com.hbue.ordering.notification.dto.SupportReplyNotificationRequest;
import com.hbue.ordering.notification.dto.SupportStatusNotificationRequest;
import com.hbue.ordering.notification.service.impl.CouponExpiryNotificationService;
import com.hbue.ordering.notification.service.impl.ReviewDiscussionNotificationService;
import com.hbue.ordering.notification.service.impl.SupportNotificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知服务内部接口。
 *
 * <p>仅允许服务之间通过共享密钥调用，网关不配置 {@code /internal/**} 路由。
 * 安全过滤链对该前缀匿名放行，由本控制器逐个校验 {@code X-Internal-Token}，
 * 校验失败统一返回 401。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@RestController
@RequestMapping("/internal/notifications")
public class NotificationInternalController {

    /**
     * 支持回复通知业务服务。
     */
    private final SupportNotificationService supportNotificationService;

    /**
     * 服务间共享密钥校验器。
     */
    private final InternalScopeTokenVerifier internalScopeTokenVerifier;

    /**
     * 优惠券到期提醒业务服务。
     */
    private final CouponExpiryNotificationService couponExpiryNotificationService;

    /**
     * 评价评论提醒业务服务。
     */
    private final ReviewDiscussionNotificationService reviewDiscussionNotificationService;

    /**
     * 创建通知内部接口控制器。
     *
     * @param supportNotificationService           支持回复通知业务服务
     * @param internalScopeTokenVerifier           服务间共享密钥校验器
     * @param couponExpiryNotificationService      优惠券到期提醒业务服务
     * @param reviewDiscussionNotificationService  评价评论提醒业务服务
     */
    public NotificationInternalController(SupportNotificationService supportNotificationService,
                                          InternalScopeTokenVerifier internalScopeTokenVerifier,
                                          CouponExpiryNotificationService couponExpiryNotificationService,
                                          ReviewDiscussionNotificationService reviewDiscussionNotificationService) {
        this.supportNotificationService = supportNotificationService;
        this.internalScopeTokenVerifier = internalScopeTokenVerifier;
        this.couponExpiryNotificationService = couponExpiryNotificationService;
        this.reviewDiscussionNotificationService = reviewDiscussionNotificationService;
    }

    /**
     * 写入并推送支持回复站内信。
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件与正文
     * @return 处理成功响应
     */
    @PostMapping("/support-reply")
    public ApiResponse<Void> supportReply(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody SupportReplyNotificationRequest request) {
        internalScopeTokenVerifier.verify(internalToken);
        supportNotificationService.sendSupportReply(request);
        return ApiResponse.success(null);
    }

    /**
     * 写入并推送工单状态变更站内信。
     *
     * <p>工单受理或关闭事务提交后由反馈服务调用，与回复通知共用内部鉴权和幂等落库，
     * 区别只在通知类型与业务文案，学生端消息中心无需区分接口来源。</p>
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件与正文
     * @return 处理成功响应
     */
    @PostMapping("/support-status")
    public ApiResponse<Void> supportStatus(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody SupportStatusNotificationRequest request) {
        internalScopeTokenVerifier.verify(internalToken);
        supportNotificationService.sendSupportStatus(request);
        return ApiResponse.success(null);
    }

    /**
     * 写入并推送优惠券到期提醒站内信。
     *
     * <p>由优惠券服务的扫描任务在券进入"到期前 24 小时"窗口时调用。该通知使用独立的
     * 类型与偏好分类，不写 order_id，与订单状态、工单通知互不影响。</p>
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件、券记录与正文
     * @return 处理成功响应
     */
    @PostMapping("/coupon-expiring")
    public ApiResponse<Void> couponExpiring(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody CouponExpiryNotificationRequest request) {
        internalScopeTokenVerifier.verify(internalToken);
        couponExpiryNotificationService.sendExpiring(request);
        return ApiResponse.success(null);
    }

    /**
     * 写入并推送评价评论站内信。
     *
     * <p>由评论服务在评论或回复提交后调用。事件标识携带评论版本，
     * 同一版本重试不会重复通知；该通知使用独立的类型与偏好分类，
     * 不写 order_id，与订单、工单、优惠券提醒互不影响。</p>
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件、评价标识与正文
     * @return 处理成功响应
     */
    @PostMapping("/review-discussion")
    public ApiResponse<Void> reviewDiscussion(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody ReviewDiscussionNotificationRequest request) {
        internalScopeTokenVerifier.verify(internalToken);
        reviewDiscussionNotificationService.send(request);
        return ApiResponse.success(null);
    }
}
