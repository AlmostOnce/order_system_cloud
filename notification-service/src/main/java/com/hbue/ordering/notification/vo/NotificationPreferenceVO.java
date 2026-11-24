package com.hbue.ordering.notification.vo;

/**
 * 对外通知偏好视图。
 *
 * <p>只暴露分类编码、展示文案和开关状态，不包含内部用户编号与数据库时间，
 * 客户端可直接按返回顺序渲染设置页。</p>
 *
 * @param category    通知分类编码，提交修改时原样回传
 * @param label       分类展示名称
 * @param description 分类说明文案
 * @param enabled     是否开启提醒
 * @author order-system
 * @date 2026-11-24
 */
public record NotificationPreferenceVO(String category, String label, String description, boolean enabled) {
}
