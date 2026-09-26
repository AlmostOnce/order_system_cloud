package com.hbue.ordering.user.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户持久化对象。
 *
 * <p>该对象用于映射 users 数据库表，
 * 不允许直接作为接口返回对象。</p>
 *
 * @author order-system
 * @date 2026-09-19
 */
@Data
@TableName("users")
public class UserDO {

    /**
     * 用户 ID。
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /**
     * 用户名。
     */
    @TableField("username")
    private String username;

    /**
     * 角色编码。
     */
    @TableField("role_code")
    private String roleCode;

    /**
     * 手机号。
     */
    @TableField("phone")
    private String phone;

    /**
     * 账户余额。
     */
    @TableField("balance")
    private BigDecimal balance;

    /**
     * 密码哈希值。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 头像地址。
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 用户状态。
     */
    @TableField("status")
    private Integer status;

    /**
     * 乐观锁版本号。
     */
    @Version
    @TableField("version")
    private Integer version;

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

    /**
     * 删除时间。
     */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}