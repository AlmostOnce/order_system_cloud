package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.dto.CouponExpiryNotificationRequest;
import com.hbue.ordering.notification.dto.NotificationDraft;
import com.hbue.ordering.notification.model.NotificationCategory;
import com.hbue.ordering.notification.service.SystemNotificationWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠券到期提醒站内信落库服务。
 *
 * <p>与订单状态、反馈工单并列但完全独立：使用独立的通知类型 {@code COUPON_EXPIRING}
 * 与独立的偏好分类 {@code COUPON}，不写 order_id，因此关闭"订单状态通知"或
 * "反馈与工单通知"不会影响券提醒，反之亦然。</p>
 *
 * <p>幂等由外部传入的事件标识保证：事件标识按用户券记录派生且终身不变，
 * 即便扫描任务重复执行、或某次提醒标记丢失，通知表唯一键也只允许一条记录。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Service
public class CouponExpiryNotificationService {

    /**
     * 优惠券到期提醒通知类型编码，长度不超过通知表 32 字符上限。
     */
    private static final String COUPON_EXPIRING_TYPE = "COUPON_EXPIRING";

    /**
     * 优惠券业务类型编码，写入 biz_type 供消息中心跳转到我的优惠券。
     */
    private static final String COUPON_BIZ_TYPE = "COUPON";

    /**
     * 标题兜底文案。
     */
    private static final String DEFAULT_TITLE = "优惠券即将过期";

    /**
     * 正文兜底文案；调用方通常已带上券名与具体到期时间。
     */
    private static final String DEFAULT_CONTENT = "您有一张优惠券将在 24 小时内过期，请尽快使用";

    /**
     * 站内信统一写入器。
     */
    private final SystemNotificationWriter writer;

    /**
     * 创建优惠券到期提醒服务。
     *
     * @param writer 站内信统一写入器
     */
    public CouponExpiryNotificationService(SystemNotificationWriter writer) {
        this.writer = writer;
    }

    /**
     * 幂等写入一张即将过期优惠券的站内信。
     *
     * <p>用户券编号必须为正数：它既用于生成业务跳转标识，也是幂等语义的一部分，
     * 缺失时无法定位提醒对象，直接拒绝而不是写一条无法跳转的空壳通知。</p>
     *
     * @param request 内部请求
     */
    @Transactional
    public void sendExpiring(CouponExpiryNotificationRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知请求不能为空");
        }
        if (request.userCouponId() == null || request.userCouponId() <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "用户优惠券编号无效");
        }
        writer.write(new NotificationDraft(request.recipientId(), request.eventId(), COUPON_EXPIRING_TYPE,
                request.title(), request.content(), DEFAULT_TITLE, DEFAULT_CONTENT,
                COUPON_BIZ_TYPE, request.userCouponId().toString(), NotificationCategory.COUPON));
    }
}
