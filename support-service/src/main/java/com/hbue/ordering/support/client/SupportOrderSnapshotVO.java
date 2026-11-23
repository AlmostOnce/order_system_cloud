package com.hbue.ordering.support.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 订单服务返回的订单快照最小投影。
 *
 * <p>反馈服务只读取关联校验需要的字段，订单接口的其它字段通过
 * {@link JsonIgnoreProperties} 忽略，避免订单服务增加字段时反序列化失败。
 * 该对象不落库，只用于构造工单快照。</p>
 *
 * @param orderId    订单编号
 * @param orderNo    订单号
 * @param windowId   窗口编号
 * @param windowName 窗口名称
 * @param status     订单状态，仅用于内部判断，不写入工单
 * @author order-system
 * @date 2026-11-23
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupportOrderSnapshotVO(
        Long orderId,
        String orderNo,
        Long windowId,
        String windowName,
        String status
) {
}
