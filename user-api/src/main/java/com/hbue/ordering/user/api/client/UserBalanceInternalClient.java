package com.hbue.ordering.user.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.dto.UserBalanceDeductCommand;
import com.hbue.ordering.user.api.vo.UserBalanceDeductResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 用户余额内部调用客户端。
 *
 * <p>供 payment-service 在余额渠道下单时扣减用户余额，属于服务间内部调用，
 * 使用共享密钥 {@code X-Internal-Token} 鉴权，不携带用户 JWT。
 * 该接口不在 Gateway 路由白名单内，外部无法直接访问；共享密钥只用于服务身份校验，
 * 不能替代 user-service 对支付密码、金额和业务幂等键的校验。</p>
 *
 * <p>刻意不配置 fallbackFactory（与 {@code OrderInternalClient} 保持一致）：
 * 扣款结果必须由调用方显式依据状态码判定，绝不能被降级逻辑替换成
 * "扣款成功"或笼统的"服务不可用"，否则支付单会被错误标记成功。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@FeignClient(name = "user-service", contextId = "userBalanceInternalClient")
public interface UserBalanceInternalClient {

    /**
     * 扣减指定用户余额，并按业务单据幂等记账。
     *
     * @param userId 用户 ID
     * @param internalToken 服务间共享密钥
     * @param command 余额扣款命令（含支付密码）
     * @return 扣款成功结果；包含业务引用、金额、扣款后余额和幂等重放标记
     * @throws feign.FeignException 下游返回非成功 HTTP 状态或调用链路失败时抛出，调用方必须停止推进支付成功
     */
    @PostMapping("/internal/users/{userId}/balance/deduct")
    ApiResponse<UserBalanceDeductResultVO> deduct(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody UserBalanceDeductCommand command
    );
}
