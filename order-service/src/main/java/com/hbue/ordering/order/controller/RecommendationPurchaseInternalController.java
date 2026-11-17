package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotPageVO;
import com.hbue.ordering.order.service.RecommendationPurchaseSnapshotQueryService;
import com.hbue.ordering.order.security.InternalTokenVerifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推荐服务历史购买的内部只读导出，仅允许持有内部共享凭据的系统调用。
 * 不提供用户级外部查询，也不经网关开放；服务直连或能够访问端口均不能替代令牌验证。
 */
@RestController
@RequestMapping("/internal/orders/recommendation-purchase-snapshots")
public class RecommendationPurchaseInternalController {
    /** 与既有订单内部接口共用恒定时间内部凭据校验器。 */
    private final InternalTokenVerifier tokens;
    /** 只读有界回填能力，不触发购买Outbox或任何订单状态变化。 */
    private final RecommendationPurchaseSnapshotQueryService snapshots;

    /** 注入服务级认证与源事实查询，构造依赖均为必需。 */
    public RecommendationPurchaseInternalController(InternalTokenVerifier tokens,
            RecommendationPurchaseSnapshotQueryService snapshots) {
        this.tokens = tokens; this.snapshots = snapshots;
    }

    /**
     * 读取已支付订单的下一页快照；不接收userId，不将内部购买身份暴露为学生接口。
     *
     * @param token X-Internal-Token内部凭据，缺失或错误均拒绝
     * @param afterOrderId 上一页末订单主键，首请求默认0
     * @param limit 单页订单数，默认100，服务层强制限定1至100
     * @return ApiResponse包装的完整订单快照页；版本与实时Topic一致
     */
    @GetMapping
    public ApiResponse<RecommendationPurchaseSnapshotPageVO> page(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(value = "afterOrderId", defaultValue = "0") long afterOrderId,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        if (!tokens.verify(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "内部调用凭据无效");
        }
        return ApiResponse.success(snapshots.page(afterOrderId, limit));
    }
}
