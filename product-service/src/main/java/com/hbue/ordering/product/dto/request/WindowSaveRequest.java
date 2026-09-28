package com.hbue.ordering.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建或更新窗口的请求对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class WindowSaveRequest {

    /** 窗口编码。 */
    @NotBlank
    @Size(max = 32)
    private String windowCode;

    /** 窗口名称。 */
    @NotBlank
    @Size(max = 64)
    private String windowName;

    /** 窗口位置；允许为空。 */
    @Size(max = 255)
    private String location;

    /** 窗口状态：1 启用，0 停用。 */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
