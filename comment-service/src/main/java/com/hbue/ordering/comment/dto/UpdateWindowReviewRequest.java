package com.hbue.ordering.comment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 店铺评价的修改参数。
 *
 * <p>与菜品评价一致，修改不允许更换所评价的订单与档口：这两项由原评价固定，
 * 保证"确实消费过"的校验结论不会因为编辑而失效。</p>
 *
 * <p>字段语义：{@code score} 必填；{@code content} 为 null 保持原文、空串表示清空；
 * {@code imageFileKeys} 与 {@code tags} 为 null 保持原值、空数组表示清空。</p>
 *
 * @param score         新评分，1 至 5 分
 * @param content       新正文，可空
 * @param imageFileKeys 新图片文件标识，可空
 * @param tags          新标签，可空
 * @author order-system
 * @date 2026-11-25
 */
public record UpdateWindowReviewRequest(
        @Min(1) @Max(5) int score,
        @Size(max = 1000) String content,
        // 仅保存文件服务标识；新增图片仍由文件服务校验归属，不接受客户端任意 URL。
        @Size(max = 3, message = "每份评价最多上传3张图片")
        List<@NotBlank @Pattern(regexp = "[a-fA-F0-9]{32}") String> imageFileKeys,
        @Size(max = 5) List<@NotBlank @Size(max = 20) String> tags
) {
}
