package com.hbue.ordering.coupon.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.client.vo.CouponExpiryNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 通知服务内部调用客户端。
 *
 * <p>只承载优惠券到期提醒：券服务负责判断"哪些券即将过期"，通知服务负责落库、
 * 偏好判断与在线提示。发送失败不回写券状态，只保留 {@code expiry_reminded_at} 为空，
 * 由下一轮扫描重试；通知表的唯一键保证重试不会产生重复提醒。</p>
 *
 * <p>该路径不在网关暴露，必须携带服务间共享密钥 {@code X-Internal-Token}。
 * 使用独立的 contextId，避免与其它指向通知服务的客户端共用 Feign 配置。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@FeignClient(name = "notification-service", contextId = "couponNotificationClient")
public interface CouponNotificationClient {

    /**
     * 写入并推送优惠券到期提醒站内信。
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件、券记录与正文
     * @return 通知服务处理结果
     */
    @PostMapping("/internal/notifications/coupon-expiring")
    ApiResponse<Void> sendCouponExpiring(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody CouponExpiryNotificationRequest request
    );
}
