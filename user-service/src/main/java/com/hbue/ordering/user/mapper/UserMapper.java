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
    /** 内部头像批量查询，仅投影用户标识和文件键。 */
    @Select("""
        <script>SELECT user_id, avatar_file_key FROM users
        WHERE status = 1 AND deleted_at IS NULL AND user_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    java.util.List<UserDO> selectAvatars(@Param("ids") java.util.List<Long> ids);

    /** 串行化本人资料修改，避免并发换绑绕过对当前邮箱的核对。 */
    @Select("SELECT user_id, username, email, password_hash, status, deleted_at FROM users WHERE user_id = #{userId} FOR UPDATE")
    UserDO selectProfileForUpdate(@Param("userId") Long userId);

    @Update("""
        UPDATE users SET username = #{username}, version = version + 1, updated_at = NOW()
        WHERE user_id = #{userId} AND status = 1 AND deleted_at IS NULL
        """)
    int updateOwnProfile(@Param("userId") Long userId, @Param("username") String username);

    @Update("""
        UPDATE users SET email = #{email}, version = version + 1, updated_at = NOW()
        WHERE user_id = #{userId} AND status = 1 AND deleted_at IS NULL
        """)
    int updateOwnEmail(@Param("userId") Long userId, @Param("email") String email);

    @Select("SELECT user_id FROM users WHERE user_uuid = #{userUuid} AND status = 1 AND deleted_at IS NULL")
    Long selectIdByUuid(@Param("userUuid") String userUuid);

    @Select("SELECT user_id FROM users WHERE user_uuid = #{userUuid} AND deleted_at IS NULL")
    Long selectExistingIdByUuid(@Param("userUuid") String userUuid);

    /** 充值串行化同一用户的余额变动，锁内检查重复请求及余额上限。 */
    @Select("""
        SELECT user_id, user_uuid, user_no, balance, pay_password_hash, status, deleted_at
        FROM users WHERE user_id = #{userId} AND deleted_at IS NULL FOR UPDATE
        """)
    UserDO selectBalanceForUpdate(@Param("userId") Long userId);

    /** 充值与消费共用同一余额行；原子累加，不覆盖并发扣款结果。 */
    @Update("""
        UPDATE users SET balance = balance + #{amount}, version = version + 1, updated_at = NOW()
        WHERE user_id = #{userId} AND status = 1 AND deleted_at IS NULL
          AND balance <= 99999999.99 - #{amount}
        """)
    int creditBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Select("SELECT user_id FROM users WHERE user_no = #{userNo}")
    Long selectIdByUserNo(@Param("userNo") String userNo);

    /** 本人资料的字段白名单，不查询密码和支付凭据。 */
    @Select("""
        SELECT user_id, user_uuid, user_no, username, role_code, email, avatar_file_key, created_at, status
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
            user_uuid, user_no,
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
            deleted_at
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
     * @param email 邮箱
     * @return 已存在的用户
     */
    @Select("""
        SELECT
            user_id,
            user_uuid, user_no,
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
            user_uuid, user_no,
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
            user_uuid, user_no,
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
            user_uuid, user_no,
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
        user_uuid, user_no,
        username,
        role_code,
        password_hash,
        status,
        deleted_at
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
            user_uuid, user_no,
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
            user_uuid, user_no,
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
     * @param userId 用户 ID
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
}
