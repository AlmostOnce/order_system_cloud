package com.hbue.ordering.order.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CancellationNotificationTccMapper {
    record Row(long orderId, int orderVersion, long recipientId, String status) {}
    @Insert("""
        INSERT INTO order_notification_tcc(xid,order_id,order_version,recipient_id,status)
        VALUES(#{xid},#{orderId},#{version},#{recipientId},#{status}) ON DUPLICATE KEY UPDATE xid=xid
        """)
    int insertIfMissing(@Param("xid") String xid, @Param("orderId") long orderId,
            @Param("version") int version, @Param("recipientId") long recipientId, @Param("status") String status);
    @Select("SELECT order_id,order_version,recipient_id,status FROM order_notification_tcc WHERE xid=#{xid} FOR UPDATE")
    Row lock(@Param("xid") String xid);
    @Update("UPDATE order_notification_tcc SET status=#{status} WHERE xid=#{xid}")
    int setStatus(@Param("xid") String xid, @Param("status") String status);
}
