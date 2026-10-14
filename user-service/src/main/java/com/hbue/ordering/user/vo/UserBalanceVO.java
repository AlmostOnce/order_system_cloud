package com.hbue.ordering.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户余额视图对象。
 *
 * <p>只返回本人余额与支付密码设置状态，不包含账户标识、支付密码哈希或流水。
 * 余额属于敏感信息，查询接口必须根据已认证 JWT 主体定位用户，不能接受客户端
 * 指定的用户 ID。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBalanceVO {

    /**
     * 账户余额。
     */
    private BigDecimal balance;

    /**
     * 是否已设置支付密码；未设置前禁止使用余额。
     */
    private Boolean payPasswordSet;
}
