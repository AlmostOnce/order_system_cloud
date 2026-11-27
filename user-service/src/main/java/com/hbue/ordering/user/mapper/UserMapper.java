package com.hbue.ordering.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.user.model.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 用户数据访问接口。
 *
 * <p>负责访问 users 用户表。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    @Select("SELECT user_id,email,credential_version FROM users WHERE email=#{email} AND status=1 AND deleted_at IS NULL")
    UserDO selectRecoveryByEmail(@Param("email") String email);

    /**
     * 锁定并读取密码更新所需的凭据行。
     *
     * <p>找回密码与登录态主动改密共用：两者都需要在同一事务内锁定当前密码哈希与
     * 凭据版本，确认没有并发改密后再按版本条件更新，避免后写覆盖前写。</p>
     */
    @Select("""
            SELECT user_id,email,password_hash,credential_version FROM users
            WHERE user_id=#{id} AND status=1 AND deleted_at IS NULL FOR UPDATE
            """)
    UserDO selectPasswordCredentialForUpdate(@Param("id") Long id);

    @Select("SELECT credential_version FROM users WHERE user_uuid=#{uuid} AND status=1 AND deleted_at IS NULL")
    Long selectCredentialVersion(@Param("uuid") String uuid);

    @Update("""
            UPDATE users SET password_hash=#{hash}, credential_version=credential_version+1,
                version=version+1, updated_at=NOW()
            WHERE user_id=#{id} AND email=#{email} AND credential_version=#{credentialVersion}
                AND status=1 AND deleted_at IS NULL
            """)
    int resetLoginPassword(@Param("id") Long id, @Param("email") String email,
                           @Param("credentialVersion") long credentialVersion, @Param("hash") String hash);

    /**
     * 内部头像批量查询，仅投影用户标识和文件键。
     */
    @Select("""
            <script>SELECT user_id, avatar_file_key FROM users
            WHERE status = 1 AND deleted_at IS NULL AND user_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    java.util.List<UserDO> selectAvatars(@Param("ids") java.util.List<Long> ids);

    /**
     * 串行化本人资料修改，避免并发换绑绕过对当前邮箱的核对。
     */
    @Select("SELECT user_id, username, email, password_hash, status, deleted_at FROM users WHERE user_id = #{userId} FOR UPDATE")
    UserDO selectProfileForUpdate(@Param("userId") Long userId);

    @Update("""
            UPDATE users SET username = #{username}, version = version + 1, updated_at = NOW()
            WHERE user_id = #{userId} AND status = 1 AND deleted_at IS NULL
            """)
    int updateOwnProfile(@Param("userId") Long userId, @Param("username") String username);

    @Update("""
            UPDATE users SET email = #{email}, email_verified_at = CURRENT_TIMESTAMP, version = version + 1, updated_at = NOW()
            WHERE user_id = #{userId} AND status = 1 AND deleted_at IS NULL
            """)
    int updateOwnEmail(@Param("userId") Long userId, @Param("email") String email);

    @Select("SELECT user_id FROM users WHERE user_uuid = #{userUuid} AND status = 1 AND deleted_at IS NULL")
    Long selectIdByUuid(@Param("userUuid") String userUuid);

    @Select("SELECT user_id FROM users WHERE user_uuid = #{userUuid} AND deleted_at IS NULL")
    Long selectExistingIdByUuid(@Param("userUuid") String userUuid);

    /**
     * 充值串行化同一用户的余额变动，锁内检查重复请求及余额上限。
     */
    @Select("""
            SELECT user_id, user_uuid, user_no, credential_version, email_verified_at, balance, pay_password_hash, status, deleted_at
            FROM users WHERE user_id = #{userId} AND deleted_at IS NULL FOR UPDATE
            """)
    UserDO selectBalanceForUpdate(@Param("userId") Long userId);

    /**
     * 退款重试锁定原账户，包括已停用或软删除账户，用于确认已提交的历史入账事实。
     * 此查询不授权新增入账；Service在幂等命中后才返回，否则仍拒绝停用/已删除账户。
     */
    @Select("SELECT user_id,balance,status,deleted_at FROM users WHERE user_id=#{userId} FOR UPDATE")
    UserDO selectRefundAccountForUpdate(@Param("userId") Long userId);

    /**
     * 充值与消费共用同一余额行；原子累加，不覆盖并发扣款结果。
     */
    @Update("""
            UPDATE users SET balance = balance + #{amount}, version = version + 1, updated_at = NOW()
            WHERE user_id = #{userId} AND status = 1 AND deleted_at IS NULL
              AND balance <= 99999999.99 - #{amount}
            """)
    int creditBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Select("SELECT user_id FROM users WHERE user_no = #{userNo}")
    Long selectIdByUserNo(@Param("userNo") String userNo);

    /**
     * 本人资料的字段白名单，不查询密码和支付凭据。
     */
    @Select("""
            SELECT user_id, user_uuid, user_no, credential_version, email_verified_at, username, role_code, email, avatar_file_key, created_at, status
            FROM users WHERE user_id = #{userId} AND status = 1 AND deleted_at IS NULL
            """)
    UserDO selectOwnProfile(@Param("userId") Long userId);

    /**
     * 查询正常且未删除的用户。
     *
     * @param userId 用户 ID
     * @param status 用户状态
     * @return 用户持久化对象
     */
    @Select("""
            SELECT
                user_id,
                user_uuid, user_no, credential_version, email_verified_at,
                username,
                role_code,
                email,
                balance,
                password_hash,
                avatar_file_key,
                status,
                version,
                created_at,
                updated_at,
                deleted_at,
                deletion_effective_at
            FROM users
            WHERE user_id = #{userId}
              AND status = #{status}
              AND deleted_at IS NULL
            """)
    UserDO selectAvailableById(
            @Param("userId") Long userId,
            @Param("status") Integer status
    );

    /**
     * 根据用户名或邮箱查询用户。
     *
     * <p>用于注册查重：只要用户名或邮箱任一命中即视为已注册。
     * 需要区分是哪个字段命中时，改用 {@link #selectByUsername(String)}
     * 与 {@link #selectByEmail(String)} 分别精确查询。</p>
     *
     * @param username 用户名
     * @param email    邮箱
     * @return 已存在的用户
     */
    @Select("""
            SELECT
                user_id,
                user_uuid, user_no, credential_version, email_verified_at,
                username,
                role_code,
                email,
                status,
                deleted_at
            FROM users
            WHERE username = #{username}
               OR email = #{email}
            LIMIT 1
            """)
    UserDO selectByUsernameOrEmail(
            @Param("username") String username,
            @Param("email") String email
    );

    /**
     * 按用户名精确查询用户。
     *
     * <p>引导流程先用它判断"目标用户名是否就是待引导账号"，与邮箱分开查询，
     * 避免 {@code username OR email} 的合并查询无法区分是用户名命中还是邮箱命中。</p>
     *
     * @param username 用户名
     * @return 用户持久化对象，不存在时返回 null
     */
    @Select("""
            SELECT
                user_id,
                user_uuid, user_no, credential_version, email_verified_at,
                username,
                role_code,
                email,
                status,
                deleted_at
            FROM users
            WHERE username = #{username}
            LIMIT 1
            """)
    UserDO selectByUsername(@Param("username") String username);

    /**
     * 为引导创建的管理员账号补写邮箱验证时间。
     *
     * <p>引导账号的邮箱来自部署配置，无法走用户端“收验证码证明邮箱归属”的流程。
     * 历史库中这类账号的 {@code email_verified_at} 可能为空，认证服务会一直返回
     * “需要验证邮箱”且不下发登录令牌，导致管理端无法登录。</p>
     *
     * <p>只在验证时间为空时写入，重复执行无副作用，也不会覆盖用户后续的真实验证时间；
     * 账号已删除时同样不处理，避免给无效账号补状态。</p>
     *
     * @param userId 用户主键
     * @return 受影响行数，0 表示账号不存在、已删除或已有验证时间
     */
    @Update("""
            UPDATE users
            SET email_verified_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
              AND deleted_at IS NULL
              AND email_verified_at IS NULL
            """)
    int markBootstrapEmailVerified(@Param("userId") Long userId);

    /**
     * 按邮箱精确查询用户。
     *
     * <p>引导流程用它判断邮箱是否已被其他账号占用：被占用时必须显式报错，
     * 不能让引导账号静默缺失（否则数据范围授予链的信任根建不出来却无人察觉）。</p>
     *
     * @param email 注册邮箱
     * @return 用户持久化对象，不存在时返回 null
     */
    @Select("""
            SELECT
                user_id,
                user_uuid, user_no, credential_version, email_verified_at,
                username,
                role_code,
                email,
                status,
                deleted_at
            FROM users
            WHERE email = #{email}
            LIMIT 1
            """)
    UserDO selectByEmail(@Param("email") String email);

    /**
     * 按用户 ID 查询未删除的用户（不限制状态）。
     *
     * <p>账号启用/禁用需要读到一个已停用账号才能把它重新启用，
     * 因此这里不能复用 {@link #selectAvailableById(Long, Integer)}（它只返回启用账号）。</p>
     *
     * @param userId 用户 ID
     * @return 用户持久化对象，不存在或已删除时返回 null
     */
    @Select("""
            SELECT
                user_id,
                user_uuid, user_no, credential_version, email_verified_at,
                username,
                role_code,
                email,
                status,
                deleted_at
            FROM users
            WHERE user_id = #{userId}
              AND deleted_at IS NULL
            """)
    UserDO selectNotDeletedById(@Param("userId") Long userId);

    /**
     * 按永久用户号或邮箱查询登录用户。
     * 用户号使用 ASCII 二进制排序，参数按字节比较以兼容含非 ASCII 字符的邮箱输入。
     *
     * @param account 用户号或邮箱
     * @return 用户持久化对象
     */
    @Select("""
            SELECT
                user_id,
                user_uuid, user_no, credential_version, email_verified_at,
                username,
                role_code,
                password_hash,
                status,
                deleted_at,
                deletion_effective_at
            FROM users
            WHERE (user_no = CAST(#{account} AS BINARY) OR email = #{account})
            LIMIT 1
            """)
    UserDO selectByAccountForLogin(
            @Param("account") String account
    );

    /**
     * 按用户 ID 查询未删除账号的余额与支付密码。
     *
     * <p>余额属于敏感信息，查询接口与扣款接口都用它读取当前余额，
     * 调用方必须自行校验账号状态。</p>
     *
     * @param userId 用户 ID
     * @return 用户持久化对象，不存在或已删除时返回 null
     */
    @Select("""
            SELECT
                user_id,
                user_uuid, user_no, credential_version, email_verified_at,
                balance,
                pay_password_hash,
                status,
                deleted_at
            FROM users
            WHERE user_id = #{userId}
              AND deleted_at IS NULL
            """)
    UserDO selectBalanceById(@Param("userId") Long userId);

    /**
     * 按用户 ID 查询未删除账号的登录密码与支付密码哈希。
     *
     * <p>仅供设置/修改支付密码时校验凭据使用，返回值绝不对外暴露。</p>
     *
     * @param userId 用户 ID
     * @return 用户持久化对象，不存在或已删除时返回 null
     */
    @Select("""
            SELECT
                user_id,
                user_uuid, user_no, credential_version, email_verified_at,
                password_hash,
                pay_password_hash,
                status,
                deleted_at
            FROM users
            WHERE user_id = #{userId}
              AND deleted_at IS NULL
            """)
    UserDO selectPasswordsById(@Param("userId") Long userId);

    /**
     * 条件扣减余额。
     *
     * <p>把"余额充足"写进 WHERE 条件并由数据库原子求值，防止并发下超扣；
     * 返回 0 表示余额不足或账号不可用，调用方需回查区分。</p>
     *
     * @param userId 用户 ID
     * @param amount 扣款金额，必须为正数
     * @return 受影响行数，1 表示扣款成功
     */
    @Update("""
            UPDATE users
            SET balance = balance - #{amount},
                version = version + 1,
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND status = 1
              AND deleted_at IS NULL
              AND balance >= #{amount}
            """)
    int deductBalance(
            @Param("userId") Long userId,
            @Param("amount") BigDecimal amount
    );

    /**
     * 设置或修改支付密码哈希。
     *
     * @param userId          用户 ID
     * @param payPasswordHash 支付密码哈希
     * @return 受影响行数，1 表示写入成功
     */
    @Update("""
            UPDATE users
            SET pay_password_hash = #{payPasswordHash},
                version = version + 1,
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND deleted_at IS NULL
            """)
    int updatePayPassword(
            @Param("userId") Long userId,
            @Param("payPasswordHash") String payPasswordHash
    );

    @Select("SELECT user_id,email,password_hash,pay_password_hash,credential_version,status,deleted_at FROM users WHERE user_id=#{id}")
    UserDO selectPayPasswordRecovery(@Param("id") Long id);

    @Select("SELECT user_id,email,password_hash,pay_password_hash,credential_version,status,deleted_at FROM users WHERE user_id=#{id} FOR UPDATE")
    UserDO selectPayPasswordRecoveryForUpdate(@Param("id") Long id);

    @Select("SELECT (email_verified_at IS NOT NULL) FROM users WHERE user_uuid=#{uuid} AND status=1 AND deleted_at IS NULL")
    Boolean selectEmailVerified(@Param("uuid") String uuid);

    @Select("SELECT user_id,email,email_verified_at,password_hash,credential_version,role_code,status,deleted_at FROM users WHERE user_id=#{id}")
    UserDO selectEmailEnrollment(@Param("id") Long id);

    @Select("SELECT user_id,email,email_verified_at,password_hash,credential_version,role_code,status,deleted_at FROM users WHERE user_id=#{id} FOR UPDATE")
    UserDO selectEmailEnrollmentForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE users SET email=#{email},email_verified_at=CURRENT_TIMESTAMP,
              credential_version=credential_version+1,version=version+1,updated_at=CURRENT_TIMESTAMP
            WHERE user_id=#{id} AND email_verified_at IS NULL AND status=1 AND deleted_at IS NULL
            """)
    int completeEmailEnrollment(@Param("id") Long id, @Param("email") String email);

    /**
     * 读取账号注销资格判断所需字段（不加锁）。
     *
     * <p>用于资格查询、发送验证码和签发撤回令牌等只读场景。包含邮箱、余额与注销三字段，
     * 但绝不返回给客户端；真正提交注销和到期处理必须改用
     * {@link #selectAccountDeletionStateForUpdate(Long)} 串行化并发。</p>
     *
     * @param userId 用户 ID
     * @return 未软删除用户；不存在时返回 null
     */
    @Select("""
            SELECT
                user_id, user_uuid, user_no, email, email_verified_at,
                balance, role_code, status, version, credential_version,
                deleted_at, deletion_requested_at, deletion_effective_at, deletion_reminded_at
            FROM users
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    UserDO selectAccountDeletionState(@Param("userId") Long userId);

    /**
     * 锁定并读取账号注销状态。
     *
     * <p>提交、撤回和到期匿名化都在同一行锁内完成：先确认没有并发的注销/撤回操作，
     * 再按条件更新，避免两个请求互相覆盖冷静期或把已匿名化账号再次处理。</p>
     *
     * @param userId 用户 ID
     * @return 未软删除用户；不存在时返回 null
     */
    @Select("""
            SELECT
                user_id, user_uuid, user_no, email, email_verified_at,
                balance, role_code, status, version, credential_version,
                deleted_at, deletion_requested_at, deletion_effective_at, deletion_reminded_at
            FROM users
            WHERE user_id = #{userId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    UserDO selectAccountDeletionStateForUpdate(@Param("userId") Long userId);

    /**
     * 写入注销申请并递增凭据版本。
     *
     * <p>仅当账号仍启用且没有待生效申请时更新；credential_version 递增使刷新令牌
     * 无法继续续期，提交请求携带的访问令牌由服务层同步写入黑名单。并发提交只有第一个
     * 请求能写入，其余请求由调用方回读已存在的生效时间并返回相同结果。</p>
     *
     * @param userId      用户 ID
     * @param requestedAt 申请时间
     * @param effectiveAt 生效时间
     * @return 受影响行数，1 表示本次写入成功
     */
    @Update("""
            UPDATE users
            SET deletion_requested_at = #{requestedAt},
                deletion_effective_at = #{effectiveAt},
                deletion_reminded_at = NULL,
                credential_version = credential_version + 1,
                version = version + 1,
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND status = 1
              AND deleted_at IS NULL
              AND deletion_effective_at IS NULL
            """)
    int applyAccountDeletion(
            @Param("userId") Long userId,
            @Param("requestedAt") java.time.LocalDateTime requestedAt,
            @Param("effectiveAt") java.time.LocalDateTime effectiveAt
    );

    /**
     * 撤回待生效的注销申请。
     *
     * <p>只在申请仍存在且未软删除时清空注销字段；已到期的账号由调用方先拒绝撤回，
     * 避免与到期匿名化竞争时把将要删除的账号恢复为正常状态。</p>
     *
     * @param userId 用户 ID
     * @return 受影响行数，1 表示撤回成功
     */
    @Update("""
            UPDATE users
            SET deletion_requested_at = NULL,
                deletion_effective_at = NULL,
                deletion_reminded_at = NULL,
                version = version + 1,
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND deleted_at IS NULL
              AND deletion_effective_at IS NOT NULL
            """)
    int clearAccountDeletion(@Param("userId") Long userId);

    /**
     * 查询已到生效时间、尚未匿名化的注销账号。
     *
     * <p>使用 deletion_effective_at 单列索引并按时间升序，单批最多返回 limit 条，
     * 让到期任务的处理量有界；已软删除账号不再重复返回。</p>
     *
     * @param now   当前时间
     * @param limit 单批最大数量
     * @return 用户 ID 列表
     */
    @Select("""
            SELECT user_id FROM users
            WHERE deletion_effective_at IS NOT NULL
              AND deletion_effective_at <= #{now}
              AND deleted_at IS NULL
            ORDER BY deletion_effective_at
            LIMIT #{limit}
            """)
    java.util.List<Long> selectDueDeletionIds(
            @Param("now") java.time.LocalDateTime now,
            @Param("limit") int limit
    );

    /**
     * 查询即将在提醒窗口内生效、尚未提醒过的注销账号。
     *
     * <p>提醒窗口由调用方按“生效时间减提前量”计算；deletion_reminded_at 为空避免重复提醒。</p>
     *
     * @param now          当前时间
     * @param remindBefore 提醒窗口上界
     * @param limit        单批最大数量
     * @return 用户 ID 列表
     */
    @Select("""
            SELECT user_id FROM users
            WHERE deletion_effective_at IS NOT NULL
              AND deletion_effective_at > #{now}
              AND deletion_effective_at <= #{remindBefore}
              AND deletion_reminded_at IS NULL
              AND deleted_at IS NULL
            ORDER BY deletion_effective_at
            LIMIT #{limit}
            """)
    java.util.List<Long> selectDeletionReminderIds(
            @Param("now") java.time.LocalDateTime now,
            @Param("remindBefore") java.time.LocalDateTime remindBefore,
            @Param("limit") int limit
    );

    /**
     * 抢占注销提醒权。
     *
     * <p>条件更新 deletion_reminded_at 为空的行；多实例同时扫描时只有更新成功的实例
     * 发送邮件，失败实例直接跳过，不做重试抢占。</p>
     *
     * @param userId     用户 ID
     * @param remindedAt 提醒发送时间
     * @return 受影响行数，1 表示当前实例获得提醒权
     */
    @Update("""
            UPDATE users
            SET deletion_reminded_at = #{remindedAt}, version = version + 1, updated_at = NOW()
            WHERE user_id = #{userId}
              AND deletion_reminded_at IS NULL
              AND deletion_effective_at IS NOT NULL
              AND deleted_at IS NULL
            """)
    int markDeletionReminded(
            @Param("userId") Long userId,
            @Param("remindedAt") java.time.LocalDateTime remindedAt
    );

    /**
     * 注销生效：账号匿名化并停止使用。
     *
     * <p>邮箱与邮箱验证时间释放为 NULL，以便本人日后用同一邮箱重新注册；用户名替换为
     * 不可识别的占位值；密码替换为随机哈希，防止旧密码再次匹配。user_no 与 user_uuid
     * 保留不复用，订单、退款、余额流水和工单仍按原主键关联。status=0 与 deleted_at
     * 同时写入，使登录、令牌刷新和用户查询统一拒绝该账号。</p>
     *
     * @param userId       用户 ID
     * @param username     匿名化后的用户名
     * @param passwordHash 随机生成的密码哈希
     * @param deletedAt    注销完成时间
     * @return 受影响行数，1 表示匿名化成功
     */
    @Update("""
            UPDATE users
            SET username = #{username},
                email = NULL,
                email_verified_at = NULL,
                avatar_file_key = NULL,
                password_hash = #{passwordHash},
                balance = 0,
                status = 0,
                deleted_at = #{deletedAt},
                credential_version = credential_version + 1,
                version = version + 1,
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND status = 1
              AND deleted_at IS NULL
            """)
    int anonymizeAccount(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("deletedAt") java.time.LocalDateTime deletedAt
    );
}
