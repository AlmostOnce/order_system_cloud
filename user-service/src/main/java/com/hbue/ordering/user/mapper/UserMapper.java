package com.hbue.ordering.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.user.model.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
            username,
            role_code,
            phone,
            balance,
            password_hash,
            avatar_url,
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
     * 根据用户名或手机号查询用户。
     *
     * @param username 用户名
     * @param phone 手机号
     * @return 已存在的用户
     */
    @Select("""
        SELECT
            user_id,
            username,
            phone
        FROM users
        WHERE username = #{username}
           OR phone = #{phone}
        LIMIT 1
        """)
    UserDO selectByUsernameOrPhone(
            @Param("username") String username,
            @Param("phone") String phone
    );



    /**
     * 根据用户名查询登录用户。
     *
     * @param username 用户名
     * @return 用户持久化对象
     */
    @Select("""
    SELECT
        user_id,
        username,
        role_code,
        password_hash,
        status,
        deleted_at
    FROM users
    WHERE username = #{username}
    LIMIT 1
    """)
    UserDO selectByUsernameForLogin(
            @Param("username") String username
    );
}