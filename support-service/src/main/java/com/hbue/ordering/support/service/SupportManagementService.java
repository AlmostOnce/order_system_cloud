package com.hbue.ordering.support.service;

import com.hbue.ordering.support.dto.SupportCloseRequest;
import com.hbue.ordering.support.dto.SupportInternalNoteRequest;
import com.hbue.ordering.support.dto.SupportReplyRequest;
import com.hbue.ordering.support.vo.SupportManagementPageVO;
import com.hbue.ordering.support.vo.SupportManagementTicketVO;

/**
 * 管理端反馈工单业务边界。
 *
 * <p>供餐厅经理、总经理与系统管理员使用。控制器只负责角色入口限制，
 * 实现类必须在每次查询和写入前重新读取操作者数据范围并校验目标工单，
 * 不能信任客户端传入的餐厅编号或角色信息。</p>
 *
 * <p>状态迁移统一使用带原状态条件的更新；重复受理、重复关闭按幂等返回当前详情，
 * 并发变化通过受影响行数发现并转为可展示冲突提示。公开回复在事务提交后发送
 * 站内通知，通知失败不回滚回复。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
public interface SupportManagementService {

    /**
     * 查询管理端工单队列。
     *
     * @param actor    管理端操作者身份快照
     * @param status   状态筛选，可空
     * @param type     类型筛选，可空
     * @param windowId 窗口筛选，可空
     * @param cursor   游标，可空
     * @param limit    每页条数，实现类需限制最大值
     * @return 管理端分页结果
     */
    SupportManagementPageVO page(SupportActor actor, String status, String type,
                                 Long windowId, String cursor, int limit);

    /**
     * 查询管理端工单详情，包含内部备注与提交人编号。
     *
     * @param actor    管理端操作者身份快照
     * @param ticketId 公开工单标识
     * @return 管理端工单视图
     */
    SupportManagementTicketVO detail(SupportActor actor, String ticketId);

    /**
     * 受理工单，将待受理迁移为处理中。
     *
     * @param actor    管理端操作者身份快照
     * @param ticketId 公开工单标识
     * @return 受理后的工单详情
     */
    SupportManagementTicketVO accept(SupportActor actor, String ticketId);

    /**
     * 公开回复学生。
     *
     * <p>只允许处理中的工单；回复正文与学生可见，提交后异步发送站内通知。</p>
     *
     * @param actor    管理端操作者身份快照
     * @param ticketId 公开工单标识
     * @param request  回复正文与图片
     * @return 回复后的工单详情
     */
    SupportManagementTicketVO reply(SupportActor actor, String ticketId, SupportReplyRequest request);

    /**
     * 添加内部备注。
     *
     * <p>内部备注只出现在管理端时间线，不置学生未读、不更新最近回复时间、
     * 不发送通知；已关闭工单不得继续备注。</p>
     *
     * @param actor    管理端操作者身份快照
     * @param ticketId 公开工单标识
     * @param request  备注正文
     * @return 添加后的工单详情
     */
    SupportManagementTicketVO addInternalNote(SupportActor actor, String ticketId,
                                              SupportInternalNoteRequest request);

    /**
     * 管理端关闭工单。
     *
     * @param actor    管理端操作者身份快照
     * @param ticketId 公开工单标识
     * @param request  关闭原因，可空
     * @return 关闭后的工单详情
     */
    SupportManagementTicketVO close(SupportActor actor, String ticketId, SupportCloseRequest request);
}
