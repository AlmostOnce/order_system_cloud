package com.hbue.ordering.user.controller;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.user.api.dto.BalanceRefundCommand;
import com.hbue.ordering.user.service.BalanceRefundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/** 余额退款仅服务间调用，使用共享密钥，不接受学生直接触发账户入账。 */
@RestController @RequestMapping("/internal/users/{userId}/balance")
public class BalanceRefundInternalController {
    private final BalanceRefundService refunds; private final InternalScopeTokenVerifier tokens;
    public BalanceRefundInternalController(BalanceRefundService refunds,InternalScopeTokenVerifier tokens) {this.refunds=refunds;this.tokens=tokens;}
    /** 验证服务身份后再进入原扣款校验；内部身份不能替代金额和幂等校验。 */
    @PostMapping("/refund") public ApiResponse<String> refund(@PathVariable("userId") Long userId,
            @RequestHeader(value="X-Internal-Token",required=false) String token,@Valid @RequestBody BalanceRefundCommand command) {
        tokens.verify(token);return ApiResponse.success(refunds.refund(userId,command));
    }
}
