package com.hbue.ordering.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 覆盖式设置用户数据范围的请求。
 *
 * <p>该请求只描述目标用户最终应持有的数据范围，只写
 * {@code user_data_scopes}，不修改 {@code users.role_code}；角色变更走独立接口，
 * 避免一次操作同时改变身份与数据边界。请求为空列表或 {@code null} 时按清空处理，
 * 具体范围是否存在及是否落在操作者权限内由业务服务校验。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
public class UserScopeSaveRequest {

    /**
     * 新的范围列表；为空或 null 表示清空该用户的全部范围。
     */
    @Valid
    private List<ScopeItem> scopes;

    /**
     * 请求中的单条数据范围条目。
     *
     * <p>{@code scopeType} 决定 {@code scopeRef} 的含义；PLATFORM 的引用值由
     * 服务端规范化，不应把客户端提供的范围视为已授权事实。</p>
     */
    @Data
    public static class ScopeItem {

        /**
         * 范围类型：WINDOW / RESTAURANT / PLATFORM。
         */
        @NotBlank(message = "范围类型不能为空")
        private String scopeType;

        /**
         * 范围引用：WINDOW 为档口 ID、RESTAURANT 为餐厅 ID、
         * PLATFORM 可省略（服务端统一规范化为 0）。
         */
        private Long scopeRef;
    }
}
