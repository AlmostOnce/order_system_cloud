package com.hbue.ordering.user.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户数据范围持久化对象。
 *
 * <p>该对象用于映射 user_data_scopes 表，表达"用户能操作哪些数据"。
 * 角色（{@code users.role_code}）表达"能执行哪类操作"，二者相互独立、
 * 必须同时校验，不允许直接用本对象作为接口返回对象。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
@TableName("user_data_scopes")
public class UserDataScopeDO {

    /**
     * 范围记录 ID。
     */
    @TableId(value = "scope_id", type = IdType.AUTO)
    private Long scopeId;

    /**
     * 用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 范围类型：WINDOW / RESTAURANT / PLATFORM。
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 范围引用：WINDOW 为 window_id、RESTAURANT 为 restaurant_id、PLATFORM 固定为 0。
     */
    @TableField("scope_ref")
    private Long scopeRef;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
