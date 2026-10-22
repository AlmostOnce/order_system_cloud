package com.hbue.ordering.user.vo;

import java.util.List;

/** 按流水 ID 倒序游标翻页，避免翻页期间新增记录造成重复或漏项。 */
public record BalanceRecordPageVO(List<BalanceRecordVO> items, Long nextCursor, boolean hasMore) {
}
