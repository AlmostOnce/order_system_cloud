package com.hbue.ordering.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.support.model.SupportTicketMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单时间线消息数据访问。
 *
 * <p>学生详情只读取 {@code internal_note = 0} 的消息，过滤发生在 SQL 层，
 * 不能依赖前端隐藏；管理端详情读取全部消息。所有查询按工单内部主键隔离。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Mapper
public interface SupportTicketMessageMapper extends BaseMapper<SupportTicketMessageDO> {

    /**
     * 查询学生可见的时间线。
     *
     * @param ticketId 工单内部主键
     * @return 按写入顺序排列的公开消息
     */
    @Select("""
            SELECT * FROM support_ticket_message
            WHERE ticket_id = #{ticketId} AND internal_note = 0
            ORDER BY id ASC
            """)
    List<SupportTicketMessageDO> timelineForCustomer(@Param("ticketId") Long ticketId);

    /**
     * 查询管理端完整时间线，含内部备注与系统消息。
     *
     * @param ticketId 工单内部主键
     * @return 按写入顺序排列的全部消息
     */
    @Select("""
            SELECT * FROM support_ticket_message
            WHERE ticket_id = #{ticketId}
            ORDER BY id ASC
            """)
    List<SupportTicketMessageDO> timelineForStaff(@Param("ticketId") Long ticketId);

    /**
     * 按工单与幂等键查询已写入的学生补充说明。
     *
     * @param ticketId   工单内部主键
     * @param requestKey 补充说明幂等键
     * @return 已写入的消息；首次提交时返回 null
     */
    @Select("""
            SELECT * FROM support_ticket_message
            WHERE ticket_id = #{ticketId} AND request_key = #{requestKey}
            """)
    SupportTicketMessageDO byRequest(@Param("ticketId") Long ticketId,
                                     @Param("requestKey") String requestKey);
}
