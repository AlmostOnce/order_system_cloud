package com.hbue.ordering.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.user.model.UserFavoriteDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户收藏关系的数据访问接口。
 *
 * <p>关系唯一性由数据库唯一键最终保障；新增使用普通 INSERT，
 * 重复键交由服务层处理，避免驱动影响行数配置将重复收藏误判为新关系。</p>
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavoriteDO> {

    /**
     * 创建收藏；同一用户重复收藏同一个类型和目标时保持原记录不变。
     *
     * @param userId 当前用户内部 ID
     * @param targetType 收藏目标类型
     * @param targetId 窗口或菜品 ID
     * @return 新增关系的数据库影响行数；重复键由调用方转为幂等成功
     */
    @Insert("""
        INSERT INTO user_favorites (user_id, target_type, target_id)
        VALUES (#{userId}, #{targetType}, #{targetId})
        """)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId);

    /**
     * 按收藏主键游标读取一页关系；调用方传入的 limit 已由服务层限定在 2 至 51。
     *
     * @param userId 当前用户内部 ID
     * @param targetType 收藏目标类型
     * @param beforeId 可选的上一页末条收藏 ID
     * @param limit 当前页大小加一，用于判断后面是否还有记录
     * @return 按收藏 ID 从新到旧排列的关系行
     */
    @Select("""
        <script>
        SELECT favorite_id, user_id, target_type, target_id, created_at
        FROM user_favorites
        WHERE user_id = #{userId}
          AND target_type = #{targetType}
        <if test="beforeId != null">
          AND favorite_id &lt; #{beforeId}
        </if>
        ORDER BY favorite_id DESC
        LIMIT #{limit}
        </script>
        """)
    List<UserFavoriteDO> selectPage(@Param("userId") Long userId,
                                    @Param("targetType") String targetType,
                                    @Param("beforeId") Long beforeId,
                                    @Param("limit") int limit);

    /**
     * 批量返回当前用户已收藏的目标 ID；输入列表必须在服务层限制为最多 100 项。
     *
     * @param userId 当前用户内部 ID
     * @param targetType 收藏目标类型
     * @param targetIds 当前页面可见的目标 ID
     * @return 已收藏的目标 ID 集合
     */
    @Select("""
        <script>
        SELECT target_id
        FROM user_favorites
        WHERE user_id = #{userId}
          AND target_type = #{targetType}
          AND target_id IN
          <foreach collection="targetIds" item="targetId" open="(" separator="," close=")">
            #{targetId}
          </foreach>
        </script>
        """)
    List<Long> selectFavoriteTargetIds(@Param("userId") Long userId,
                                       @Param("targetType") String targetType,
                                       @Param("targetIds") List<Long> targetIds);

    /**
     * 删除本人指定目标的收藏；不存在时影响行数为零，由服务层保持幂等成功语义。
     *
     * @param userId 当前用户内部 ID
     * @param targetType 收藏目标类型
     * @param targetId 窗口或菜品 ID
     * @return 删除行数
     */
    @Delete("""
        DELETE FROM user_favorites
        WHERE user_id = #{userId}
          AND target_type = #{targetType}
          AND target_id = #{targetId}
        """)
    int deleteFavorite(@Param("userId") Long userId,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId);
}
