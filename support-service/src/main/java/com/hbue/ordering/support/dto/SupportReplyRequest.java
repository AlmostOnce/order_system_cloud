package com.hbue.ordering.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理端公开回复请求体。
 *
 * <p>公开回复进入学生可见时间线，并更新工单最近回复时间与未读标记；
 * 只有处理中的工单可以回复，已关闭工单拒绝。图片仍要求由当前操作员工上传。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Data
public class SupportReplyRequest {

    /**
     * 回复内容，去除首尾空白后要求 1-1000 字。
     */
    @NotBlank(message = "回复内容不能为空")
    private String content;

    /**
     * 图片文件键，最多 3 个。
     */
    @Size(max = 3, message = "最多上传 3 张图片")
    private List<String> imageFileKeys;
}
