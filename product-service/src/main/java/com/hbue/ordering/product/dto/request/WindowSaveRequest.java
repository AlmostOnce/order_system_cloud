package com.hbue.ordering.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 新建或更新档口的接口请求 DTO。
 *
 * <p>该对象承载可编辑字段，不接受窗口主键或数据库审计时间；除格式校验外，
 * Service 还会验证操作者同时有权管理目标档口及请求中的所属餐厅。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class WindowSaveRequest {

    /** 所属餐厅 ID；档口必须归属某个餐厅。 */
    @NotNull
    @Positive
    private Long restaurantId;

    /** 窗口编码。 */
    @NotBlank
    @Size(max = 32)
    private String windowCode;

    /** 窗口名称。 */
    @NotBlank
    @Size(max = 64)
    private String windowName;

    /** 窗口简介；新建可不填，编辑不传时保留原值，传空字符串清空。 */
    @Size(max = 500)
    private String description;

    /** 展示排序：越小越靠前；新建不传默认 0，编辑不传保留原值。 */
    @Min(0)
    private Integer sortOrder;

    /** 窗口位置；允许为空。 */
    @Size(max = 255)
    private String location;

    /** 窗口图片文件键；省略保留，空串清除。 */
    @Pattern(regexp = "(?:[a-fA-F0-9]{32})?")
    private String imageFileKey;

    /** 窗口状态：1 启用，0 停用。 */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;

    /** 是否营业：1 营业，0 休息；与启用状态相互独立。 */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isOpen;
}
