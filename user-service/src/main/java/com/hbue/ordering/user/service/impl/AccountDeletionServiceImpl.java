package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.api.vo.AccountDeletionCancelTokenVO;
import com.hbue.ordering.user.dto.request.AccountDeletionAuthorizationRequest;
import com.hbue.ordering.user.dto.request.AccountDeletionCancelRequest;
import com.hbue.ordering.user.dto.request.AccountDeletionSubmitRequest;
import com.hbue.ordering.user.mapper.BalanceRecordMapper;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.BalanceRecordDO;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.AccountDeletionService;
import com.hbue.ordering.user.service.EmailCodeSender;
import com.hbue.ordering.user.vo.AccountDeletionAuthorizationVO;
import com.hbue.ordering.user.vo.AccountDeletionBlockerVO;
import com.hbue.ordering.user.vo.AccountDeletionEligibilityVO;
import com.hbue.ordering.user.vo.AccountDeletionSubmitVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 学生账号注销实现。
 *
 * <p>状态机与安全边界：</p>
 * <ul>
 *     <li>只有 CUSTOMER 且账号启用、未删除的用户能进入注销流程，用户 ID 一律取自 JWT。</li>
 *     <li>提交前必须通过绑定邮箱验证码换取的一次性授权令牌，余额超过上限或存在未完成
 *     订单、未结案退款、未关闭工单时拒绝提交。</li>
 *     <li>提交在同一行锁事务内完成：递增 credential_version 阻止刷新令牌续期，同时把当前
 *     访问令牌写入黑名单，使提交设备立即下线。</li>
 *     <li>冷静期默认 72 小时，期间只能凭登录时签发的一次性撤回令牌撤回。</li>
 *     <li>到期任务重新核对阻断项，仍有阻断时撤销注销；否则写 WRITE_OFF 流水后匿名化，
 *     保留订单、退款、流水和工单的历史关联。</li>
 * </ul>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    /**
     * 日志对象；不记录邮箱、验证码、令牌或密码。
     */
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionServiceImpl.class);

    /**
     * 数据库时间口径：开发与生产库均使用东八区。
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 邮件中的时间展示格式。
     */
    private static final DateTimeFormatter MAIL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 余额流水业务类型；biz_type 列长度 20，该值 16 个字符。
     */
    private static final String BIZ_TYPE_ACCOUNT_DELETION = "ACCOUNT_DELETION";

    /**
     * 余额流水变动类型：注销放弃余额。
     */
    private static final String CHANGE_TYPE_WRITE_OFF = "WRITE_OFF";

    /**
     * 用户数据访问接口。
     */
    private final UserMapper userMapper;

    /**
     * 余额流水数据访问接口。
     */
    private final BalanceRecordMapper balanceRecordMapper;

    /**
     * 注销凭据服务。
     */
    private final AccountDeletionVerificationService verification;

    /**
     * 阻断项聚合服务。
     */
    private final AccountDeletionBlockerService blockerService;

    /**
     * 邮件发送边界。
     */
    private final EmailCodeSender emailSender;

    /**
     * 密码编码器，用于生成不可匹配的随机密码哈希。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Redis 基础操作，用于把提交注销时的访问令牌立即加入黑名单。
     */
    private final RedisOperationsService redisOperationsService;

    /**
     * 访问令牌黑名单 Key 前缀，必须与认证服务及其他资源服务保持一致。
     */
    private final String accessTokenBlacklistKeyPrefix;

    /**
     * 允许放弃的余额上限，默认 20.00 元。
     */
    private final BigDecimal maxForfeitableBalance;

    /**
     * 冷静期小时数，默认 72 小时。
     */
    private final long coolingOffHours;

    /**
     * 创建账号注销服务。
     *
     * @param userMapper                    用户数据访问
     * @param balanceRecordMapper           余额流水数据访问
     * @param verification                  注销凭据服务
     * @param blockerService                阻断项聚合服务
     * @param emailSender                   邮件发送边界
     * @param passwordEncoder               密码编码器
     * @param redisOperationsService        Redis 基础操作
     * @param accessTokenBlacklistKeyPrefix 访问令牌黑名单 Key 前缀
     * @param maxForfeitableBalance         可放弃余额上限
     * @param coolingOffHours               冷静期小时数
     */
    public AccountDeletionServiceImpl(UserMapper userMapper,
                                      BalanceRecordMapper balanceRecordMapper,
                                      AccountDeletionVerificationService verification,
                                      AccountDeletionBlockerService blockerService,
                                      EmailCodeSender emailSender,
                                      PasswordEncoder passwordEncoder,
                                      RedisOperationsService redisOperationsService,
                                      @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}")
                                      String accessTokenBlacklistKeyPrefix,
                                      @Value("${account-deletion.max-forfeitable-balance:20.00}")
                                      BigDecimal maxForfeitableBalance,
                                      @Value("${account-deletion.cooling-off-hours:72}")
                                      long coolingOffHours) {
        this.userMapper = userMapper;
        this.balanceRecordMapper = balanceRecordMapper;
        this.verification = verification;
        this.blockerService = blockerService;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.redisOperationsService = redisOperationsService;
        this.accessTokenBlacklistKeyPrefix = accessTokenBlacklistKeyPrefix;
        this.maxForfeitableBalance = maxForfeitableBalance;
        this.coolingOffHours = coolingOffHours;
    }

    @Override
    public AccountDeletionEligibilityVO eligibility(Long userId) {
        UserDO user = requireActiveUser(userId);
        boolean pending = user.getDeletionEffectiveAt() != null;
        BigDecimal balance = safeBalance(user);
        boolean balanceOverLimit = balance.compareTo(maxForfeitableBalance) > 0;
        boolean emailVerified = hasVerifiedEmail(user);

        // 待生效期间不再查询订单和工单：此时用户只能撤回，阻断项不影响当前页面。
        AccountDeletionBlockerService.BlockerSummary summary = pending
                ? new AccountDeletionBlockerService.BlockerSummary(0, 0, 0, 0)
                : blockerService.load(userId);

        List<AccountDeletionBlockerVO> blockers = new ArrayList<>(blockerService.toBlockers(summary));
        if (!emailVerified) {
            blockers.add(new AccountDeletionBlockerVO(
                    "EMAIL_UNVERIFIED", "当前账号尚未绑定并验证邮箱，无法通过邮箱验证身份", 0));
        }
        if (balanceOverLimit) {
            blockers.add(new AccountDeletionBlockerVO(
                    "BALANCE_OVER_LIMIT",
                    "余额超过可放弃上限" + maxForfeitableBalance.toPlainString() + "元，请先消费后重试",
                    0));
        }

        return AccountDeletionEligibilityVO.builder()
                .pending(pending)
                .requestedAt(user.getDeletionRequestedAt())
                .effectiveAt(user.getDeletionEffectiveAt())
                .balance(balance)
                .maxForfeitableBalance(maxForfeitableBalance)
                .balanceOverLimit(balanceOverLimit)
                .rejectedRefundWarning(!pending && summary.rejectedRefunds() > 0)
                .maskedEmail(maskEmail(user.getEmail()))
                .emailVerified(emailVerified)
                .blockers(blockers)
                .canApply(!pending && emailVerified && !balanceOverLimit && !summary.hasBlocker())
                .build();
    }

    @Override
    public void sendVerificationCode(Long userId) {
        UserDO user = requireActiveUser(userId);
        if (user.getDeletionEffectiveAt() != null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "注销申请已提交，无需重复验证");
        }
        if (!hasVerifiedEmail(user)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先绑定并验证邮箱后再申请注销");
        }

        // 发送验证码前先核对阻断项，避免用户完成邮箱验证后才发现无法注销。
        AccountDeletionBlockerService.BlockerSummary summary = blockerService.load(userId);
        if (summary.hasBlocker()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, blockerLabel(summary));
        }
        if (safeBalance(user).compareTo(maxForfeitableBalance) > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "余额超过可放弃上限" + maxForfeitableBalance.toPlainString() + "元，请先消费后重试");
        }

        verification.sendCode(userId, user.getEmail());
    }

    @Override
    public AccountDeletionAuthorizationVO authorize(Long userId, AccountDeletionAuthorizationRequest request) {
        UserDO user = requireActiveUser(userId);
        if (user.getDeletionEffectiveAt() != null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "注销申请已提交，无需重复验证");
        }
        if (!hasVerifiedEmail(user)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先绑定并验证邮箱后再申请注销");
        }
        return verification.authorize(userId, user.getEmail(), request.code());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountDeletionSubmitVO submit(Long userId,
                                          String accessTokenId,
                                          Instant accessTokenExpiresAt,
                                          AccountDeletionSubmitRequest request) {
        // 锁定用户行，串行化并发提交、撤回与到期处理。
        UserDO user = userMapper.selectAccountDeletionStateForUpdate(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "账号不存在或已注销");
        }

        // 已有待生效申请时按幂等处理，返回原生效时间，绝不重置冷静期；
        // 同时补做一次访问令牌黑名单，覆盖“申请已提交但本机令牌尚未失效”的重复请求。
        if (user.getDeletionEffectiveAt() != null) {
            blacklistAccessToken(accessTokenId, accessTokenExpiresAt);
            return new AccountDeletionSubmitVO(user.getDeletionEffectiveAt());
        }

        if (!verification.isAuthorizationTokenValid(userId, request.authorizationToken())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "注销授权已失效，请重新验证邮箱");
        }
        if (!hasVerifiedEmail(user)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先绑定并验证邮箱后再申请注销");
        }

        // 事务内重新查询下游，任一依赖失败都会抛出 503 并回滚，不放行不确定状态。
        AccountDeletionBlockerService.BlockerSummary summary = blockerService.load(userId);
        if (summary.hasBlocker()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, blockerLabel(summary));
        }

        BigDecimal balance = safeBalance(user);
        if (balance.compareTo(maxForfeitableBalance) > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "余额超过可放弃上限" + maxForfeitableBalance.toPlainString() + "元，请先消费后重试");
        }
        if (balance.signum() > 0 && !Boolean.TRUE.equals(request.forfeitBalance())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请先确认放弃当前余额");
        }

        LocalDateTime requestedAt = LocalDateTime.now(ZONE);
        LocalDateTime effectiveAt = requestedAt.plusHours(coolingOffHours);
        int updated = userMapper.applyAccountDeletion(userId, requestedAt, effectiveAt);
        if (updated != 1) {
            // 极端并发下另一个请求已写入，按已提交返回原生效时间。
            UserDO current = userMapper.selectAccountDeletionState(userId);
            if (current != null && current.getDeletionEffectiveAt() != null) {
                blacklistAccessToken(accessTokenId, accessTokenExpiresAt);
                return new AccountDeletionSubmitVO(current.getDeletionEffectiveAt());
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "账号状态已变化，请刷新后重试");
        }

        // 提交成功后当前访问令牌立即加入黑名单；Redis 失败会让事务回滚，避免出现
        // “数据库已注销但本机仍可继续调用接口”的不一致状态。
        blacklistAccessToken(accessTokenId, accessTokenExpiresAt);

        // 授权令牌一次性使用，提交成功后立即作废。
        verification.revokeAuthorizationToken(userId);
        String email = user.getEmail();
        notifyAfterCommit(email, "账号注销申请已提交",
                "您的账号注销申请已提交，将于 " + effectiveAt.format(MAIL_TIME) + " 生效。\n"
                        + "生效前如需继续使用账号，请在冷静期内重新登录并撤回注销。\n"
                        + "生效后，您的个人资料将被匿名化，余额（不超过可放弃上限的部分）将被清零，"
                        + "订单、退款、余额流水和工单记录将依法保留。");
        return new AccountDeletionSubmitVO(effectiveAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(AccountDeletionCancelRequest request) {
        // 撤回令牌一次性消费，Key 中只保存摘要。
        Long userId = verification.consumeCancelToken(request.cancelToken());
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "撤回令牌已失效，请重新登录获取");
        }

        UserDO user = userMapper.selectAccountDeletionStateForUpdate(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "账号不存在或已注销");
        }
        if (user.getDeletionEffectiveAt() == null) {
            // 已撤回或从未申请，按幂等成功处理。
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (!user.getDeletionEffectiveAt().isAfter(now)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "注销已生效，无法撤回");
        }

        userMapper.clearAccountDeletion(userId);
        notifyAfterCommit(user.getEmail(), "账号注销已撤回",
                "您的账号注销申请已撤回，账号可继续正常使用。如仍需注销，请重新提交申请。");
    }

    @Override
    public AccountDeletionCancelTokenVO issueCancelToken(Long userId) {
        UserDO user = requireActiveUser(userId);
        LocalDateTime effectiveAt = user.getDeletionEffectiveAt();
        if (effectiveAt == null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "当前没有可撤回的注销申请");
        }
        if (!effectiveAt.isAfter(LocalDateTime.now(ZONE))) {
            // 冷静期已结束但到期扫描尚未完成：不再签发撤回令牌，返回空令牌让客户端
            // 进入只读等待状态。此时拒绝登录会让用户在注销完成前完全无法查看进度。
            return new AccountDeletionCancelTokenVO(null, 0L);
        }
        return verification.issueCancelToken(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finalizeDueUser(Long userId) {
        // 到期处理前再次锁定用户行，防止与撤回或另一实例的扫描重复执行。
        UserDO user = userMapper.selectAccountDeletionStateForUpdate(userId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || user.getDeletionEffectiveAt() == null
                || user.getDeletionEffectiveAt().isAfter(now)) {
            return;
        }

        BigDecimal balance = safeBalance(user);
        // 任何远程依赖失败都会抛出 503 并回滚，本次不删除；租约过期后下轮再试。
        AccountDeletionBlockerService.BlockerSummary summary = blockerService.load(userId);
        boolean balanceOverLimit = balance.compareTo(maxForfeitableBalance) > 0;

        if (summary.hasBlocker() || balanceOverLimit) {
            // 冷静期内产生新的阻断项：撤销注销并通知用户，用户可处理后重新申请。
            userMapper.clearAccountDeletion(userId);
            String reasons = blockerLabel(summary);
            if (balanceOverLimit) {
                reasons = (reasons.isEmpty() ? "" : reasons + "；")
                        + "余额超过可放弃上限" + maxForfeitableBalance.toPlainString() + "元";
            }
            String email = user.getEmail();
            notifyAfterCommit(email, "账号注销已撤销",
                    "由于账号仍有未处理事项，本次注销已自动撤销：" + reasons + "。\n"
                            + "您的账号可继续正常使用，请在处理完成后重新提交注销申请。");
            return;
        }

        if (balance.signum() > 0) {
            // 余额不超过上限且用户已确认放弃：先记 WRITE_OFF 流水，与清零在同一事务内完成。
            BalanceRecordDO record = new BalanceRecordDO();
            record.setUserId(userId);
            record.setChangeType(CHANGE_TYPE_WRITE_OFF);
            record.setAmount(balance);
            record.setBalanceAfter(BigDecimal.ZERO);
            record.setBizType(BIZ_TYPE_ACCOUNT_DELETION);
            record.setBizRef(user.getUserUuid() == null ? String.valueOf(userId) : user.getUserUuid());
            record.setRemark("账号注销，余额放弃清零");
            record.setCreatedAt(now);
            balanceRecordMapper.insert(record);
        }

        // 随机密码哈希保证旧登录密码永远无法再匹配；邮箱和头像同时释放。
        String randomPasswordHash = passwordEncoder.encode(UUID.randomUUID() + "-" + UUID.randomUUID());
        String anonymousUsername = "已注销用户_" + (user.getUserUuid() == null ? userId : user.getUserUuid());
        int anonymized = userMapper.anonymizeAccount(userId, anonymousUsername, randomPasswordHash, now);
        if (anonymized != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "账号状态已变化，注销未完成");
        }

        String email = user.getEmail();
        notifyAfterCommit(email, "账号已注销",
                "您的账号已完成注销。个人资料已匿名化，余额已清零，"
                        + "订单、退款、余额流水和工单记录依法保留。感谢您的使用。");
    }

    @Override
    public void sendEffectiveReminder(Long userId) {
        UserDO user = userMapper.selectAccountDeletionState(userId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || user.getDeletionEffectiveAt() == null
                || !user.getDeletionEffectiveAt().isAfter(now)) {
            return;
        }

        // 条件更新抢占提醒权，多实例扫描时只有更新成功的实例发送邮件。
        int marked = userMapper.markDeletionReminded(userId, now);
        if (marked != 1) {
            return;
        }
        notifyAfterCommit(user.getEmail(), "账号注销即将生效",
                "您的账号注销将于 " + user.getDeletionEffectiveAt().format(MAIL_TIME) + " 生效。\n"
                        + "如需继续使用账号，请在生效前重新登录并撤回注销；生效后数据将按注销规则处理。");
    }

    /**
     * 读取启用且未删除的账号。
     *
     * @param userId 用户 ID
     * @return 用户持久化对象
     */
    private UserDO requireActiveUser(Long userId) {
        UserDO user = userMapper.selectAccountDeletionState(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "账号不存在或已注销");
        }
        return user;
    }

    /**
     * 判断账号是否已绑定并完成邮箱验证。
     *
     * @param user 用户
     * @return 邮箱非空且验证时间存在时返回 true
     */
    private boolean hasVerifiedEmail(UserDO user) {
        return user.getEmail() != null && !user.getEmail().isBlank() && user.getEmailVerifiedAt() != null;
    }

    /**
     * 读取余额，空值按 0 处理。
     *
     * @param user 用户
     * @return 非空余额
     */
    private BigDecimal safeBalance(UserDO user) {
        return user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
    }

    /**
     * 将阻断统计转换为可直接展示的首个原因。
     *
     * @param summary 阻断统计
     * @return 首个阻断项说明；无阻断时返回空字符串
     */
    private String blockerLabel(AccountDeletionBlockerService.BlockerSummary summary) {
        List<AccountDeletionBlockerVO> blockers = blockerService.toBlockers(summary);
        return blockers.isEmpty() ? "" : blockers.get(0).getLabel();
    }

    /**
     * 邮箱脱敏，只保留首字符和域名。
     *
     * @param email 邮箱
     * @return 脱敏结果；邮箱为空时返回 null
     */
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * 将当前访问令牌加入黑名单，使提交注销的设备立即失去调用权限。
     *
     * <p>各资源服务只校验 JWT 的 jti 黑名单，不读取 credential_version，
     * 因此仅递增凭据版本只能阻止刷新令牌续期，无法让已签发的访问令牌立刻失效。
     * 这里按令牌剩余有效期写入与认证服务、其他资源服务一致的黑名单 Key，TTL 到期后自动清理。
     * Redis 写入失败会抛出异常，让所在事务回滚，避免“已注销但仍可访问”的状态。</p>
     *
     * @param accessTokenId        访问令牌 jti，为空时跳过
     * @param accessTokenExpiresAt 访问令牌过期时间，已过期时无需写入
     */
    private void blacklistAccessToken(String accessTokenId, Instant accessTokenExpiresAt) {
        if (accessTokenId == null || accessTokenId.isBlank() || accessTokenExpiresAt == null) {
            return;
        }
        Duration timeToLive = Duration.between(Instant.now(), accessTokenExpiresAt);
        if (timeToLive.isNegative() || timeToLive.isZero()) {
            return;
        }
        redisOperationsService.setValue(
                accessTokenBlacklistKeyPrefix + accessTokenId,
                "1",
                timeToLive
        );
    }

    /**
     * 在当前事务提交后发送通知邮件。
     *
     * <p>邮件失败不能回滚注销状态，因此这里捕获全部运行时异常并只记录日志。
     * 没有活动事务时（例如计划任务直接调用）立即发送。</p>
     *
     * @param email   接收邮箱，为空时跳过
     * @param subject 邮件主题
     * @param text    纯文本正文
     */
    private void notifyAfterCommit(String email, String subject, String text) {
        if (email == null || email.isBlank()) {
            return;
        }
        Runnable task = () -> {
            try {
                emailSender.sendNotice(email, subject, text);
            } catch (RuntimeException exception) {
                log.warn("账号注销通知邮件发送失败，主题={}，原因={}", subject, exception.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
