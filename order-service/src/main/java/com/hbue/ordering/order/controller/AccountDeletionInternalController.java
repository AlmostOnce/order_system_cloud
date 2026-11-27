package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.order.service.OrderAccountDeletionService;
import com.hbue.ordering.order.vo.AccountDeletionBlockersVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单服务账号注销内部接口。
 *
 * <p>只服务用户服务的注销资格校验与到期复核，要求服务间共享令牌。接口是只读的，
 * 不改变订单或退款状态；用户服务在依赖不可用时按 fail-closed 拒绝注销。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@RestController
@RequestMapping("/internal/users")
public class AccountDeletionInternalController {

    /**
     * 订单侧阻断查询服务。
     */
    private final OrderAccountDeletionService accountDeletionService;

    /**
     * 内部调用令牌校验器。
     */
    private final InternalScopeTokenVerifier internalScopeTokenVerifier;

    /**
     * 创建内部接口。
     *
     * @param accountDeletionService     订单侧阻断查询服务
     * @param internalScopeTokenVerifier 内部调用令牌校验器
     */
    public AccountDeletionInternalController(OrderAccountDeletionService accountDeletionService,
                                             InternalScopeTokenVerifier internalScopeTokenVerifier) {
        this.accountDeletionService = accountDeletionService;
        this.internalScopeTokenVerifier = internalScopeTokenVerifier;
    }

    /**
     * 查询用户维度的订单与退款阻断计数。
     *
     * @param userId 用户 ID
     * @param token  服务间共享内部令牌
     * @return 阻断计数
     */
    @GetMapping("/{userId}/account-deletion/blockers")
    public ApiResponse<AccountDeletionBlockersVO> blockers(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalScopeTokenVerifier.verify(token);
        return ApiResponse.success(accountDeletionService.blockers(userId));
    }
}
