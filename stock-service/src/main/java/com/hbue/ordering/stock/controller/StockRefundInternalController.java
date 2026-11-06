package com.hbue.ordering.stock.controller;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.stock.api.dto.StockRefundRequest;
import com.hbue.ordering.stock.service.StockRefundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/** 库存部分退款内部接口，不能经由学生端指定数量直接补库存。 */
@RestController @RequestMapping("/internal/stocks")
public class StockRefundInternalController {
    private final StockRefundService refunds; private final InternalScopeTokenVerifier tokens;
    public StockRefundInternalController(StockRefundService refunds,InternalScopeTokenVerifier tokens) {this.refunds=refunds;this.tokens=tokens;}
    @PostMapping("/refund") public ApiResponse<String> restore(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @Valid @RequestBody StockRefundRequest command) {tokens.verify(token);return ApiResponse.success(refunds.restore(command));}
}
