package com.hbue.ordering.notification.vo;
import java.util.List;
/**
 * 聊天游标分页响应。nextCursor只在hasMore为true时有值，客户端需继续补拉而不是假定首屏已包含全部消息。
 * @param items 本页记录，不超过请求的分页上限
 * @param nextCursor 有更多数据时为本页末条游标；消息为seq字符串，会话为UUID
 * @param hasMore 额外读取一条记录后判断是否还有下一页
 */
public record ChatPageVO<T>(List<T> items, String nextCursor, boolean hasMore) {
}
