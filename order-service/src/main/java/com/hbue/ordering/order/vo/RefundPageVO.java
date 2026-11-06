package com.hbue.ordering.order.vo;
import java.util.List;
/** 主键倒序游标分页，无全量COUNT；单次最多20条。 */
public record RefundPageVO(List<RefundVO> items,String nextCursor,boolean hasMore) {}
