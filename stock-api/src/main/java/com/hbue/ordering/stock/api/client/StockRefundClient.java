package com.hbue.ordering.stock.api.client;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.stock.api.dto.StockRefundRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
/** 部分退款库存回补：内部密钥认证，无成功降级，远程结果不明时由退款任务同号重试。 */
@FeignClient(name="stock-service",contextId="stockRefundClient")
public interface StockRefundClient {
    @PostMapping("/internal/stocks/refund") ApiResponse<String> restore(
            @RequestHeader("X-Internal-Token") String token,@RequestBody StockRefundRequest command);
}
