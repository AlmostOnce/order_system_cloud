package com.hbue.ordering.comment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 菜品评价的修改参数。
 *
 * <p>修改不允许更换评价对象：订单号、菜品号由原评价固定，既不接收也不校验，
 * 避免把一条已核验过消费资格的评价改挂到别的订单上。</p>
 *
 * <p>字段为空表示"不修改该维度"还是"清空"必须明确，这里的约定是：</p>
 * <ul>
 *   <li>{@code score} 必填，避免出现"只想改文字却把评分清零"的歧义；</li>
 *   <li>{@code content} 为 null 保持原文，传空串表示清空正文；</li>
 *   <li>{@code imageFileKeys} / {@code tags} 为 null 保持原值，传空数组表示清空。</li>
 * </ul>
 *
 * @param score         新评分，1 至 5 星
 * @param content       新正文，可空
 * @param imageFileKeys 新图片文件标识，可空
 * @param tags          新标签，可空
 * @author order-system
 * @date 2026-11-25
 */
public record UpdateCommentRequest(
        @Min(1) @Max(5) int score,
        @Size(max = 1000) String content,
        // 仅保存文件服务标识；新增图片仍由文件服务校验归属，不接受客户端任意 URL。
        @Size(max = 3, message = "每份评价最多上传3张图片")
        List<@NotBlank @Pattern(regexp = "[a-fA-F0-9]{32}") String> imageFileKeys,
        @Size(max = 5) List<@NotBlank @Size(max = 20) String> tags
) {
}
