package com.hbue.ordering.notification.service;

import com.hbue.ordering.notification.dto.NotificationDraft;

/**
 * 站内信统一写入器。
 *
 * <p>反馈回复、工单状态、优惠券到期等所有"系统主动下发的站内信"共用同一条写入路径：
 * 参数校验与截断 → 幂等落库 → 按偏好决定是否实时提示 → 事务提交后推送。</p>
 *
 * <p>拆成独立组件而不是各业务各写一份，是为了保证幂等键、长度上限、偏好语义在所有类型上
 * 完全一致；新增通知类型只需要提供一份草稿，不能绕开这些约束直接写通知表。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
public interface SystemNotificationWriter {

    /**
     * 写入一条站内信，并在事务提交后按偏好推送在线提示。
     *
     * <p>调用方必须已经处于事务中：实时提示注册在 {@code afterCommit}，没有事务就不会触发；
     * 重复事件（同接收人同事件标识）按幂等成功处理，不会产生第二条通知。</p>
     *
     * @param draft 站内信草稿
     * @throws com.hbue.ordering.common.core.exception.BusinessException 接收人、事件标识或业务标识非法时抛出
     */
    void write(NotificationDraft draft);
}
