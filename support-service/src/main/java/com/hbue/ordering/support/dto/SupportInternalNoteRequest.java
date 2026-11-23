package com.hbue.ordering.support.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端内部备注请求体。
 *
 * <p>内部备注只对管理端可见，不置学生未读、不发送通知、不更新最近回复时间，
 * 用于员工之间交接核实情况。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Data
public class SupportInternalNoteRequest {

    /**
     * 备注内容，去除首尾空白后要求 1-1000 字。
     */
    @NotBlank(message = "备注内容不能为空")
    private String content;
}
