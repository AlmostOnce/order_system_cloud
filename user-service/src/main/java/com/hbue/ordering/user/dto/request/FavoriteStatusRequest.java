package com.hbue.ordering.user.dto.request;

import com.hbue.ordering.user.model.FavoriteTargetType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 查询当前页面中一批窗口或菜品的收藏状态。
 *
 * <p>批量数量在请求边界限制为 100，避免把状态接口变成无界数据库查询；
 * 用户 ID 不放入请求体，而由控制器从已验证 JWT 获取。</p>
 *
 * @param targetType 窗口或菜品类型
 * @param targetIds 当前页面需要展示状态的目标 ID，最多 100 个
 */
public record FavoriteStatusRequest(
        @NotNull(message = "请选择收藏类型") FavoriteTargetType targetType,
        @NotEmpty(message = "请至少提交一个目标编号")
        @Size(max = 100, message = "单次最多查询100个收藏状态")
        List<@NotNull(message = "目标编号不能为空") @Positive(message = "目标编号必须为正数") Long> targetIds) {
}
