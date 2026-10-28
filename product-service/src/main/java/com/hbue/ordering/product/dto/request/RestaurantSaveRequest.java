package com.hbue.ordering.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 新建或更新餐厅的请求对象。
 *
 * <p>{@code restaurantCode} 与 {@code sortOrder} 属平台口径：餐厅经理提交的请求
 * 必须与原值一致，否则返回 403；{@code sortOrder} 允许留空，表示保持原值。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
public class RestaurantSaveRequest {

    /** 餐厅编码。 */
    @NotBlank
    @Size(max = 32)
    private String restaurantCode;

    /** 餐厅名称。 */
    @NotBlank
    @Size(max = 64)
    private String restaurantName;

    /** 位置/楼层。 */
    @NotBlank
    @Size(max = 64)
    private String location;

    /** 联系电话；允许为空。 */
    @Size(max = 20)
    private String phone;

    /** 门面图文件键；省略保留，空串清除。 */
    @Pattern(regexp = "(?:[a-fA-F0-9]{32})?")
    private String imageFileKey;

    /** 公告/简介；允许为空。 */
    @Size(max = 255)
    private String notice;

    /** 展示排序；留空表示保持原值。 */
    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
