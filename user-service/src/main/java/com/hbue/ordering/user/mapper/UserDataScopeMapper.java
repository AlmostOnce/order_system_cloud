package com.hbue.ordering.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.user.model.UserDataScopeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户数据范围数据访问接口。
 *
 * <p>负责访问 user_data_scopes 用户数据范围表。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Mapper
public interface UserDataScopeMapper extends BaseMapper<UserDataScopeDO> {

    /**
     * 查询指定用户持有的全部数据范围。
     *
     * @param userId 用户 ID
     * @return 数据范围列表
     */
    @Select("""
        SELECT
            scope_id,
            user_id,
            scope_type,
            scope_ref,
            created_at,
            updated_at
        FROM user_data_scopes
        WHERE user_id = #{userId}
        ORDER BY scope_id
        """)
    List<UserDataScopeDO> selectByUserId(@Param("userId") Long userId);
}
