package com.hbue.ordering.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.api.dto.UserBalanceDeductCommand;
import com.hbue.ordering.user.api.vo.UserBalanceDeductResultVO;
import com.hbue.ordering.user.mapper.BalanceRecordMapper;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.BalanceRecordDO;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.PayPasswordService;
import com.hbue.ordering.user.service.UserBalanceService;
import com.hbue.ordering.user.vo.UserBalanceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户余额业务实现。
 *
 * <p>扣款核心是"先插流水占位、再条件扣款、最后回填余额"：
 * 流水插入依赖唯一键 {@code (user_id, biz_type, biz_ref, change_type)} 充当幂等锁，
 * 并发重复请求中只有一个能插入成功，其余直接返回幂等结果；
 * 条件扣款把"余额充足"交给数据库原子判定，防止并发超扣。
 * 占位流水在事务提交前回填，外部读不到中间状态。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Service
public class UserBalanceServiceImpl implements UserBalanceService {

    /** 日志对象。 */
    private static final Logger log =
            LoggerFactory.getLogger(UserBalanceServiceImpl.class);

    /** 消费类型的余额变动。 */
    private static final String CHANGE_TYPE_PAY = "PAY";

    /** 用户启用状态。 */
    private static final Integer USER_STATUS_ENABLED = 1;

    /** 用户数据访问接口。 */
    private final UserMapper userMapper;

    /** 余额流水数据访问接口。 */
    private final BalanceRecordMapper balanceRecordMapper;

    /** 支付密码业务服务。 */
    private final PayPasswordService payPasswordService;

    /**
     * 创建用户余额业务服务。
     *
     * @param userMapper 用户数据访问接口
     * @param balanceRecordMapper 余额流水数据访问接口
     * @param payPasswordService 支付密码业务服务
     */
    public UserBalanceServiceImpl(
            UserMapper userMapper,
            BalanceRecordMapper balanceRecordMapper,
            PayPasswordService payPasswordService
    ) {
        this.userMapper = userMapper;
        this.balanceRecordMapper = balanceRecordMapper;
        this.payPasswordService = payPasswordService;
    }

    /**
     * 查询本人余额与支付密码设置状态。
     *
     * @param userId 当前登录用户 ID
     * @return 余额视图对象
     */
    @Override
    public UserBalanceVO getMyBalance(Long userId) {
        UserDO user = requireEnabledUser(userId);
        return UserBalanceVO.builder()
                .balance(user.getBalance())
                .payPasswordSet(user.getPayPasswordHash() != null
                        && !user.getPayPasswordHash().isBlank())
                .build();
    }

    /**
     * 按业务单据扣减余额并幂等记账。
     *
     * <p>流水唯一键冲突不视为失败：那说明同一业务单据已经记过账并完成扣款，
     * 因此对 {@link DuplicateKeyException} 不回滚事务，直接返回幂等结果。</p>
     *
     * @param userId 用户 ID
     * @param command 余额扣款命令
     * @return 扣款结果
     */
    @Override
    @Transactional(noRollbackFor = DuplicateKeyException.class)
    public UserBalanceDeductResultVO deduct(
            Long userId,
            UserBalanceDeductCommand command
    ) {
        validate(userId, command);

        String bizType = command.getBizType().trim();
        String bizRef = command.getBizRef().trim();
        BigDecimal amount = command.getAmount();

        // 第一重幂等：同一业务单据已记过账时直接返回，且不再校验支付密码。
        BalanceRecordDO existing = selectRecord(userId, bizType, bizRef);
        if (existing != null) {
            return buildIdempotentResult(userId, bizRef, existing);
        }

        // 支付密码是余额支付的第二道凭据，未设置或校验失败都不得扣款。
        payPasswordService.verifyPayPassword(
                userId,
                command.getPayPassword()
        );

        // 第二重幂等：先插流水占位，唯一键冲突即代表并发重复请求。
        BalanceRecordDO record = new BalanceRecordDO();
        record.setUserId(userId);
        record.setChangeType(CHANGE_TYPE_PAY);
        record.setAmount(amount);
        // 占位值，扣款成功后在同一事务内回填真实余额，提交前外部不可见。
        record.setBalanceAfter(BigDecimal.ZERO);
        record.setBizType(bizType);
        record.setBizRef(bizRef);
        record.setRemark(command.getRemark());
        record.setCreatedAt(LocalDateTime.now());
        try {
            balanceRecordMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            BalanceRecordDO concurrent = selectRecord(userId, bizType, bizRef);
            if (concurrent == null) {
                // 非业务单据冲突（例如主键异常），按真实故障抛出。
                throw exception;
            }
            log.info("余额扣款命中幂等重放，userId={}，bizRef={}", userId, bizRef);
            return buildIdempotentResult(userId, bizRef, concurrent);
        }

        // 第三重幂等：条件扣款，余额不足或账号不可用时受影响行数为 0。
        int affected = userMapper.deductBalance(userId, amount);
        if (affected == 0) {
            // 抛异常回滚本次占位流水，避免留下"有流水但没扣款"的假账。
            throw insufficientBalance(userId);
        }

        BigDecimal balanceAfter = readBalance(userId);
        balanceRecordMapper.updateBalanceAfter(
                record.getRecordId(),
                balanceAfter
        );
        log.info("余额扣款成功，userId={}，bizRef={}，金额={}，扣后余额={}",
                userId, bizRef, amount, balanceAfter);

        return UserBalanceDeductResultVO.builder()
                .userId(userId)
                .bizRef(bizRef)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .idempotent(false)
                .build();
    }

    /**
     * 校验扣款入参。
     *
     * @param userId 用户 ID
     * @param command 余额扣款命令
     */
    private void validate(Long userId, UserBalanceDeductCommand command) {
        if (userId == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "用户 ID 不能为空"
            );
        }
        if (command == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "扣款参数不能为空"
            );
        }
        if (command.getBizType() == null || command.getBizType().isBlank()
                || command.getBizRef() == null || command.getBizRef().isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "业务类型与业务单据引用不能为空"
            );
        }
        if (command.getAmount() == null
                || command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "扣款金额必须大于 0"
            );
        }
    }

    /**
     * 查询同一业务单据的既有流水。
     *
     * @param userId 用户 ID
     * @param bizType 业务类型
     * @param bizRef 业务单据引用
     * @return 既有流水，不存在时返回 null
     */
    private BalanceRecordDO selectRecord(
            Long userId,
            String bizType,
            String bizRef
    ) {
        return balanceRecordMapper.selectOne(
                new QueryWrapper<BalanceRecordDO>()
                        .eq("user_id", userId)
                        .eq("biz_type", bizType)
                        .eq("biz_ref", bizRef)
                        .eq("change_type", CHANGE_TYPE_PAY)
        );
    }

    /**
     * 读取用户当前余额。
     *
     * @param userId 用户 ID
     * @return 当前余额
     */
    private BigDecimal readBalance(Long userId) {
        UserDO user = userMapper.selectBalanceById(userId);
        return user == null ? BigDecimal.ZERO : user.getBalance();
    }

    /**
     * 查询启用且未删除的用户，不存在时按资源不存在处理。
     *
     * @param userId 用户 ID
     * @return 用户持久化对象
     */
    private UserDO requireEnabledUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }
        UserDO user = userMapper.selectBalanceById(userId);
        if (user == null || !USER_STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在"
            );
        }
        return user;
    }

    /**
     * 构造余额不足或账号不可用的业务异常。
     *
     * @param userId 用户 ID
     * @return 业务异常
     */
    private BusinessException insufficientBalance(Long userId) {
        UserDO user = userMapper.selectBalanceById(userId);
        if (user == null || !USER_STATUS_ENABLED.equals(user.getStatus())) {
            return new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在"
            );
        }
        return new BusinessException(
                CommonErrorCode.CONFLICT,
                "账户余额不足"
        );
    }

    /**
     * 构造幂等重放结果。
     *
     * @param userId 用户 ID
     * @param bizRef 业务单据引用
     * @param existing 既有流水
     * @return 幂等结果
     */
    private UserBalanceDeductResultVO buildIdempotentResult(
            Long userId,
            String bizRef,
            BalanceRecordDO existing
    ) {
        return UserBalanceDeductResultVO.builder()
                .userId(userId)
                .bizRef(bizRef)
                .amount(existing.getAmount())
                .balanceAfter(existing.getBalanceAfter())
                .idempotent(true)
                .build();
    }
}
