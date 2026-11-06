package com.hbue.ordering.product.vo;

import lombok.Data;
import java.util.List;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 档口管理接口返回的视图对象。
 *
 * <p>包含管理端需要查看的启停和营业状态；操作者可见范围由 Service 层按餐厅/档口
 * 数据范围过滤，本类不承载持久化或授权逻辑。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
public class WindowAdminVO {

    /** 窗口 ID。 */
    private Long windowId;

    /** 所属餐厅 ID。 */
    private Long restaurantId;

    /** 窗口编码。 */
    private String windowCode;

    /** 窗口名称。 */
    private String windowName;

    /** 窗口简介。 */
    private String description;

    /** 窗口展示排序，越小越靠前。 */
    private Integer sortOrder;

    /** 窗口位置。 */
    private String location;

    /** 窗口图片地址。 */
    private String imageUrl;

    /** 管理端编辑及排序所需的文件键，不能用展示URL回填保存接口。 */
    private List<String> imageFileKeys;

    /** 与文件键一一对应的展示地址，最多5张。 */
    private List<String> imageUrls;

    /** 保留现有管理列表构造契约；图片列表由Service按同一窗口数据补齐。 */
    public WindowAdminVO(Long windowId, Long restaurantId, String windowCode, String windowName,
            String description, Integer sortOrder, String location, String imageUrl, Integer status,
            Integer isOpen, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.windowId = windowId; this.restaurantId = restaurantId; this.windowCode = windowCode;
        this.windowName = windowName; this.description = description; this.sortOrder = sortOrder;
        this.location = location; this.imageUrl = imageUrl; this.status = status; this.isOpen = isOpen;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    /** 窗口状态：1 启用，0 停用。 */
    private Integer status;

    /** 是否营业：1 营业，0 休息。 */
    private Integer isOpen;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 修改时间。 */
    private LocalDateTime updatedAt;
}
