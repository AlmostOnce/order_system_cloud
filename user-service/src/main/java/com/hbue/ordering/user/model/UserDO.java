package com.hbue.ordering.user.model;

import com.baomidou.mybatisplus.annotation.*;
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
     * 登录凭据版本，密码恢复时递增，使旧会话失效。
     */
    @TableField("credential_version")
    private Long credentialVersion = 0L;

    /**
     * 用户 ID。
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /**
     * 永久登录用户号，由系统生成。
     */
    @TableField("user_no")
    private String userNo;

    /**
     * 对外稳定随机 UUID，内部关联仍使用 user_id。
     */
    @TableField("user_uuid")
    private String userUuid;

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
     * 当前绑定邮箱实际验证成功时间；历史账号为null。
     */
    private LocalDateTime emailVerifiedAt;

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
     * 头像文件键。
     */
    @TableField("avatar_file_key")
    private String avatarFileKey;

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

    /**
     * 账号注销申请提交时间。
     *
     * <p>为空表示当前没有待生效的注销申请。提交注销后到生效前，账号仍保留数据，
     * 但认证服务不再签发正常令牌；撤回或到期匿名化后由对应流程清理或保留用于审计。</p>
     */
    @TableField("deletion_requested_at")
    private LocalDateTime deletionRequestedAt;

    /**
     * 账号注销生效时间。
     *
     * <p>提交时按配置的冷静期计算（默认 72 小时）。到期任务依据该列选出待处理账号，
     * 并在同一事务内重新核对阻断项后才执行匿名化。</p>
     */
    @TableField("deletion_effective_at")
    private LocalDateTime deletionEffectiveAt;

    /**
     * 注销生效前提醒邮件的发送时间。
     *
     * <p>通过条件更新该列抢占提醒权，避免多实例重复发送；撤回后重置为 null。</p>
     */
    @TableField("deletion_reminded_at")
    private LocalDateTime deletionRemindedAt;
}