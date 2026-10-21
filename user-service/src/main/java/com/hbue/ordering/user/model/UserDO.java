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
     * 注册邮箱。
     *
     * <p>用作账号标识、邮件验证码与找回密码凭据。历史账号可能为空（为 null 表示
     * 该账号创建于改用邮箱之前），MySQL 唯一索引对多个 NULL 不做冲突判定。</p>
     */
    @TableField("email")
    private String email;

    /**
     * 手机号。
     *
     * <p>注册流程已改用邮箱，该字段仅为兼容既有数据保留，新注册不再写入。</p>
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
     * 支付密码哈希值。
     *
     * <p>与登录密码完全分离：账号被盗时，登录态被窃取不应直接导致余额被花掉，
     * 余额支付需要第二道凭据。为 null 表示尚未设置支付密码，未设置前禁止使用余额。
     * 该值不参与 JWT，只在余额相关接口的请求体里传入，用后即弃。</p>
     */
    @TableField("pay_password_hash")
    private String payPasswordHash;

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