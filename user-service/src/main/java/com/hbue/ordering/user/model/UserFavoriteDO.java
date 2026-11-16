package com.hbue.ordering.user.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收藏关系的 MyBatis-Plus 持久化对象。
 *
 * <p>本对象只映射收藏关系表，不承载窗口或菜品详情；跨服务目标停用后，
 * 收藏行仍保留，学生端可显示不可用占位并主动移除。</p>
 */
@Data
@TableName("user_favorites")
public class UserFavoriteDO {

    /** 收藏关系的内部游标，也用于稳定的倒序分页。 */
    @TableId(value = "favorite_id", type = IdType.AUTO)
    private Long favoriteId;

    /** 收藏所属用户的内部主键，只从已验证 JWT 主体取得。 */
    @TableField("user_id")
    private Long userId;

    /** 目标类型代码，仅允许 WINDOW 或 PRODUCT。 */
    @TableField("target_type")
    private String targetType;

    /** 商品服务中的窗口 ID 或菜品 ID；该跨库引用不建立数据库外键。 */
    @TableField("target_id")
    private Long targetId;

    /** 收藏创建时间，由数据库生成，供列表展示与审计。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
