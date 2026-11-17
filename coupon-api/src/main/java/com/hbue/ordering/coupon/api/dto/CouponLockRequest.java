package com.hbue.ordering.coupon.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单服务请求锁券时提交的可信业务事实。
 *
 * <p>本契约只允许受 X-Internal-Token 保护的服务间接口使用。用户 ID、窗口和适用商品金额
 * 必须由订单服务从认证主体及商品服务报价中得出；优惠券服务仍独立复核持券归属、窗口范围、
 * 门槛、时效和状态，不能信任调用方提供的优惠金额。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponLockRequest {

    /** 用户持有的券记录主键。 */
    @NotNull(message = "用户优惠券 ID 不能为空")
    @Positive(message = "用户优惠券 ID 必须大于 0")
    private Long userCouponId;

    /** 经订单服务认证得到的用户主键，优惠券服务据此校验持券归属。 */
    @NotNull(message = "用户 ID 不能为空")
    @Positive(message = "用户 ID 必须大于 0")
    private Long userId;

    /** 服务端生成的订单号，也是锁定/释放的幂等关联键。 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 当前订单所属窗口，用于核对平台券适用范围。 */
    @NotNull(message = "窗口 ID 不能为空")
    @Positive(message = "窗口 ID 必须大于 0")
    private Long windowId;

    /** 订单服务核价后纳入优惠门槛的商品金额，不包含客户端报价或费用项。 */
    @NotNull(message = "适用商品金额不能为空")
    @DecimalMin(value = "0.01", message = "适用商品金额必须大于 0")
    private BigDecimal eligibleAmount;
}
