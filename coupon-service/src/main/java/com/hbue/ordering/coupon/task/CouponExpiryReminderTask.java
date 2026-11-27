package com.hbue.ordering.coupon.task;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.redis.ScheduledTaskLock;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.client.CouponNotificationClient;
import com.hbue.ordering.coupon.client.vo.CouponExpiryNotificationRequest;
import com.hbue.ordering.coupon.dto.CouponExpiryReminderRow;
import com.hbue.ordering.coupon.mapper.UserCouponMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 优惠券到期前 24 小时的提醒任务。
 *
 * <p>扫描条件由 SQL 决定：可用状态、未提醒、尚未过期且进入 24 小时窗口。任务本身只负责
 * "逐条发送 + 发送成功后打标记"，不在内存里计算时间窗，避免与数据库时钟产生偏差。</p>
 *
 * <p>幂等分三层：</p>
 * <ul>
 *   <li>分布式租约：多实例部署时同一轮只有一个实例扫描发送，避免重复调用通知服务；</li>
 *   <li>本地标记 {@code expiry_reminded_at}：发送成功后条件更新，多实例并发只有一次能写成功；</li>
 *   <li>通知表唯一键 {@code (recipient_id, event_id)}：事件标识按券记录派生且不变，
 *       即使标记丢失导致重复发送，也只会落一条提醒。</li>
 * </ul>
 *
 * <p>发送顺序刻意是"先发通知、后打标记"：先打标记一旦通知失败就永远补不回来；
 * 先发通知最多在标记失败时重发一次，而重发会被唯一键吸收。</p>
 *
 * <p>单条失败只记日志并继续处理剩余券，不中断整批；批次扫描本身失败也只记录，
 * 由下一轮调度重试，绝不因为一次异常而停止后续提醒。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Component
public class CouponExpiryReminderTask {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(CouponExpiryReminderTask.class);

    /**
     * 稳定的任务名：所有实例必须一致，否则分布式互斥失效。
     */
    private static final String TASK_NAME = "coupon-expiry-reminder";

    /**
     * 租约时长：一批最多 200 条通知发送，10 分钟足以覆盖，过期后由下一轮实例接管。
     */
    private static final Duration LEASE = Duration.ofMinutes(10);

    /**
     * 单次扫描上限，控制一次调度的数据库读取量与远程调用数量。
     */
    private static final int BATCH_SIZE = 200;

    /**
     * 通知标题；正文会带上券名与具体到期时间，标题保持简短以适配消息列表。
     */
    private static final String NOTIFICATION_TITLE = "优惠券即将过期";

    /**
     * 到期时间展示格式：月-日 时:分，学生只需知道大概还剩多久。
     */
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * 用户券数据访问，用于扫描与标记。
     */
    private final UserCouponMapper userCouponMapper;

    /**
     * 通知服务内部客户端。
     */
    private final CouponNotificationClient notificationClient;

    /**
     * 服务间共享内部令牌。
     */
    private final String internalToken;

    /**
     * 定时任务互斥租约，避免多实例重复扫描并发送同一批提醒。
     */
    private final ScheduledTaskLock taskLock;

    /**
     * 创建到期提醒任务。
     *
     * @param userCouponMapper   用户券数据访问
     * @param notificationClient 通知服务内部客户端
     * @param internalToken      服务间共享内部令牌
     * @param taskLock           定时任务互斥租约
     */
    public CouponExpiryReminderTask(UserCouponMapper userCouponMapper,
                                    CouponNotificationClient notificationClient,
                                    @Value("${internal.scope.token:}") String internalToken,
                                    ScheduledTaskLock taskLock) {
        this.userCouponMapper = userCouponMapper;
        this.notificationClient = notificationClient;
        this.internalToken = internalToken;
        this.taskLock = taskLock;
    }

    /**
     * 扫描并发送到期提醒。
     *
     * <p>首次延迟 1 分钟、之后每 5 分钟执行一次，保证券进入 24 小时窗口后最多 5 分钟内
     * 收到提醒。两者都可通过配置覆盖，便于在低峰期调整扫描频率。</p>
     *
     * <p>扫描前先抢分布式租约：多实例部署时同一轮只有一个实例执行，其余实例直接跳过；
     * Redis 不可用时同样跳过本轮，宁可晚 5 分钟提醒，也不在多实例上重复发起通知。</p>
     */
    @Scheduled(initialDelayString = "${coupon.expiry-reminder.initial-delay-ms:60000}",
            fixedDelayString = "${coupon.expiry-reminder.delay-ms:300000}")
    public void remindExpiring() {
        if (internalToken == null || internalToken.isBlank()) {
            // 没有内部凭据时所有调用都会被通知服务拒绝，直接跳过避免刷屏日志。
            log.error("优惠券到期提醒跳过：internal.scope.token 未配置");
            return;
        }
        try (ScheduledTaskLock.Lease lease = taskLock.tryAcquire(TASK_NAME, LEASE)) {
            if (lease == null) {
                log.info("优惠券到期提醒已由其它实例执行，跳过本轮");
                return;
            }
            List<CouponExpiryReminderRow> candidates;
            try {
                candidates = userCouponMapper.findExpiryReminderCandidates(BATCH_SIZE);
            } catch (RuntimeException exception) {
                log.error("优惠券到期提醒扫描失败，等待下一轮重试", exception);
                return;
            }
            if (candidates.isEmpty()) {
                return;
            }
            int sent = 0;
            for (CouponExpiryReminderRow candidate : candidates) {
                try {
                    sendOne(candidate);
                    sent++;
                } catch (RuntimeException exception) {
                    // 单条失败不阻塞整批；该券仍为"未提醒"，下一轮会再次尝试。
                    log.warn("优惠券到期提醒发送失败，将在下一轮重试，userCouponId={}",
                            candidate.getUserCouponId(), exception);
                }
            }
            log.info("优惠券到期提醒本轮完成，待提醒={}，成功={}", candidates.size(), sent);
        }
    }

    /**
     * 发送单张券的提醒，并在通知服务确认后标记已提醒。
     *
     * @param candidate 待提醒的券
     */
    private void sendOne(CouponExpiryReminderRow candidate) {
        CouponExpiryNotificationRequest request = new CouponExpiryNotificationRequest(
                candidate.getUserId(),
                eventId(candidate.getUserCouponId()),
                NOTIFICATION_TITLE,
                content(candidate),
                candidate.getUserCouponId()
        );
        ApiResponse<Void> response = notificationClient.sendCouponExpiring(internalToken, request);
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            throw new IllegalStateException("通知服务未确认优惠券到期提醒");
        }
        if (userCouponMapper.markExpiryReminded(candidate.getUserCouponId()) != 1) {
            // 并发实例已抢先标记：通知已按唯一键幂等写入，这里只需记录，不算失败。
            log.info("优惠券到期提醒已被其它实例标记，userCouponId={}", candidate.getUserCouponId());
        }
    }

    /**
     * 生成终身稳定的幂等事件标识。
     *
     * @param userCouponId 用户券记录编号
     * @return 事件标识，长度远小于通知表 128 字符上限
     */
    private String eventId(Long userCouponId) {
        return "coupon-expiry:" + userCouponId;
    }

    /**
     * 拼接提醒正文，说明是哪张券、何时过期。
     *
     * @param candidate 待提醒的券
     * @return 通知正文
     */
    private String content(CouponExpiryReminderRow candidate) {
        String title = candidate.getTitle() == null || candidate.getTitle().isBlank()
                ? "优惠券" : candidate.getTitle().trim();
        return "「" + title + "」将于 " + candidate.getValidUntil().format(EXPIRY_FORMATTER) + " 过期，请尽快使用";
    }
}
