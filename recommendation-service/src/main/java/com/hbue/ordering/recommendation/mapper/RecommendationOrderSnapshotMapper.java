package com.hbue.ordering.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.recommendation.model.RecommendationOrderSnapshotDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

/** 推荐订单快照读写；源订单版本锁只保护本服务投影，不锁源订单库。 */
public interface RecommendationOrderSnapshotMapper extends BaseMapper<RecommendationOrderSnapshotDO> {
    /** 并发首个事件时先建立可锁定行；重复键不改变已处理版本。 */
    @Insert("""
        INSERT INTO recommendation_order_snapshots(order_id,user_id,window_id,order_version,paid_at)
        VALUES(#{orderId},#{userId},#{windowId},-1,#{paidAt})
        ON DUPLICATE KEY UPDATE order_id=VALUES(order_id)
        """)
    int insertLockRow(@Param("orderId") Long orderId, @Param("userId") Long userId,
                      @Param("windowId") Long windowId, @Param("paidAt") LocalDateTime paidAt);

    /** 在同一投影事务内串行处理一张订单的不同版本，避免旧消息并发覆盖新快照。 */
    @Select("SELECT * FROM recommendation_order_snapshots WHERE order_id=#{orderId} FOR UPDATE")
    RecommendationOrderSnapshotDO selectForUpdate(@Param("orderId") Long orderId);

    /** 查询本人90天内的最近订单；最大笔数有界，避免画像读取无界扫描。 */
    @Select("""
        SELECT * FROM recommendation_order_snapshots
        WHERE user_id=#{userId} AND paid_at >= #{since}
        ORDER BY paid_at DESC, order_id DESC LIMIT #{limit}
        """)
    List<RecommendationOrderSnapshotDO> selectRecentByUser(
            @Param("userId") Long userId, @Param("since") LocalDateTime since, @Param("limit") int limit);

    /** 只在源版本更新时替换头快照，调用方已持有本地订单行锁。 */
    @Update("""
        UPDATE recommendation_order_snapshots
        SET user_id=#{userId},window_id=#{windowId},order_version=#{orderVersion},paid_at=#{paidAt}
        WHERE order_id=#{orderId}
        """)
    int updateSnapshot(@Param("orderId") Long orderId, @Param("userId") Long userId,
            @Param("windowId") Long windowId, @Param("orderVersion") Integer orderVersion,
            @Param("paidAt") LocalDateTime paidAt);
}
