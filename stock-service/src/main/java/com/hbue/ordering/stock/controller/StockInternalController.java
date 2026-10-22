package com.hbue.ordering.stock.controller;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.stock.api.dto.StockDeductRequest;
import com.hbue.ordering.stock.api.dto.StockRestoreRequest;
import com.hbue.ordering.stock.api.vo.StockVO;
import com.hbue.ordering.stock.service.StockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 库存内部接口。
 *
 * <p>仅供 order-service 在下单与取消订单时调用：扣减库存、回补库存、查询库存。
 * 这些接口不配置网关路由，外部无法访问；调用方仍需携带共享密钥
 * {@code X-Internal-Token}，防止内网被横向调用。</p>
 *
 * <p>所有接口返回统一响应体。业务失败（如库存不足）在本控制器内被转换为
 * 携带业务错误码的失败响应，并保持 HTTP 200：调用方通过 Feign 调用本接口，
 * 若按 4xx 返回会触发其降级逻辑，把「库存不足」这一明确业务原因
 * 替换成笼统的「服务不可用」，用户将无法得知真实原因。
 * 因此内部接口以业务码而非 HTTP 状态表达业务失败，仅在鉴权失败时保留 401。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@RestController
@Validated
@Tag(name = "内部接口（服务间调用）")
@RequestMapping("/internal/stocks")
public class StockInternalController {

    /** 库存业务服务。 */
    private final StockService stockService;

    /** 内部调用共享密钥校验器。 */
    private final InternalScopeTokenVerifier internalScopeTokenVerifier;

    /**
     * 创建库存内部接口。
     *
     * @param stockService 库存业务服务
     * @param internalScopeTokenVerifier 内部调用共享密钥校验器
     */
    public StockInternalController(
            StockService stockService,
            InternalScopeTokenVerifier internalScopeTokenVerifier
    ) {
        this.stockService = stockService;
        this.internalScopeTokenVerifier = internalScopeTokenVerifier;
    }

    /** 仅供商品服务聚合菜单库存，内部令牌验证与单品查询一致。 */
    @GetMapping("/windows/{windowId}")
    public ApiResponse<List<StockVO>> getWindowStocks(
            @PathVariable("windowId") @Positive Long windowId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken
    ) {
        internalScopeTokenVerifier.verify(internalToken);
        return ApiResponse.success(stockService.getWindowStocks(windowId));
    }

    /**
     * 扣减库存。
     *
     * @param request 扣减请求，包含订单号、档口与菜品明细
     * @param internalToken 服务间共享密钥
     * @return 统一成功响应；库存不足时由异常处理返回业务失败响应
     */
    @PostMapping("/deduct")
    public ApiResponse<Void> deduct(
            @Valid @RequestBody StockDeductRequest request,
            @RequestHeader(value = "X-Internal-Token", required = false)
            String internalToken
    ) {
        // 内部调用必须携带正确的共享密钥，失败时统一返回 401。
        internalScopeTokenVerifier.verify(internalToken);

        try {
            stockService.deduct(request);
        } catch (BusinessException exception) {
            // 业务失败按统一响应体返回（HTTP 200），保留库存不足等具体错误码与提示。
            return businessFailure(exception);
        }
        return ApiResponse.success(null);
    }

    /**
     * 回补库存（取消订单、退款等场景）。
     *
     * @param request 回补请求，包含订单号、档口与菜品明细
     * @param internalToken 服务间共享密钥
     * @return 统一成功响应
     */
    @PostMapping("/restore")
    public ApiResponse<Void> restore(
            @Valid @RequestBody StockRestoreRequest request,
            @RequestHeader(value = "X-Internal-Token", required = false)
            String internalToken
    ) {
        // 内部调用必须携带正确的共享密钥，失败时统一返回 401。
        internalScopeTokenVerifier.verify(internalToken);

        try {
            stockService.restore(request);
        } catch (BusinessException exception) {
            // 业务失败按统一响应体返回（HTTP 200），保留具体的失败原因。
            return businessFailure(exception);
        }
        return ApiResponse.success(null);
    }

    /**
     * 查询指定档口菜品的库存。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @param internalToken 服务间共享密钥
     * @return 库存信息；无库存记录时 data 为 null
     */
    @GetMapping("/{windowId}/{productId}")
    public ApiResponse<StockVO> getStock(
            @PathVariable("windowId") Long windowId,
            @PathVariable("productId") Long productId,
            @RequestHeader(value = "X-Internal-Token", required = false)
            String internalToken
    ) {
        // 内部调用必须携带正确的共享密钥，失败时统一返回 401。
        internalScopeTokenVerifier.verify(internalToken);

        return ApiResponse.success(stockService.getStock(windowId, productId));
    }

    /**
     * 把业务异常转换为带错误码的失败响应。
     *
     * @param exception 业务异常
     * @return 携带原始错误码与提示的失败响应
     */
    private ApiResponse<Void> businessFailure(BusinessException exception) {
        // 保留库存服务抛出的原始错误码与提示，供调用方区分库存不足与库存记录缺失等情况。
        return ApiResponse.failure(
                exception.getErrorCode(),
                exception.getMessage()
        );
    }
}
