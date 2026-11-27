package com.hbue.ordering.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 学生补充说明请求体。
 *
 * <p>补充说明只追加到已存在工单的时间线，不改变工单状态；工单关闭后拒绝写入。
 * 请求头必须携带幂等键，同键重放返回首次写入的消息。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Data
public class AddSupportMessageRequest {

    /**
     * 补充内容，去除首尾空白后要求 1-1000 字。
     */
    @NotBlank(message = "补充内容不能为空")
    private String content;

    /**
     * 图片文件键，最多 3 个；校验规则与提交工单一致。
     */
    @Size(max = 3, message = "最多上传 3 张图片")
    private List<String> imageFileKeys;
}
