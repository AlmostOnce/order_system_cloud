package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可供顾客浏览的窗口摘要。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowSummaryVO {

    /** 窗口 ID。 */
    private Long windowId;

    /** 窗口编码。 */
    private String windowCode;

    /** 窗口名称。 */
    private String windowName;

    /** 窗口位置。 */
    private String location;
}
