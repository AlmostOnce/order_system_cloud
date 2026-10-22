package com.hbue.ordering.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.dto.request.RechargeRequest;
import com.hbue.ordering.user.mapper.BalanceRecordMapper;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.BalanceRecordDO;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.PayPasswordService;
import com.hbue.ordering.user.service.UserWalletService;
import com.hbue.ordering.user.vo.BalanceRecordPageVO;
import com.hbue.ordering.user.vo.BalanceRecordVO;
import com.hbue.ordering.user.vo.UserProfileVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 模拟充值仍保留真实记账的一致性与幂等性，不调用第三方支付。 */
@Service
public class UserWalletServiceImpl implements UserWalletService {
    private static final BigDecimal MAX_RECHARGE = new BigDecimal("10000.00");
    private final UserMapper userMapper;
    private final BalanceRecordMapper recordMapper;
    private final PayPasswordService payPasswordService;

    public UserWalletServiceImpl(UserMapper userMapper, BalanceRecordMapper recordMapper,
                                 PayPasswordService payPasswordService) {
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.payPasswordService = payPasswordService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceRecordVO recharge(Long userId, String idempotencyKey, RechargeRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{8,64}")) {
            throw badRequest("充值请求标识无效");
        }
        if (request == null || request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().compareTo(MAX_RECHARGE) > 0 || request.getAmount().scale() > 2
                || request.getPayPassword() == null || request.getPayPassword().isBlank()
                || request.getPayPassword().length() < 6 || request.getPayPassword().length() > 20) {
            throw badRequest("请输入 0.01 至 10000 元的充值金额及有效支付密码，金额最多两位小数");
        }
        // 先锁用户行，确保同一用户并发重复充值能读取前一个事务已提交的流水。
        UserDO user = userMapper.selectBalanceForUpdate(userId);
        requireAvailable(user);
        BalanceRecordDO existing = recordMapper.selectOne(new QueryWrapper<BalanceRecordDO>()
                .eq("user_id", userId).eq("biz_type", "MOCK_RECHARGE")
                .eq("biz_ref", idempotencyKey).eq("change_type", "RECHARGE"));
        if (existing != null) {
            if (existing.getAmount().compareTo(request.getAmount()) != 0) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "该充值请求已用于其他金额");
            }
            return view(existing);
        }
        // 复用现有支付密码校验、未设置判定及 Redis 防爆破规则，不另存明文。
        payPasswordService.verifyPayPassword(userId, request.getPayPassword());
        if (userMapper.creditBalance(userId, request.getAmount()) != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "充值后余额超出账户上限");
        }
        UserDO updated = userMapper.selectBalanceById(userId);
        if (updated == null || updated.getBalance() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "读取充值后余额失败");
        }
        BalanceRecordDO record = new BalanceRecordDO();
        record.setUserId(userId);
        record.setChangeType("RECHARGE");
        record.setBizType("MOCK_RECHARGE");
        record.setBizRef(idempotencyKey);
        record.setAmount(request.getAmount());
        record.setBalanceAfter(updated.getBalance());
        record.setRemark("余额充值");
        record.setCreatedAt(LocalDateTime.now());
        if (recordMapper.insert(record) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "充值记账失败");
        }
        return view(record);
    }

    @Override
    public BalanceRecordPageVO listRecords(Long userId, String type, Long beforeId, int limit) {
        requireAvailable(userMapper.selectBalanceById(userId));
        if (limit < 1 || limit > 50 || (beforeId != null && beforeId <= 0)
                || (type != null && !Set.of("RECHARGE", "PAY", "REFUND").contains(type))) {
            throw badRequest("流水查询参数无效");
        }
        List<BalanceRecordDO> rows = recordMapper.selectList(new QueryWrapper<BalanceRecordDO>()
                .eq("user_id", userId).eq(type != null, "change_type", type)
                .lt(beforeId != null, "record_id", beforeId).orderByDesc("record_id")
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<BalanceRecordVO> items = rows.stream().limit(limit).map(this::view).toList();
        return new BalanceRecordPageVO(items,
                hasMore ? items.getLast().recordId() : null, hasMore);
    }

    @Override
    public UserProfileVO getMyProfile(Long userId) {
        UserDO user = userMapper.selectOwnProfile(userId);
        requireAvailable(user);
        return new UserProfileVO(user.getUserId(), user.getUsername(), user.getRoleCode(), user.getEmail(),
                user.getPhone(), user.getAvatarUrl(), user.getCreatedAt());
    }

    private void requireAvailable(UserDO user) {
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在或账户不可用");
        }
    }

    private BalanceRecordVO view(BalanceRecordDO record) {
        return new BalanceRecordVO(record.getRecordId(), record.getChangeType(), record.getAmount(),
                record.getBalanceAfter(), record.getBizRef(), record.getRemark(), record.getCreatedAt());
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }
}
