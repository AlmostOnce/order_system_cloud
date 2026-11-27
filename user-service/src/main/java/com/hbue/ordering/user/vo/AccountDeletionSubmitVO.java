package com.hbue.ordering.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 注销申请提交结果。
 *
 * <p>重复提交且申请仍待生效时返回原生效时间，不重置冷静期；首次提交返回本次计算的
 * 生效时间。客户端据此进入待生效倒计时页面，并提示已退出登录。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionSubmitVO {

    /**
     * 注销生效时间。
     */
    private LocalDateTime effectiveAt;
}
