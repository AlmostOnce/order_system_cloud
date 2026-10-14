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
 * 该接口不在 Gateway 路由白名单内，外部无法直接访问。</p>
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
     * @return 扣款结果；扣款失败时返回业务失败响应（HTTP 状态码非 2xx）
     */
    @PostMapping("/internal/users/{userId}/balance/deduct")
    ApiResponse<UserBalanceDeductResultVO> deduct(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody UserBalanceDeductCommand command
    );
}
