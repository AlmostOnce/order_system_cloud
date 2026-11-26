package com.hbue.ordering.support.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.assembler.SupportTicketViewAssembler;
import com.hbue.ordering.support.client.SupportNotificationClient;
import com.hbue.ordering.support.client.vo.SupportReplyNotificationRequest;
import com.hbue.ordering.support.client.vo.SupportStatusNotificationRequest;
import com.hbue.ordering.support.dto.SupportCloseRequest;
import com.hbue.ordering.support.dto.SupportInternalNoteRequest;
import com.hbue.ordering.support.dto.SupportReplyRequest;
import com.hbue.ordering.support.mapper.SupportTicketMapper;
import com.hbue.ordering.support.mapper.SupportTicketMessageMapper;
import com.hbue.ordering.support.model.SupportTicketDO;
import com.hbue.ordering.support.model.SupportTicketMessageDO;
import com.hbue.ordering.support.service.SupportActor;
import com.hbue.ordering.support.service.SupportImageService;
import com.hbue.ordering.support.service.SupportManagementService;
import com.hbue.ordering.support.service.SupportScope;
import com.hbue.ordering.support.service.SupportScopeService;
import com.hbue.ordering.support.support.SupportRole;
import com.hbue.ordering.support.support.SupportSenderType;
import com.hbue.ordering.support.support.SupportStatus;
import com.hbue.ordering.support.support.SupportType;
import com.hbue.ordering.support.vo.SupportManagementPageVO;
import com.hbue.ordering.support.vo.SupportManagementTicketVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 管理端反馈工单业务。
 *
 * <p>角色注解限制接口入口，本服务在每次操作前重新读取操作者数据范围并校验目标
 * 工单是否可见。状态迁移全部使用带原状态条件的 UPDATE：重复受理按幂等返回当前
 * 详情，先查后改之间的并发变化通过受影响行数发现并返回冲突提示。公开回复提交后
 * 才异步通知学生，通知失败不回滚回复。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Service
public class SupportManagementServiceImpl implements SupportManagementService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(SupportManagementServiceImpl.class);

    /**
     * 单页最大条数。
     */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 默认分页条数。
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 消息正文最大长度。
     */
    private static final int MAX_CONTENT_LENGTH = 1000;

    /**
     * 工单表数据访问。
     */
    private final SupportTicketMapper ticketMapper;

    /**
     * 工单消息数据访问。
     */
    private final SupportTicketMessageMapper messageMapper;

    /**
     * 图片校验与序列化服务。
     */
    private final SupportImageService imageService;

    /**
     * 视图转换器。
     */
    private final SupportTicketViewAssembler viewAssembler;

    /**
     * 数据范围与类型权限服务。
     */
    private final SupportScopeService scopeService;

    /**
     * 通知服务客户端。
     */
    private final SupportNotificationClient notificationClient;

    /**
     * 服务间共享内部令牌。
     */
    private final String internalToken;

    /**
     * 创建管理端工单服务。
     *
     * @param ticketMapper   工单数据访问
     * @param messageMapper  消息数据访问
     * @param imageService       图片校验与序列化服务
     * @param viewAssembler      视图转换器
     * @param scopeService       数据范围与类型权限服务
     * @param notificationClient 通知服务客户端
     * @param internalToken      服务间共享内部令牌
     */
    public SupportManagementServiceImpl(SupportTicketMapper ticketMapper,
                                        SupportTicketMessageMapper messageMapper,
                                        SupportImageService imageService,
                                        SupportTicketViewAssembler viewAssembler,
                                        SupportScopeService scopeService,
                                        SupportNotificationClient notificationClient,
                                        @Value("${internal.scope.token:}") String internalToken) {
        this.ticketMapper = ticketMapper;
        this.messageMapper = messageMapper;
        this.imageService = imageService;
        this.viewAssembler = viewAssembler;
        this.scopeService = scopeService;
        this.notificationClient = notificationClient;
        this.internalToken = internalToken;
    }

    /**
     * 查询管理端工单队列。
     *
     * <p>列表在数据库层按角色可见类型与餐厅范围过滤：系统管理员只查软件工单；
     * 餐厅经理只查授权餐厅的用餐工单；总经理持有全平台范围时可混合查询两类。
     * 非全平台范围且没有任何餐厅时直接返回空页，不执行 `IN ()` 查询。</p>
     *
     * @param actor    管理端操作者
     * @param status   状态筛选，可空
     * @param type     类型筛选，可空
     * @param windowId 窗口筛选，可空
     * @param cursor   游标，可空
     * @param limit    每页条数
     * @return 管理端分页结果
     */
    @Override
    public SupportManagementPageVO page(SupportActor actor, String status, String type,
                                        Long windowId, String cursor, int limit) {
        SupportScope scope = scopeService.resolve(actor.userId());
        String statusFilter = normalizeStatus(status);
        SupportType typeFilter = normalizeType(type);
        int pageSize = normalizeLimit(limit);
        // 类型筛选的越权判定统一交给范围服务，与详情/处理路径共用同一套角色规则，
        // 避免出现"列表能查到、点进详情却 403"的口径漂移。
        scopeService.requireTypeFilterAllowed(actor, scope, typeFilter);

        List<SupportTicketDO> rows;
        if (scopeService.usesPlatformQueue(actor, scope)) {
            // 系统管理员即使不传类型也强制只看软件工单，防止默认展示用餐业务数据。
            String queryType = actor.hasRole(SupportRole.SYSTEM_ADMIN)
                    ? SupportType.SOFTWARE.getCode()
                    : (typeFilter == null ? null : typeFilter.getCode());
            rows = ticketMapper.pageForPlatform(statusFilter, queryType, windowId,
                    blankToNull(cursor), pageSize + 1);
        } else {
            if (scope.restaurantIds().isEmpty()) {
                return new SupportManagementPageVO(List.of(), null, false);
            }
            rows = ticketMapper.pageForRestaurants(statusFilter, SupportType.DINING.getCode(),
                    windowId, blankToNull(cursor), pageSize + 1, scope.restaurantIds());
        }

        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore && !rows.isEmpty()
                ? rows.get(rows.size() - 1).getTicketId() : null;
        List<SupportManagementTicketVO> items = new ArrayList<>(rows.size());
        for (SupportTicketDO row : rows) {
            // 队列只需要摘要，不加载时间线，避免一页触发几十次消息查询。
            items.add(viewAssembler.toManagement(row, List.of()));
        }
        return new SupportManagementPageVO(List.copyOf(items), nextCursor, hasMore);
    }

    /**
     * 查询管理端工单详情。
     *
     * @param actor    管理端操作者
     * @param ticketId 公开工单标识
     * @return 含内部备注的完整工单
     */
    @Override
    public SupportManagementTicketVO detail(SupportActor actor, String ticketId) {
        SupportTicketDO ticket = requireTicket(ticketId);
        SupportScope scope = scopeService.resolve(actor.userId());
        scopeService.requireAccessible(actor, scope, ticket);
        return viewAssembler.toManagement(ticket, messageMapper.timelineForStaff(ticket.getId()));
    }

    /**
     * 受理工单。
     *
     * <p>待受理 → 处理中。重复受理直接返回当前详情；已关闭工单返回 409。
     * 并发受理时只有一条 UPDATE 成功，其余请求回读后按当前状态返回。
     * 受理成功后追加系统时间线消息，并在事务提交后向学生发送状态变更站内信。</p>
     *
     * @param actor    管理端操作者
     * @param ticketId 公开工单标识
     * @return 受理后的工单详情
     */
    @Override
    @Transactional
    public SupportManagementTicketVO accept(SupportActor actor, String ticketId) {
        SupportTicketDO ticket = requireAccessibleTicket(actor, ticketId);
        if (SupportStatus.PROCESSING.getCode().equals(ticket.getStatus())) {
            return loadManagementDetail(ticket.getId());
        }
        if (SupportStatus.CLOSED.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "反馈已关闭，无法受理");
        }
        int updated = ticketMapper.updateStatus(ticket.getId(),
                SupportStatus.PENDING.getCode(), SupportStatus.PROCESSING.getCode());
        if (updated == 0) {
            SupportTicketDO latest = requireTicket(ticketId);
            if (SupportStatus.PROCESSING.getCode().equals(latest.getStatus())) {
                return loadManagementDetail(latest.getId());
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "反馈状态已变化，请刷新后重试");
        }
        SupportTicketMessageDO systemMessage = appendSystemMessage(ticket.getId(), "工作人员已受理");
        registerAfterCommitStatusNotification(ticket, systemMessage,
                "您提交的反馈（编号 " + viewAssembler.displayNo(ticket.getTicketId())
                        + "）已由工作人员受理，正在处理中。");
        log.info("管理端受理反馈，ticketId={}, operatorId={}", ticketId, actor.userId());
        return loadManagementDetail(ticket.getId());
    }

    /**
     * 公开回复学生。
     *
     * <p>仅处理中工单可回复；回复落库与工单未读、最近回复时间在同一事务内完成。
     * 通知在事务提交后发送：提交前失败会导致回滚，提交后通知失败只记录日志，
     * 学生仍可通过详情看到回复，后续补偿任务可按未通知消息重放。</p>
     *
     * @param actor    管理端操作者
     * @param ticketId 公开工单标识
     * @param request  回复正文与图片
     * @return 回复后的工单详情
     */
    @Override
    @Transactional
    public SupportManagementTicketVO reply(SupportActor actor, String ticketId,
                                           SupportReplyRequest request) {
        SupportTicketDO ticket = requireAccessibleTicket(actor, ticketId);
        if (SupportStatus.CLOSED.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "反馈已关闭，无法回复");
        }
        if (!SupportStatus.PROCESSING.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先受理该反馈再回复");
        }
        String content = normalizeContent(request.getContent(), "回复内容");
        List<String> imageKeys = imageService.validateAndNormalize(
                request.getImageFileKeys(), actor.userId());
        SupportTicketMessageDO message = new SupportTicketMessageDO();
        message.setMessageId(UUID.randomUUID().toString());
        message.setTicketId(ticket.getId());
        message.setSenderType(SupportSenderType.STAFF.getCode());
        message.setSenderId(actor.userId());
        message.setContent(content);
        message.setImageFileKeys(imageService.toJson(imageKeys));
        message.setInternalNote(0);
        messageMapper.insert(message);
        int updated = ticketMapper.markReplied(ticket.getId());
        if (updated == 0) {
            // 工单在写消息后被并发关闭，整个事务回滚，回复不会以孤儿消息留下。
            throw new BusinessException(CommonErrorCode.CONFLICT, "反馈状态已变化，请刷新后重试");
        }
        registerAfterCommitNotification(ticket, message);
        log.info("管理端回复反馈，ticketId={}, operatorId={}", ticketId, actor.userId());
        return loadManagementDetail(ticket.getId());
    }

    /**
     * 添加内部备注。
     *
     * <p>内部备注不置学生未读、不发送通知、不更新最近回复时间，只出现在管理端
     * 时间线中。已关闭工单拒绝备注，避免关闭后继续产生业务内容。</p>
     *
     * @param actor    管理端操作者
     * @param ticketId 公开工单标识
     * @param request  备注正文
     * @return 添加后的工单详情
     */
    @Override
    @Transactional
    public SupportManagementTicketVO addInternalNote(SupportActor actor, String ticketId,
                                                     SupportInternalNoteRequest request) {
        SupportTicketDO ticket = requireAccessibleTicket(actor, ticketId);
        if (SupportStatus.CLOSED.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "反馈已关闭，无法添加备注");
        }
        String content = normalizeContent(request.getContent(), "备注内容");
        SupportTicketMessageDO message = new SupportTicketMessageDO();
        message.setMessageId(UUID.randomUUID().toString());
        message.setTicketId(ticket.getId());
        message.setSenderType(SupportSenderType.STAFF.getCode());
        message.setSenderId(actor.userId());
        message.setContent(content);
        message.setImageFileKeys("[]");
        message.setInternalNote(1);
        messageMapper.insert(message);
        ticketMapper.touchUpdatedAt(ticket.getId());
        return loadManagementDetail(ticket.getId());
    }

    /**
     * 管理端关闭工单。
     *
     * <p>重复关闭按幂等返回当前详情；从待受理或处理中关闭时追加系统消息，
     * 并在事务提交后发送状态变更站内信。关闭不改变订单状态，也不触发退款。</p>
     *
     * @param actor    管理端操作者
     * @param ticketId 公开工单标识
     * @param request  关闭原因，可空
     * @return 关闭后的工单详情
     */
    @Override
    @Transactional
    public SupportManagementTicketVO close(SupportActor actor, String ticketId,
                                           SupportCloseRequest request) {
        SupportTicketDO ticket = requireAccessibleTicket(actor, ticketId);
        if (SupportStatus.CLOSED.getCode().equals(ticket.getStatus())) {
            return loadManagementDetail(ticket.getId());
        }
        int updated = ticketMapper.closeByStaff(ticket.getId());
        if (updated == 0) {
            SupportTicketDO latest = requireTicket(ticketId);
            if (SupportStatus.CLOSED.getCode().equals(latest.getStatus())) {
                return loadManagementDetail(latest.getId());
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "反馈状态已变化，请刷新后重试");
        }
        String reason = request == null ? null : trimToNull(request.getReason());
        SupportTicketMessageDO systemMessage = appendSystemMessage(ticket.getId(),
                reason == null ? "工作人员已关闭反馈" : "工作人员已关闭反馈：" + reason);
        String notice = "您提交的反馈（编号 " + viewAssembler.displayNo(ticket.getTicketId()) + "）已关闭。";
        if (reason != null) {
            // 关闭原因已写入学生可见的系统时间线，通知中同步展示，避免学生必须进入详情才能了解原因。
            notice = notice + "关闭说明：" + reason;
        }
        registerAfterCommitStatusNotification(ticket, systemMessage, notice);
        log.info("管理端关闭反馈，ticketId={}, operatorId={}", ticketId, actor.userId());
        return loadManagementDetail(ticket.getId());
    }

    /**
     * 读取工单并校验数据范围。
     *
     * @param actor    管理端操作者
     * @param ticketId 公开工单标识
     * @return 通过范围校验的工单
     */
    private SupportTicketDO requireAccessibleTicket(SupportActor actor, String ticketId) {
        SupportTicketDO ticket = requireTicket(ticketId);
        SupportScope scope = scopeService.resolve(actor.userId());
        scopeService.requireAccessible(actor, scope, ticket);
        return ticket;
    }

    /**
     * 按公开标识读取工单，不存在返回 404。
     *
     * @param ticketId 公开工单标识
     * @return 工单
     */
    private SupportTicketDO requireTicket(String ticketId) {
        SupportTicketDO ticket = ticketMapper.byTicketId(ticketId);
        if (ticket == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "工单不存在");
        }
        return ticket;
    }

    /**
     * 读取管理端完整详情。
     *
     * <p>内部方法不再重复校验范围，调用前必须已经通过 {@code requireAccessibleTicket}。</p>
     *
     * @param ticketId 工单内部主键
     * @return 管理端详情
     */
    private SupportManagementTicketVO loadManagementDetail(Long ticketId) {
        SupportTicketDO ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "工单不存在");
        }
        return viewAssembler.toManagement(ticket, messageMapper.timelineForStaff(ticketId));
    }

    /**
     * 追加系统状态消息。
     *
     * <p>系统消息没有发送人编号，学生可见；返回消息对象是为了把公开消息标识
     * 交给通知事件编号，保证通知与时间线消息一一对应、重试幂等。</p>
     *
     * @param ticketId 工单内部主键
     * @param content  状态说明
     * @return 已写入的系统消息
     */
    private SupportTicketMessageDO appendSystemMessage(Long ticketId, String content) {
        SupportTicketMessageDO message = new SupportTicketMessageDO();
        message.setMessageId(UUID.randomUUID().toString());
        message.setTicketId(ticketId);
        message.setSenderType(SupportSenderType.SYSTEM.getCode());
        message.setContent(content);
        message.setImageFileKeys("[]");
        message.setInternalNote(0);
        messageMapper.insert(message);
        return message;
    }

    /**
     * 在当前事务提交后发送回复站内信通知。
     *
     * @param ticket  工单
     * @param message 公开回复消息
     */
    private void registerAfterCommitNotification(SupportTicketDO ticket, SupportTicketMessageDO message) {
        runAfterCommit(() -> notifyCustomer(ticket, message));
    }

    /**
     * 在当前事务提交后发送工单状态变更站内信。
     *
     * <p>受理与关闭都是已提交的业务事实，通知只是提醒；事务回滚时不发送，
     * 通知失败只记录日志，学生仍可在反馈列表中看到最新状态。</p>
     *
     * @param ticket  工单
     * @param message 系统时间线消息，其公开标识用于通知幂等
     * @param content 通知正文
     */
    private void registerAfterCommitStatusNotification(SupportTicketDO ticket,
                                                       SupportTicketMessageDO message,
                                                       String content) {
        runAfterCommit(() -> notifyCustomerStatus(ticket, message, content));
    }

    /**
     * 在事务提交后执行通知动作。
     *
     * <p>没有活动事务时直接执行，保证方法被非事务入口复用时不会静默丢通知；
     * 正常入口都带 {@code @Transactional}，提交前不会通知学生。</p>
     *
     * @param action 通知动作
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * 调用通知服务写入站内信。
     *
     * <p>通知失败只记录告警，不影响已提交的回复。事件编号采用
     * {@code support-reply:{messageId}}，通知服务按事件编号幂等，
     * 重试不会产生重复站内信。</p>
     *
     * @param ticket  工单
     * @param message 公开回复消息
     */
    private void notifyCustomer(SupportTicketDO ticket, SupportTicketMessageDO message) {
        String title = "反馈处理进展";
        String content = "您提交的反馈（编号 " + viewAssembler.displayNo(ticket.getTicketId())
                + "）已有新回复，请在“帮助与反馈”中查看。";
        try {
            // 事件编号带业务前缀，通知服务按事件编号幂等，重试不会产生重复站内信。
            String eventId = "support-reply:" + message.getMessageId();
            ApiResponse<Void> response = notificationClient.sendSupportReply(internalToken,
                    new SupportReplyNotificationRequest(ticket.getCustomerId(),
                            eventId, title, content, ticket.getTicketId()));
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
                log.warn("反馈回复通知发送失败，ticketId={}, messageId={}",
                        ticket.getTicketId(), message.getMessageId());
            }
        } catch (RuntimeException exception) {
            // 通知属于最终一致环节，失败不抛出，避免把已成功的回复误报为失败。
            log.warn("反馈回复通知调用异常，ticketId={}, messageId={}",
                    ticket.getTicketId(), message.getMessageId(), exception);
        }
    }

    /**
     * 调用通知服务写入工单状态变更站内信。
     *
     * <p>通知类型由通知服务按接口固定为 {@code SUPPORT_STATUS}；事件编号采用
     * {@code support-status:{messageId}}，与本次追加的系统时间线消息一一对应。
     * 通知失败只记录告警，不影响已经提交的受理或关闭结果。</p>
     *
     * @param ticket  工单
     * @param message 系统时间线消息
     * @param content 通知正文
     */
    private void notifyCustomerStatus(SupportTicketDO ticket, SupportTicketMessageDO message, String content) {
        String title = "反馈处理进展";
        try {
            String eventId = "support-status:" + message.getMessageId();
            ApiResponse<Void> response = notificationClient.sendSupportStatus(internalToken,
                    new SupportStatusNotificationRequest(ticket.getCustomerId(),
                            eventId, title, content, ticket.getTicketId()));
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
                log.warn("反馈状态通知发送失败，ticketId={}, messageId={}",
                        ticket.getTicketId(), message.getMessageId());
            }
        } catch (RuntimeException exception) {
            // 通知失败不回滚已提交的受理或关闭，学生进入反馈列表仍能看到最新状态。
            log.warn("反馈状态通知调用异常，ticketId={}, messageId={}",
                    ticket.getTicketId(), message.getMessageId(), exception);
        }
    }

    /**
     * 规整状态筛选。
     *
     * @param status 状态编码，可空
     * @return 标准状态编码或 null
     */
    private String normalizeStatus(String status) {
        String value = trimToNull(status);
        if (value == null || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        SupportStatus parsed = SupportStatus.findByCode(value);
        if (parsed == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "状态筛选值不正确");
        }
        return parsed.getCode();
    }

    /**
     * 规整类型筛选。
     *
     * @param type 类型编码，可空
     * @return 类型枚举或 null
     */
    private SupportType normalizeType(String type) {
        String value = trimToNull(type);
        if (value == null || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        SupportType parsed = SupportType.findByCode(value);
        if (parsed == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "类型筛选值不正确");
        }
        return parsed;
    }

    /**
     * 规整并校验管理端正文。
     *
     * @param content 原始正文
     * @param label   字段名称
     * @return 去除首尾空白后的正文
     */
    private String normalizeContent(String content, String label) {
        String value = trimToNull(content);
        if (value == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, label + "不能为空");
        }
        if (value.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, label + "不能超过 1000 字");
        }
        return value;
    }

    /**
     * 规整分页条数。
     *
     * @param limit 客户端传入条数
     * @return 1-50 范围内的条数
     */
    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    /**
     * 去除首尾空白，空串转 null。
     *
     * @param value 原始文本
     * @return 规整后的文本或 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 空串转 null。
     *
     * @param value 原始游标
     * @return 非空游标或 null
     */
    private String blankToNull(String value) {
        return trimToNull(value);
    }
}
