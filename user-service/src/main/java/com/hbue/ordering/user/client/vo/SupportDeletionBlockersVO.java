package com.hbue.ordering.user.client.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单服务返回的账号注销阻断统计。
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportDeletionBlockersVO {

    /**
     * 未关闭工单数量：待处理、处理中。
     */
    private long openTickets;
}
