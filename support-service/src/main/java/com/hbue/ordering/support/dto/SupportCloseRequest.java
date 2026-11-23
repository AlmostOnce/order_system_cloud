package com.hbue.ordering.support.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 关闭工单请求体。
 *
 * <p>学生和管理端都使用同一结构。原因可空，只写入一条系统消息用于说明，
 * 不单独建字段；关闭本身不触发退款或订单状态变更。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Data
public class SupportCloseRequest {

    /**
     * 关闭原因，可空，最长 200 字。
     */
    @Size(max = 200, message = "关闭原因不能超过 200 字")
    private String reason;
}
