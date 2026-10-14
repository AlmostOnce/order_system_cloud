package com.hbue.ordering.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户余额视图对象。
 *
 * <p>只返回本人余额与支付密码设置状态。余额属于敏感信息，
 * 接口必须按登录用户本人过滤，越权按资源不存在处理。</p>
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
