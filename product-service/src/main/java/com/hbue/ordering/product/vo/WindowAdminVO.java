package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 窗口管理接口返回对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowAdminVO {

    /** 窗口 ID。 */
    private Long windowId;

    /** 所属餐厅 ID。 */
    private Long restaurantId;

    /** 窗口编码。 */
    private String windowCode;

    /** 窗口名称。 */
    private String windowName;

    /** 窗口位置。 */
    private String location;

    /** 窗口图片地址。 */
    private String imageUrl;

    /** 窗口状态：1 启用，0 停用。 */
    private Integer status;

    /** 是否营业：1 营业，0 休息。 */
    private Integer isOpen;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 修改时间。 */
    private LocalDateTime updatedAt;
}
