package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.service.AccountDeletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 账号注销到期扫描任务。
 *
 * <p>定时做两件事：</p>
 * <ol>
 *     <li>找出即将在提醒窗口内生效且尚未提醒的申请，发送一次提醒邮件；</li>
 *     <li>找出已到生效时间的申请，逐个抢占 Redis 租约后执行到期复核与匿名化。</li>
 * </ol>
 *
 * <p>租约用于多实例部署时避免同一用户被并发处理；单用户处理失败只记录日志，
 * 不影响本批其他用户，租约过期后下一轮自动重试。真正的状态变更在
 * {@link AccountDeletionService#finalizeDueUser(Long)} 的事务内完成。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Component
@ConditionalOnProperty(name = "account-deletion.scan-enabled", havingValue = "true", matchIfMissing = true)
public class AccountDeletionScanTask {

    /**
     * 日志对象；不记录邮箱、令牌等敏感信息。
     */
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionScanTask.class);

    /**
     * 数据库时间口径。
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 用户数据访问接口。
     */
    private final UserMapper userMapper;

    /**
     * 账号注销业务服务。
     */
    private final AccountDeletionService accountDeletionService;

    /**
     * 注销凭据服务，提供租约抢占与释放。
     */
    private final AccountDeletionVerificationService verification;

    /**
     * 单批扫描数量，避免一次拉取过多用户。
     */
    private final int batchSize;

    /**
     * 单用户处理租约秒数，应大于一次复核加邮件发送的耗时。
     */
    private final long leaseSeconds;

    /**
     * 生效前提醒提前量（小时）。
     */
    private final long reminderLeadHours;

    /**
     * 创建扫描任务。
     *
     * @param userMapper            用户数据访问
     * @param accountDeletionService 注销业务服务
     * @param verification          注销凭据服务
     * @param batchSize             单批数量
     * @param leaseSeconds          租约秒数
     * @param reminderLeadHours     提醒提前量
     */
    public AccountDeletionScanTask(UserMapper userMapper,
                                   AccountDeletionService accountDeletionService,
                                   AccountDeletionVerificationService verification,
                                   @Value("${account-deletion.scan-batch-size:20}") int batchSize,
                                   @Value("${account-deletion.lease-seconds:300}") long leaseSeconds,
                                   @Value("${account-deletion.reminder-lead-hours:24}") long reminderLeadHours) {
        this.userMapper = userMapper;
        this.accountDeletionService = accountDeletionService;
        this.verification = verification;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.reminderLeadHours = reminderLeadHours;
    }

    /**
     * 定时扫描提醒与到期注销。
     *
     * <p>默认每 5 分钟执行一次，首次延迟 1 分钟，避免与服务启动争抢资源。
     * 每一轮内部异常都被捕获，保证调度线程不会因单次失败停止。</p>
     */
    @Scheduled(fixedDelayString = "${account-deletion.scan-interval-ms:300000}",
            initialDelayString = "${account-deletion.scan-initial-delay-ms:60000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        sendReminders(now);
        finalizeDue(now);
    }

    /**
     * 发送 24 小时提醒。
     *
     * @param now 当前时间
     */
    private void sendReminders(LocalDateTime now) {
        List<Long> userIds;
        try {
            userIds = userMapper.selectDeletionReminderIds(now, now.plusHours(reminderLeadHours), batchSize);
        } catch (RuntimeException exception) {
            log.warn("查询待提醒注销账号失败，原因={}", exception.getClass().getSimpleName());
            return;
        }
        for (Long userId : userIds) {
            try {
                accountDeletionService.sendEffectiveReminder(userId);
            } catch (RuntimeException exception) {
                log.warn("注销提醒处理失败，userId={}，原因={}", userId, exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * 处理已到生效时间的注销账号。
     *
     * @param now 当前时间
     */
    private void finalizeDue(LocalDateTime now) {
        List<Long> userIds;
        try {
            userIds = userMapper.selectDueDeletionIds(now, batchSize);
        } catch (RuntimeException exception) {
            log.warn("查询到期注销账号失败，原因={}", exception.getClass().getSimpleName());
            return;
        }
        for (Long userId : userIds) {
            String leaseId = UUID.randomUUID().toString();
            boolean acquired;
            try {
                acquired = verification.tryAcquireLease(userId, leaseId, leaseSeconds);
            } catch (RuntimeException exception) {
                log.warn("抢占注销处理租约失败，userId={}，原因={}", userId, exception.getClass().getSimpleName());
                continue;
            }
            if (!acquired) {
                // 其他实例正在处理；本轮跳过，避免重复匿名化或重复发信。
                continue;
            }
            try {
                accountDeletionService.finalizeDueUser(userId);
            } catch (RuntimeException exception) {
                // 单用户失败不能中断整批；事务已回滚，租约释放后下一轮重试。
                log.warn("注销到期处理失败，userId={}，原因={}", userId, exception.getClass().getSimpleName());
            } finally {
                try {
                    verification.releaseLease(userId, leaseId);
                } catch (RuntimeException exception) {
                    // 释放失败时租约自然过期，不能影响本批其他用户。
                    log.warn("释放注销处理租约失败，userId={}", userId);
                }
            }
        }
    }
}
