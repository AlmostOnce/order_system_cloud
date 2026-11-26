package com.hbue.ordering.comment.client;

import com.hbue.ordering.comment.client.vo.PurchaseFactVO;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单服务消费事实内部客户端。
 *
 * <p>评论服务不保存订单数据，"是否买过该菜品/是否在该档口消费过"必须实时向订单服务确认。
 * 该路径走服务间共享令牌，不携带用户 JWT：用户身份由评论服务从已认证令牌解析后
 * 作为参数传入，避免把终端请求原样转发到内部接口。</p>
 *
 * <p>与 {@link CommentOrderClient} 的区别：后者按订单号校验单笔购买的归属与状态，
 * 用于创建评价；本客户端按"用户 + 目标"判断历史消费事实，用于评论准入，
 * 评论请求本身没有订单号，不能复用前者。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@FeignClient(name = "order-service", contextId = "commentPurchaseFactClient")
public interface CommentPurchaseFactClient {

    /**
     * 查询消费事实。
     *
     * @param internalToken 服务间共享令牌
     * @param userId        用户内部编号
     * @param targetType    PRODUCT 菜品或 WINDOW 档口
     * @param targetId      菜品编号或档口编号
     * @return 布尔结论；调用失败由 Feign 抛出异常，由准入服务统一映射为 503
     */
    @GetMapping("/internal/orders/purchase-facts")
    ApiResponse<PurchaseFactVO> purchaseFact(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestParam("userId") Long userId,
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId
    );
}
