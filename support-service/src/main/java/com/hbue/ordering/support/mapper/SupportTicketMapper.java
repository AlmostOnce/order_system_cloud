package com.hbue.ordering.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.support.model.SupportTicketDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 反馈工单数据访问。
 *
 * <p>所有查询都绑定学生本人或管理端数据范围，不接受客户端直接指定其他用户；
 * 所有业务值使用 {@code #{...}} 参数绑定。状态变更一律通过带原状态条件的
 * 单条 UPDATE 完成，由数据库行锁保证并发下不会出现先查后改的覆盖。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Mapper
public interface SupportTicketMapper extends BaseMapper<SupportTicketDO> {

    /**
     * 按公开标识查询工单。
     *
     * @param ticketId 公开工单标识
     * @return 工单；不存在时返回 null
     */
    @Select("SELECT * FROM support_ticket WHERE ticket_id = #{ticketId}")
    SupportTicketDO byTicketId(@Param("ticketId") String ticketId);

    /**
     * 按学生与幂等键查询首次创建的工单。
     *
     * @param customerId 学生内部用户编号
     * @param requestKey 创建幂等键
     * @return 工单；首次提交时返回 null
     */
    @Select("""
            SELECT * FROM support_ticket
            WHERE customer_id = #{customerId} AND request_key = #{requestKey}
            """)
    SupportTicketDO byCustomerRequest(@Param("customerId") Long customerId,
                                      @Param("requestKey") String requestKey);

    /**
     * 查询学生本人的工单游标页。
     *
     * <p>游标使用上一页最后一条的公开标识，子查询再次绑定本人范围，
     * 即使传入他人标识也只会得到本人范围内的排序位置。</p>
     *
     * @param customerId 学生内部用户编号
     * @param cursor     上一页最后一条工单的公开标识，首屏为空
     * @param size       本次读取条数，调用方传入 limit + 1
     * @return 按内部主键倒序的工单
     */
    @Select("""
            SELECT * FROM support_ticket
            WHERE customer_id = #{customerId}
              AND (#{cursor} IS NULL OR id < (
                  SELECT cursor_row.id FROM support_ticket cursor_row
                  WHERE cursor_row.ticket_id = #{cursor}
                    AND cursor_row.customer_id = #{customerId}
              ))
            ORDER BY id DESC
            LIMIT #{size}
            """)
    List<SupportTicketDO> pageByCustomer(@Param("customerId") Long customerId,
                                         @Param("cursor") String cursor,
                                         @Param("size") int size);

    /**
     * 管理端队列查询（全平台范围）。
     *
     * <p>不限餐厅；status、type、windowId 均可空，为空时不过滤。
     * 具体可见类型与角色由 Service 预先收敛，本方法不再做角色判断。</p>
     *
     * @param status   状态编码，可空
     * @param type     工单类型编码，可空
     * @param windowId 窗口编号，可空
     * @param cursor   游标工单公开标识，可空
     * @param size     读取条数，调用方传入 limit + 1
     * @return 按内部主键倒序的工单
     */
    @Select("""
            SELECT * FROM support_ticket
            WHERE (#{status} IS NULL OR status = #{status})
              AND (#{type} IS NULL OR type = #{type})
              AND (#{windowId} IS NULL OR window_id = #{windowId})
              AND (#{cursor} IS NULL OR id < (
                  SELECT cursor_row.id FROM support_ticket cursor_row
                  WHERE cursor_row.ticket_id = #{cursor}
              ))
            ORDER BY id DESC
            LIMIT #{size}
            """)
    List<SupportTicketDO> pageForPlatform(@Param("status") String status,
                                          @Param("type") String type,
                                          @Param("windowId") Long windowId,
                                          @Param("cursor") String cursor,
                                          @Param("size") int size);

    /**
     * 管理端队列查询（餐厅范围）。
     *
     * <p>餐厅经理只能看到本人餐厅范围内的用餐工单。SQL 在数据库层绑定
     * restaurant_id 集合，避免先查出全量再在内存中过滤；集合由 Service 保证非空。</p>
     *
     * @param status        状态编码，可空
     * @param type          工单类型编码，可空
     * @param windowId      窗口编号，可空
     * @param cursor        游标工单公开标识，可空
     * @param size          读取条数，调用方传入 limit + 1
     * @param restaurantIds 授权餐厅编号集合，必须非空
     * @return 按内部主键倒序的工单
     */
    @Select("""
            <script>
            SELECT * FROM support_ticket
            WHERE restaurant_id IN
            <foreach collection="restaurantIds" item="restaurantId" open="(" separator="," close=")">
                #{restaurantId}
            </foreach>
              AND (#{status} IS NULL OR status = #{status})
              AND (#{type} IS NULL OR type = #{type})
              AND (#{windowId} IS NULL OR window_id = #{windowId})
              AND (#{cursor} IS NULL OR id &lt; (
                  SELECT cursor_row.id FROM support_ticket cursor_row
                  WHERE cursor_row.ticket_id = #{cursor}
              ))
            ORDER BY id DESC
            LIMIT #{size}
            </script>
            """)
    List<SupportTicketDO> pageForRestaurants(@Param("status") String status,
                                             @Param("type") String type,
                                             @Param("windowId") Long windowId,
                                             @Param("cursor") String cursor,
                                             @Param("size") int size,
                                             @Param("restaurantIds") List<Long> restaurantIds);

    /**
     * 受理工单：只有待受理状态可以迁移到处理中。
     *
     * @param id   工单内部主键
     * @param from 期望的原状态
     * @param to   目标状态
     * @return 受影响行数；0 表示状态已被其他请求改变
     */
    @Update("""
            UPDATE support_ticket
            SET status = #{to}, updated_at = NOW(3)
            WHERE id = #{id} AND status = #{from}
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("from") String from,
                     @Param("to") String to);

    /**
     * 记录商家公开回复：置未读、刷新最近回复与更新时间。
     *
     * <p>只允许处理中的工单回复；若工单刚被关闭，条件更新返回 0，调用方需回读状态并拒绝。</p>
     *
     * @param id 工单内部主键
     * @return 受影响行数
     */
    @Update("""
            UPDATE support_ticket
            SET last_reply_at = NOW(3), customer_unread = 1, updated_at = NOW(3)
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markReplied(@Param("id") Long id);

    /**
     * 学生查看详情后清空未读提示。
     *
     * <p>清零失败不影响详情返回，下次查看会再次尝试；只更新本人数据。</p>
     *
     * @param id         工单内部主键
     * @param customerId 学生内部用户编号
     * @return 受影响行数
     */
    @Update("""
            UPDATE support_ticket
            SET customer_unread = 0
            WHERE id = #{id} AND customer_id = #{customerId} AND customer_unread = 1
            """)
    int clearCustomerUnread(@Param("id") Long id,
                            @Param("customerId") Long customerId);

    /**
     * 学生关闭工单：本人且未关闭时生效。
     *
     * @param id         工单内部主键
     * @param customerId 学生内部用户编号
     * @return 受影响行数；0 表示已关闭或不属于本人
     */
    @Update("""
            UPDATE support_ticket
            SET status = 'CLOSED', closed_at = NOW(3), customer_unread = 0, updated_at = NOW(3)
            WHERE id = #{id} AND customer_id = #{customerId}
              AND status IN ('PENDING','PROCESSING')
            """)
    int closeByCustomer(@Param("id") Long id,
                        @Param("customerId") Long customerId);

    /**
     * 管理端关闭工单：仅未关闭状态生效。
     *
     * @param id 工单内部主键
     * @return 受影响行数；0 表示已被关闭
     */
    @Update("""
            UPDATE support_ticket
            SET status = 'CLOSED', closed_at = NOW(3), updated_at = NOW(3)
            WHERE id = #{id} AND status IN ('PENDING','PROCESSING')
            """)
    int closeByStaff(@Param("id") Long id);

    /**
     * 学生补充说明后刷新工单更新时间，不改变状态与回复时间。
     *
     * @param id 工单内部主键
     * @return 受影响行数
     */
    @Update("UPDATE support_ticket SET updated_at = NOW(3) WHERE id = #{id}")
    int touchUpdatedAt(@Param("id") Long id);

    /**
     * 统计学生账号注销前仍未关闭的工单数量。
     *
     * <p>待受理和处理中都属于未关闭，必须阻断注销；已关闭工单保留历史记录，
     * 不阻断注销。查询绑定学生内部用户编号，不返回工单内容。</p>
     *
     * @param customerId 学生内部用户编号
     * @return 未关闭工单数
     */
    @Select("""
            SELECT COUNT(*) FROM support_ticket
            WHERE customer_id = #{customerId} AND status IN ('PENDING','PROCESSING')
            """)
    long countOpenForAccountDeletion(@Param("customerId") Long customerId);
}
