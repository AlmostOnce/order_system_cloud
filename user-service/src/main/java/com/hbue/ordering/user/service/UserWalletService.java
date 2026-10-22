package com.hbue.ordering.user.service;

import com.hbue.ordering.user.dto.request.RechargeRequest;
import com.hbue.ordering.user.vo.BalanceRecordPageVO;
import com.hbue.ordering.user.vo.BalanceRecordVO;
import com.hbue.ordering.user.vo.UserProfileVO;

public interface UserWalletService {
    BalanceRecordVO recharge(Long userId, String idempotencyKey, RechargeRequest request);

    BalanceRecordPageVO listRecords(Long userId, String type, Long beforeId, int limit);

    UserProfileVO getMyProfile(Long userId);
}
