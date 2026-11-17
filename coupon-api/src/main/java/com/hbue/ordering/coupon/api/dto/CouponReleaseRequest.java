package com.hbue.ordering.coupon.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 订单取消后释放锁券的内部命令；服务只处理此订单当前绑定的锁定券。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponReleaseRequest {

    /** 订单服务生成的业务订单号，不接受用户券 ID 以外的跨订单释放选择。 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
