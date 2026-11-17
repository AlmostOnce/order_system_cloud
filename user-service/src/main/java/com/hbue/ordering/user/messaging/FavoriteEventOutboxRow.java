package com.hbue.ordering.user.messaging;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户库中尚未确认发布的收藏变更事实；不包含账号、邮箱等额外身份信息。 */
@Data
public class FavoriteEventOutboxRow {
    /** 自增发件箱编号，消费者按其数值而不是字符串字典序比较同一关系版本。 */
    private Long id;
    /** 用户库内部用户编号，仅提供给受信任消费者。 */
    private Long userId;
    /** 目标类型，仅为 PRODUCT 或 WINDOW。 */
    private String targetType;
    /** 商品服务中的菜品或窗口编号。 */
    private Long targetId;
    /** true 为收藏，false 为取消收藏的墓碑。 */
    private Boolean active;
    /** 用户库登记事务变更的时间；仅供展示或衰减，不作为版本顺序。 */
    private LocalDateTime changedAt;
}
