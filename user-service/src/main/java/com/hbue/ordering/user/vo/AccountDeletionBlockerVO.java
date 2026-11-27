package com.hbue.ordering.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单项注销阻断原因。
 *
 * <p>前端按 {@link #code} 做逻辑判断、按 {@link #label} 展示中文说明，不使用
 * 可变文案作为判断条件。数量为 0 的阻断项不会出现在资格响应中。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionBlockerVO {

    /**
     * 稳定阻断编码，例如 UNFINISHED_ORDERS、UNSETTLED_REFUNDS、OPEN_TICKETS、BALANCE_OVER_LIMIT。
     */
    private String code;

    /**
     * 面向用户的中文说明。
     */
    private String label;

    /**
     * 受影响数量；余额类阻断表示超出的金额时为 0，仅作为提示。
     */
    private long count;
}
