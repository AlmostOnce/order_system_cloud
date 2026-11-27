package com.hbue.ordering.notification.dto;

import java.util.List;

/**
 * 通知偏好修改请求。
 *
 * <p>支持一次提交多个分类；同一分类重复出现时 Service 以最后一次为准。
 * enabled 使用包装类型是为了区分「客户端没有传值」和「显式关闭」，
 * 缺少开关状态时直接拒绝，避免误把未传值当成关闭。</p>
 *
 * @param preferences 待修改的分类开关列表，不能为空
 * @author order-system
 * @date 2026-11-24
 */
public record NotificationPreferenceUpdateRequest(List<Item> preferences) {

    /**
     * 单个分类的开关修改项。
     *
     * @param category 通知分类编码，必须是服务端已知分类
     * @param enabled  是否开启提醒
     */
    public record Item(String category, Boolean enabled) {
    }
}
