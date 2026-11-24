package com.hbue.ordering.notification.mapper;

import com.hbue.ordering.notification.model.NotificationPreferenceDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户通知偏好数据访问。
 *
 * <p>所有语句都由 Service 传入已认证用户编号，不提供按客户端参数跨用户读写的入口。
 * 表以 (user_id, category) 为主键，更新使用 upsert，保证重复保存同一开关得到相同结果。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Mapper
public interface NotificationPreferenceMapper {

    /**
     * 查询指定用户已经显式保存的偏好记录。
     *
     * <p>没有记录的分类使用默认开启，由 Service 合并；这里只返回实际存在的行。</p>
     *
     * @param userId 用户内部编号
     * @return 该用户的偏好记录，可能为空列表
     */
    @Select("""
            SELECT user_id, category, enabled, updated_at
            FROM notification_preferences
            WHERE user_id = #{userId}
            """)
    List<NotificationPreferenceDO> selectByUser(@Param("userId") long userId);

    /**
     * 插入或更新一个分类的开关。
     *
     * <p>主键冲突时只改写 enabled 与更新时间，不产生重复行；重复提交同一状态是幂等的。
     * 业务值全部通过参数绑定，并用 #{enabled} 再次引用同一参数，避免拼接 SQL。</p>
     *
     * @param userId   用户内部编号
     * @param category 通知分类编码
     * @param enabled  是否开启提醒
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO notification_preferences(user_id, category, enabled)
            VALUES(#{userId}, #{category}, #{enabled})
            ON DUPLICATE KEY UPDATE enabled = #{enabled}, updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(@Param("userId") long userId, @Param("category") String category,
               @Param("enabled") boolean enabled);

    /**
     * 判断指定分类是否被显式关闭。
     *
     * <p>返回 0 表示没有记录或记录为开启；调用方据此按默认开启处理。
     * 使用主键 (user_id, category) 精确定位，只读取一行的计数。</p>
     *
     * @param userId   用户内部编号
     * @param category 通知分类编码
     * @return 关闭状态的记录数，只可能为 0 或 1
     */
    @Select("""
            SELECT COUNT(*)
            FROM notification_preferences
            WHERE user_id = #{userId} AND category = #{category} AND enabled = 0
            """)
    int countDisabled(@Param("userId") long userId, @Param("category") String category);
}
