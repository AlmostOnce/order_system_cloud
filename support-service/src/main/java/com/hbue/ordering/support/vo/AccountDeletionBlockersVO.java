package com.hbue.ordering.support.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号注销阻断统计视图。
 *
 * <p>仅供用户服务内部调用，字段与用户服务 Feign 契约一致；只返回未关闭工单数量，
 * 不返回工单主题或对话内容。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionBlockersVO {

    /**
     * 未关闭工单数量。
     */
    private long openTickets;
}
