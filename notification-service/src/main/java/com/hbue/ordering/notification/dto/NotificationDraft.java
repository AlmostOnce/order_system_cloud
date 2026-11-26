package com.hbue.ordering.notification.dto;

import com.hbue.ordering.notification.model.NotificationCategory;

/**
 * 一条待写入的站内信草稿。
 *
 * <p>各业务服务（反馈、优惠券等）通过内部接口转成该对象后交给统一的写入器，
 * 由写入器负责长度截断、幂等落库、偏好判断与提交后实时提示。业务侧只描述
 * "发给谁、什么类型、说什么"，不重复实现这些横切规则。</p>
 *
 * <p>{@code fallbackTitle} 与 {@code fallbackContent} 在正文为空时兜底，
 * 保证通知记录永远有可读内容，而不是一条空白消息。</p>
 *
 * @param recipientId     接收用户内部编号，必须为正数
 * @param eventId         来源业务事件标识，与接收人共同构成幂等唯一键
 * @param type            通知类型编码，写入 notification_type，长度不超过 32
 * @param title           通知标题，为空时使用 fallbackTitle
 * @param content         通知正文，为空时使用 fallbackContent
 * @param fallbackTitle   标题兜底文案
 * @param fallbackContent 正文兜底文案
 * @param bizType         关联业务类型，写入 biz_type，供消息中心识别跳转目标
 * @param bizId           关联业务公开标识，可空；禁止传内部敏感数据
 * @param category        通知偏好分类，决定是否推送实时提示与计入未读角标
 * @author order-system
 * @date 2026-11-25
 */
public record NotificationDraft(
        Long recipientId,
        String eventId,
        String type,
        String title,
        String content,
        String fallbackTitle,
        String fallbackContent,
        String bizType,
        String bizId,
        NotificationCategory category
) {
}
