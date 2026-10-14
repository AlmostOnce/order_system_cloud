package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.user.api.dto.UserBalanceDeductCommand;
import com.hbue.ordering.user.api.vo.UserBalanceDeductResultVO;
import com.hbue.ordering.user.service.UserBalanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户余额内部扣款接口。
 *
 * <p>供 payment-service 在余额渠道下单时调用，使用共享密钥鉴权，
 * 不暴露给外部客户端。账务在同库同事务内完成，调用方不得据此做跨服务事务。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@RestController
@Tag(name = "内部接口（服务间调用）")
@RequestMapping("/internal/users")
public class UserBalanceInternalController {

    /** 用户余额业务服务。 */
    private final UserBalanceService userBalanceService;

    /** 内部调用令牌校验器。 */
    private final InternalScopeTokenVerifier internalScopeTokenVerifier;

    /**
     * 创建余额扣款内部接口。
     *
     * @param userBalanceService 用户余额业务服务
     * @param internalScopeTokenVerifier 内部调用令牌校验器
     */
    public UserBalanceInternalController(
            UserBalanceService userBalanceService,
            InternalScopeTokenVerifier internalScopeTokenVerifier
    ) {
        this.userBalanceService = userBalanceService;
        this.internalScopeTokenVerifier = internalScopeTokenVerifier;
    }

    /**
     * 扣减指定用户余额并按业务单据幂等记账。
     *
     * @param userId 用户 ID
     * @param internalToken 服务间共享密钥
     * @param command 余额扣款命令
     * @return 扣款结果
     */
    @PostMapping("/{userId}/balance/deduct")
    public ApiResponse<UserBalanceDeductResultVO> deduct(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "X-Internal-Token", required = false)
            String internalToken,
            @RequestBody UserBalanceDeductCommand command
    ) {
        // 内部调用必须携带正确的共享密钥，失败时统一返回 401。
        internalScopeTokenVerifier.verify(internalToken);
        return ApiResponse.success(
                userBalanceService.deduct(userId, command)
        );
    }
}
