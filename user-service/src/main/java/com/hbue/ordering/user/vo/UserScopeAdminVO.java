package com.hbue.ordering.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户数据范围管理视图对象。
 *
 * <p>用于范围授予接口的返回，携带 {@code scopeId} 供后续撤销单条范围使用。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserScopeAdminVO {

    /**
     * 范围记录 ID。
     */
    private Long scopeId;

    /**
     * 范围类型：WINDOW / RESTAURANT / PLATFORM。
     */
    private String scopeType;

    /**
     * 范围引用：WINDOW 为档口 ID、RESTAURANT 为餐厅 ID、PLATFORM 固定为 0。
     */
    private Long scopeRef;
}
