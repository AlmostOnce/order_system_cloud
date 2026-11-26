package com.hbue.ordering.comment.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 菜品评价创建参数；图片可不传，每份独立最多三张，由Bean Validation在进入业务前拒绝超额请求。
 *
 * @param orderId      已完成的订单编号
 * @param productId    本单实际购买的菜品编号
 * @param score        评分，1 至 5 星
 * @param content      正文，可空
 * @param imageFileKeys 图片文件标识，可空
 * @param tags         评价标签，可空
 * @param anonymous    是否匿名展示；null 与 false 都按实名处理，保持旧客户端兼容，
 *                     该值参与幂等摘要，同一请求键不能在中途改变匿名意图
 */
public record CreateCommentRequest(@NotNull @Positive Long orderId, @NotNull @Positive Long productId,
                                   @Min(1) @Max(5) int score,
                                   @Size(max = 1000) String content,
                                   // 仅保存文件服务标识；图片归属及格式仍由既有服务校验，不接受客户端任意URL。
                                   @Size(max = 3, message = "每份评价最多上传3张图片") List<@NotBlank @Pattern(regexp = "[a-fA-F0-9]{32}") String> imageFileKeys,
                                   @Size(max = 5) List<@NotBlank @Size(max = 20) String> tags,
                                   Boolean anonymous) {
}
