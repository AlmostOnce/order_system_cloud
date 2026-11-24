package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.user.api.vo.AccountDeletionCancelTokenVO;
import com.hbue.ordering.user.service.AccountDeletionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号注销内部接口。
 *
 * <p>仅供认证服务在登录身份校验通过后调用，用于给待注销账号签发一次性撤回令牌。
 * 接口要求服务间共享令牌，且不校验也签发任何访问令牌；用户服务会再次确认账号
 * 确实存在待生效注销申请后才签发。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@RestController
@RequestMapping("/internal/users")
public class AccountDeletionInternalController {

    /**
     * 账号注销业务服务。
     */
    private final AccountDeletionService accountDeletionService;

    /**
     * 内部调用令牌校验器。
     */
    private final InternalScopeTokenVerifier internalScopeTokenVerifier;

    /**
     * 创建注销内部接口。
     *
     * @param accountDeletionService     账号注销业务服务
     * @param internalScopeTokenVerifier 内部调用令牌校验器
     */
    public AccountDeletionInternalController(AccountDeletionService accountDeletionService,
                                             InternalScopeTokenVerifier internalScopeTokenVerifier) {
        this.accountDeletionService = accountDeletionService;
        this.internalScopeTokenVerifier = internalScopeTokenVerifier;
    }

    /**
     * 签发一次性撤回令牌。
     *
     * @param userId 已通过登录密码校验的用户 ID
     * @param token  服务间共享内部令牌
     * @return 撤回令牌及有效秒数
     */
    @PostMapping("/{userId}/deletion/cancel-token")
    public ApiResponse<AccountDeletionCancelTokenVO> issueCancelToken(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalScopeTokenVerifier.verify(token);
        return ApiResponse.success(accountDeletionService.issueCancelToken(userId));
    }
}
