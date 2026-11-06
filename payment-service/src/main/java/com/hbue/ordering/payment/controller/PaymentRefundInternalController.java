package com.hbue.ordering.payment.controller;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.payment.service.PaymentRefundService;
import com.hbue.ordering.payment.vo.PaymentRefundFactVO;
import com.hbue.ordering.payment.dto.request.PaymentRefundRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/** 退款内部API，所有方法都先校验服务密钥，网关不暴露internal路径。 */
@RestController @RequestMapping("/internal/payments/orders/{orderId}")
public class PaymentRefundInternalController {
    private final PaymentRefundService refunds; private final InternalScopeTokenVerifier tokens;
    public PaymentRefundInternalController(PaymentRefundService refunds,InternalScopeTokenVerifier tokens) {this.refunds=refunds;this.tokens=tokens;}
    @GetMapping("/refund-fact") public ApiResponse<PaymentRefundFactVO> fact(@PathVariable("orderId") Long orderId,
            @RequestHeader(value="X-Internal-Token",required=false) String token) {tokens.verify(token);return ApiResponse.success(refunds.fact(orderId));}
    /** paymentNo不由调用方选择，实际原支付号只从支付库读取；请求不接受原支付单号。 */
    @PostMapping("/refund") public ApiResponse<String> refund(@PathVariable("orderId") Long orderId,
            @RequestHeader(value="X-Internal-Token",required=false) String token,@Valid @RequestBody PaymentRefundRequest command) {
        tokens.verify(token);return ApiResponse.success(refunds.refund(orderId,command.refundNo(),command.amount()));
    }
}
