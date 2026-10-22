package com.hbue.ordering.stock.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.stock.api.client.fallback.StockServiceClientFallbackFactory;
import com.hbue.ordering.stock.api.dto.StockDeductRequest;
import com.hbue.ordering.stock.api.dto.StockRestoreRequest;
import com.hbue.ordering.stock.api.vo.StockVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 库存服务内部调用客户端。
 *
 * <p>供 order-service 在下单链路中扣减库存、在取消/退款链路中回补库存，
 * 以及查询库存快照。属于服务间内部调用，使用共享密钥 {@code X-Internal-Token} 鉴权，
 * 不携带用户 JWT；该路径不在 Gateway 路由白名单内，外部无法直接访问。</p>
 *
 * <p>降级工厂统一返回「服务不可用」，调用方必须把非成功业务码当作失败处理：
 * 扣减失败时全局事务必须回滚，绝不能被当成「扣减成功」继续落单。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@FeignClient(
        name = "stock-service",
        contextId = "stockServiceClient",
        fallbackFactory = StockServiceClientFallbackFactory.class
)
public interface StockServiceClient {

    /** 按窗口批量读取数据库库存，缺少的菜品记录不会出现在结果中。 */
    @GetMapping("/internal/stocks/windows/{windowId}")
    ApiResponse<List<StockVO>> getWindowStocks(
            @PathVariable("windowId") Long windowId,
            @RequestHeader("X-Internal-Token") String internalToken
    );

    /**
     * 扣减库存，并按业务单据幂等记账。
     *
     * @param internalToken 服务间共享密钥
     * @param request 扣减请求（订单号、档口、菜品明细）
     * @return 扣减结果；库存不足时返回明确的业务失败码，调用方须触发全局回滚
     */
    @PostMapping("/internal/stocks/deduct")
    ApiResponse<Void> deduct(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody StockDeductRequest request
    );

    /**
     * 回补库存，同一订单同一菜品只回补一次。
     *
     * @param internalToken 服务间共享密钥
     * @param request 回补请求（订单号、档口、菜品明细）
     * @return 回补结果
     */
    @PostMapping("/internal/stocks/restore")
    ApiResponse<Void> restore(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody StockRestoreRequest request
    );

    /**
     * 查询指定档口菜品的库存快照。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @param internalToken 服务间共享密钥
     * @return 库存视图；记录不存在时 data 为 null
     */
    @GetMapping("/internal/stocks/{windowId}/{productId}")
    ApiResponse<StockVO> getStock(
            @PathVariable("windowId") Long windowId,
            @PathVariable("productId") Long productId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
