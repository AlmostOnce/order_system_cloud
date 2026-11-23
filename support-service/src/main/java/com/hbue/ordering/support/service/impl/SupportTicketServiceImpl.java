package com.hbue.ordering.support.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.client.SupportOrderClient;
import com.hbue.ordering.support.client.SupportWindowClient;
import com.hbue.ordering.support.client.SupportOrderSnapshotVO;
import com.hbue.ordering.support.client.vo.SupportWindowInfoVO;
import com.hbue.ordering.support.dto.AddSupportMessageRequest;
import com.hbue.ordering.support.dto.CreateSupportTicketRequest;
import com.hbue.ordering.support.dto.SupportCloseRequest;
import com.hbue.ordering.support.model.SupportTicketDO;
import com.hbue.ordering.support.model.SupportTicketMessageDO;
import com.hbue.ordering.support.mapper.SupportTicketMessageMapper;
import com.hbue.ordering.support.mapper.SupportTicketMapper;
import com.hbue.ordering.support.service.SupportTicketService;
import com.hbue.ordering.support.support.SupportCategory;
import com.hbue.ordering.support.support.SupportSenderType;
import com.hbue.ordering.support.support.SupportStatus;
import com.hbue.ordering.support.support.SupportType;
import com.hbue.ordering.support.vo.SupportTicketDetailVO;
import com.hbue.ordering.support.vo.SupportTicketPageVO;
import com.hbue.ordering.support.vo.SupportTicketSummaryVO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 学生端反馈工单业务实现。
 *
 * <p>实现 {@link SupportTicketService} 定义的学生侧边界，所有方法都以 JWT 中的
 * 学生内部编号为归属依据，不接受客户端指定他人身份。
 * 创建接口同时使用请求头幂等键与内容摘要，保证网络重试不重复建单、同键不同内容
 * 不会被静默覆盖；补充说明使用工单内幂等键。数据权限在 SQL 条件中绑定学生编号，
 * 越权访问统一返回「反馈不存在」，不暴露他人工单是否存在。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Service
public class SupportTicketServiceImpl implements SupportTicketService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(SupportTicketServiceImpl.class);

    /**
     * 单页最大条数，避免一次读取过多时间线或图片。
     */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 默认分页条数。
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 描述最短长度。
     */
    private static final int MIN_DESCRIPTION_LENGTH = 10;

    /**
     * 描述与消息正文最大长度。
     */
    private static final int MAX_CONTENT_LENGTH = 1000;

    /**
     * 设备信息最大长度，与数据库列一致。
     */
    private static final int MAX_DEVICE_INFO_LENGTH = 255;

    /**
     * 幂等键格式：8-64 位字母、数字、下划线或短横线，与计划契约一致。
     *
     * <p>不强制 UUID，前端可以用 UUID，也允许网关注入的短随机串；但仍限制字符集与
     * 长度，避免把任意请求头原样写入唯一键列或用于日志注入。</p>
     */
    private static final Pattern REQUEST_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

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
     * 订单服务客户端，用于校验订单归属并读取窗口快照。
     */
    private final SupportOrderClient orderClient;

    /**
     * 窗口服务客户端，用于换算餐厅编号与窗口名称。
     */
    private final SupportWindowClient windowClient;

    /**
     * 服务间共享内部令牌，用于窗口内部接口。
     */
    private final String internalToken;

    /**
     * 创建学生反馈服务。
     *
     * @param ticketMapper  工单数据访问
     * @param messageMapper 消息数据访问
     * @param imageService      图片校验与序列化服务
     * @param viewAssembler     视图转换器
     * @param orderClient   订单服务客户端
     * @param windowClient  窗口服务客户端
     * @param internalToken 服务间共享内部令牌
     */
    public SupportTicketServiceImpl(SupportTicketMapper ticketMapper,
                                    SupportTicketMessageMapper messageMapper,
                                    SupportImageService imageService,
                                    SupportTicketViewAssembler viewAssembler,
                                    SupportOrderClient orderClient,
                                    SupportWindowClient windowClient,
                                    @Value("${internal.scope.token:}") String internalToken) {
        this.ticketMapper = ticketMapper;
        this.messageMapper = messageMapper;
        this.imageService = imageService;
        this.viewAssembler = viewAssembler;
        this.orderClient = orderClient;
        this.windowClient = windowClient;
        this.internalToken = internalToken;
    }

    /**
     * 提交反馈。
     *
     * <p>处理顺序：参数与图片校验 → 幂等回查 → 订单/窗口快照 → 插入。
     * 远程调用放在插入之前，避免依赖失败时留下半成品工单；并发同键插入由数据库
     * 唯一约束兜底，捕获冲突后回查并返回已存在工单。</p>
     *
     * @param customerId    当前学生内部用户编号
     * @param authorization 学生原始 Bearer 凭据，用于订单归属校验
     * @param request       提交内容
     * @param requestKey    请求头幂等键
     * @return 新建或已存在的工单详情
     */
    @Override
    @Transactional
    public SupportTicketDetailVO create(Long customerId, String authorization,
                                        CreateSupportTicketRequest request, String requestKey) {
        String normalizedKey = normalizeRequestKey(requestKey);
        SupportType type = requireType(request.getType());
        SupportCategory category = requireCategory(type, request.getCategory());
        String description = normalizeDescription(request.getDescription());
        List<String> imageKeys = imageService.validateAndNormalize(request.getImageFileKeys(), customerId);
        String requestHash = buildRequestHash(type, category, request, description, imageKeys);

        SupportTicketDO existing = ticketMapper.byCustomerRequest(customerId, normalizedKey);
        if (existing != null) {
            return existingDetail(existing, requestHash);
        }

        SupportTicketDO ticket = new SupportTicketDO();
        ticket.setTicketId(UUID.randomUUID().toString());
        ticket.setCustomerId(customerId);
        ticket.setType(type.getCode());
        ticket.setCategory(category.getCode());
        ticket.setStatus(SupportStatus.PENDING.getCode());
        ticket.setDescription(description);
        ticket.setImageFileKeys(imageService.toJson(imageKeys));
        ticket.setCustomerUnread(0);
        ticket.setRequestKey(normalizedKey);
        ticket.setRequestHash(requestHash);
        if (type == SupportType.DINING) {
            fillDiningContext(ticket, request, authorization);
        } else {
            // 软件问题不关联订单与窗口，设备信息截断后保存。
            ticket.setDeviceInfo(truncate(request.getDeviceInfo(), MAX_DEVICE_INFO_LENGTH));
        }

        try {
            ticketMapper.insert(ticket);
        } catch (DuplicateKeyException exception) {
            // 并发重试时另一个请求已插入同一幂等键，回查后按内容摘要决定返回或拒绝。
            SupportTicketDO concurrent = ticketMapper.byCustomerRequest(customerId, normalizedKey);
            if (concurrent == null) {
                throw exception;
            }
            return existingDetail(concurrent, requestHash);
        }
        log.info("学生提交反馈成功，ticketId={}, type={}, customerId={}",
                ticket.getTicketId(), type.getCode(), customerId);
        return loadCustomerDetail(ticket.getTicketId(), customerId);
    }

    /**
     * 查询学生本人的反馈列表。
     *
     * @param customerId 学生内部用户编号
     * @param cursor     上一页最后一条的公开标识，首屏可空
     * @param limit      每页条数，1-50
     * @return 游标分页结果
     */
    @Override
    public SupportTicketPageVO page(Long customerId, String cursor, int limit) {
        int pageSize = normalizeLimit(limit);
        List<SupportTicketDO> rows = ticketMapper.pageByCustomer(
                customerId, blankToNull(cursor), pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore && !rows.isEmpty()
                ? rows.get(rows.size() - 1).getTicketId() : null;
        List<SupportTicketSummaryVO> items = new ArrayList<>(rows.size());
        for (SupportTicketDO row : rows) {
            items.add(viewAssembler.toSummary(row));
        }
        return new SupportTicketPageVO(List.copyOf(items), nextCursor, hasMore);
    }

    /**
     * 查询学生本人反馈详情，并在读取时清空新回复提示。
     *
     * <p>清未读失败不影响详情返回；下次打开会再次尝试。时间线在 SQL 层过滤
     * 内部备注，学生无法通过接口看到员工内部交接内容。</p>
     *
     * @param customerId 学生内部用户编号
     * @param ticketId   公开工单标识
     * @return 工单详情
     */
    @Override
    @Transactional
    public SupportTicketDetailVO detail(Long customerId, String ticketId) {
        SupportTicketDO ticket = requireOwned(customerId, ticketId);
        clearUnread(ticket, customerId);
        return viewAssembler.toDetail(ticket, messageMapper.timelineForCustomer(ticket.getId()));
    }

    /**
     * 学生补充说明。
     *
     * <p>只有未关闭工单可以补充。请求头幂等键与工单内部主键共同保证同一次重试
     * 只写入一条消息；已写入时直接返回当前详情，不重复刷新工单更新时间。</p>
     *
     * @param customerId 学生内部用户编号
     * @param ticketId   公开工单标识
     * @param requestKey 请求头幂等键
     * @param request    补充内容与图片
     * @return 补充后的工单详情
     */
    @Override
    @Transactional
    public SupportTicketDetailVO addMessage(Long customerId, String ticketId,
                                            String requestKey, AddSupportMessageRequest request) {
        String normalizedKey = normalizeRequestKey(requestKey);
        SupportTicketDO ticket = requireOwned(customerId, ticketId);
        // 幂等回查必须先于状态校验：首次补充成功后工单可能被关闭，网络重试仍应返回首次结果，
        // 不能因为当前已关闭而把一次已经成功的补充误报为 409。
        SupportTicketMessageDO existing = messageMapper.byRequest(ticket.getId(), normalizedKey);
        if (existing != null) {
            return loadCustomerDetail(ticketId, customerId);
        }
        if (!SupportStatus.PENDING.getCode().equals(ticket.getStatus())
                && !SupportStatus.PROCESSING.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "反馈已关闭，无法补充说明");
        }
        // 只有真正要写入新消息时才校验正文与图片，重放请求不再重复调用文件服务。
        String content = normalizeContent(request.getContent(), "补充内容");
        List<String> imageKeys = imageService.validateAndNormalize(request.getImageFileKeys(), customerId);
        SupportTicketMessageDO message = new SupportTicketMessageDO();
        message.setMessageId(UUID.randomUUID().toString());
        message.setTicketId(ticket.getId());
        message.setSenderType(SupportSenderType.CUSTOMER.getCode());
        message.setSenderId(customerId);
        message.setContent(content);
        message.setImageFileKeys(imageService.toJson(imageKeys));
        message.setInternalNote(0);
        message.setRequestKey(normalizedKey);
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException exception) {
            // 并发重试同一补充说明时返回当前详情，不把唯一键冲突暴露给客户端。
            if (messageMapper.byRequest(ticket.getId(), normalizedKey) == null) {
                throw exception;
            }
            return loadCustomerDetail(ticketId, customerId);
        }
        ticketMapper.touchUpdatedAt(ticket.getId());
        return loadCustomerDetail(ticketId, customerId);
    }

    /**
     * 学生关闭反馈。
     *
     * <p>重复关闭按幂等处理，直接返回已关闭详情；从待受理或处理中关闭时写一条
     * 系统消息说明原因。关闭只影响工单本身，不触发订单或退款状态变更。</p>
     *
     * @param customerId 学生内部用户编号
     * @param ticketId   公开工单标识
     * @param request    关闭原因，可空
     * @return 关闭后的工单详情
     */
    @Override
    @Transactional
    public SupportTicketDetailVO close(Long customerId, String ticketId, SupportCloseRequest request) {
        SupportTicketDO ticket = requireOwned(customerId, ticketId);
        if (SupportStatus.CLOSED.getCode().equals(ticket.getStatus())) {
            return loadCustomerDetail(ticketId, customerId);
        }
        int updated = ticketMapper.closeByCustomer(ticket.getId(), customerId);
        if (updated == 0) {
            SupportTicketDO latest = requireOwned(customerId, ticketId);
            if (SupportStatus.CLOSED.getCode().equals(latest.getStatus())) {
                return loadCustomerDetail(ticketId, customerId);
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "反馈状态已变化，请刷新后重试");
        }
        String reason = request == null ? null : trimToNull(request.getReason());
        appendSystemMessage(ticket.getId(),
                reason == null ? "学生已关闭反馈" : "学生已关闭反馈：" + reason);
        log.info("学生关闭反馈，ticketId={}, customerId={}", ticketId, customerId);
        return loadCustomerDetail(ticketId, customerId);
    }

    /**
     * 校验并补全用餐问题的订单与窗口快照。
     *
     * <p>订单号优先作为关联依据：订单服务已经按学生 JWT 校验归属，反馈服务只读取
     * 窗口与订单号快照。同时提交窗口时必须与订单窗口一致；只提交窗口时通过窗口
     * 内部接口换算餐厅。两者都为空直接拒绝，避免无法落实管理端数据范围。</p>
     *
     * @param ticket        待插入工单
     * @param request       提交内容
     * @param authorization 学生 Bearer 凭据
     */
    private void fillDiningContext(SupportTicketDO ticket, CreateSupportTicketRequest request,
                                   String authorization) {
        Long orderId = request.getOrderId();
        Long requestedWindowId = request.getWindowId();
        if (orderId == null && requestedWindowId == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "用餐问题请选择关联订单或窗口");
        }
        SupportOrderSnapshotVO order = null;
        if (orderId != null) {
            if (authorization == null || authorization.isBlank()) {
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
            }
            order = requireOrder(orderId, authorization);
            if (order.windowId() == null) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST,
                        "该订单没有关联窗口，请通过软件问题反馈");
            }
            if (requestedWindowId != null && !requestedWindowId.equals(order.windowId())) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "所选窗口与订单不一致");
            }
            ticket.setOrderId(order.orderId());
            ticket.setOrderNo(order.orderNo());
            ticket.setWindowId(order.windowId());
            ticket.setWindowName(order.windowName());
        } else {
            ticket.setWindowId(requestedWindowId);
        }
        SupportWindowInfoVO window = requireWindow(ticket.getWindowId());
        if (window.restaurantId() == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "无法确定窗口所属餐厅");
        }
        ticket.setRestaurantId(window.restaurantId());
        if (ticket.getWindowName() == null || ticket.getWindowName().isBlank()) {
            ticket.setWindowName(window.windowName());
        }
    }

    /**
     * 通过订单服务读取订单快照。
     *
     * @param orderId       订单编号
     * @param authorization 学生 Bearer 凭据
     * @return 订单快照
     */
    private SupportOrderSnapshotVO requireOrder(Long orderId, String authorization) {
        ApiResponse<SupportOrderSnapshotVO> response;
        try {
            response = orderClient.getOrder(orderId, authorization);
        } catch (FeignException.Unauthorized exception) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        } catch (FeignException.Forbidden | FeignException.NotFound exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "订单不存在或不属于当前账号");
        } catch (FeignException exception) {
            log.warn("查询订单失败，orderId={}, httpStatus={}", orderId, exception.status());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "订单服务暂不可用，请稍后重试");
        }
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "订单不存在或不属于当前账号");
        }
        return response.getData();
    }

    /**
     * 通过商品服务读取窗口信息。
     *
     * @param windowId 窗口编号
     * @return 窗口信息
     */
    private SupportWindowInfoVO requireWindow(Long windowId) {
        if (internalToken == null || internalToken.isBlank()) {
            // 内部令牌未配置时直接拒绝，不能把无凭据请求发给商品服务再解释成窗口不存在。
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "内部调用凭据未配置");
        }
        ApiResponse<SupportWindowInfoVO> response;
        try {
            response = windowClient.getWindow(windowId, internalToken);
        } catch (FeignException exception) {
            log.warn("查询窗口失败，windowId={}, httpStatus={}", windowId, exception.status());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        }
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "窗口不存在或已删除");
        }
        return response.getData();
    }

    /**
     * 查询学生本人可见的工单详情。
     *
     * @param ticketId   公开工单标识
     * @param customerId 学生内部用户编号
     * @return 工单详情
     */
    private SupportTicketDetailVO loadCustomerDetail(String ticketId, Long customerId) {
        SupportTicketDO ticket = requireOwned(customerId, ticketId);
        clearUnread(ticket, customerId);
        return viewAssembler.toDetail(ticket, messageMapper.timelineForCustomer(ticket.getId()));
    }

    /**
     * 清空新回复提示并把内存对象同步为零。
     *
     * <p>清零属于辅助副作用，计划要求清零失败不影响详情返回；这里捕获异常并记录告警，
     * 下次打开详情会再次尝试。异常被就地吞掉，不会把一次成功的读取变成失败响应。</p>
     *
     * @param ticket     工单
     * @param customerId 学生内部用户编号
     */
    private void clearUnread(SupportTicketDO ticket, Long customerId) {
        if (!Integer.valueOf(1).equals(ticket.getCustomerUnread())) {
            return;
        }
        try {
            ticketMapper.clearCustomerUnread(ticket.getId(), customerId);
            ticket.setCustomerUnread(0);
        } catch (RuntimeException exception) {
            // 只在日志中保留工单标识，不记录学生编号等敏感信息。
            log.warn("清空反馈未读提示失败，ticketId={}", ticket.getTicketId(), exception);
        }
    }

    /**
     * 查询本人工单，不存在或不属于本人统一返回 404。
     *
     * @param customerId 学生内部用户编号
     * @param ticketId   公开工单标识
     * @return 工单
     */
    private SupportTicketDO requireOwned(Long customerId, String ticketId) {
        SupportTicketDO ticket = ticketMapper.byTicketId(ticketId);
        if (ticket == null || !customerId.equals(ticket.getCustomerId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "反馈不存在");
        }
        return ticket;
    }

    /**
     * 幂等回查命中时的返回逻辑。
     *
     * @param existing    已存在工单
     * @param requestHash 本次请求内容摘要
     * @return 已存在工单详情
     */
    private SupportTicketDetailVO existingDetail(SupportTicketDO existing, String requestHash) {
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "该请求编号已用于其他反馈内容，请重新提交");
        }
        return loadCustomerDetail(existing.getTicketId(), existing.getCustomerId());
    }

    /**
     * 追加系统状态消息。
     *
     * @param ticketId 工单内部主键
     * @param content  状态说明
     */
    private void appendSystemMessage(Long ticketId, String content) {
        SupportTicketMessageDO message = new SupportTicketMessageDO();
        message.setMessageId(UUID.randomUUID().toString());
        message.setTicketId(ticketId);
        message.setSenderType(SupportSenderType.SYSTEM.getCode());
        message.setSenderId(null);
        message.setContent(content);
        message.setImageFileKeys("[]");
        message.setInternalNote(0);
        message.setRequestKey(null);
        messageMapper.insert(message);
    }

    /**
     * 校验并规整请求头幂等键。
     *
     * <p>按计划契约接受 8-64 位字母、数字、下划线或短横线，不强制 UUID；
     * 缺失、超长或含其他字符一律返回 400。规整后直接作为数据库唯一键的一部分，
     * 不做大小写转换，避免两次不同请求被误判为同一次。</p>
     *
     * @param requestKey 客户端请求头
     * @return 去除首尾空白后的幂等键
     */
    private String normalizeRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "缺少请求编号，请重试");
        }
        String normalized = requestKey.trim();
        if (!REQUEST_KEY_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST,
                    "请求编号需为 8-64 位字母、数字、下划线或短横线");
        }
        return normalized;
    }

    /**
     * 解析工单类型。
     *
     * @param code 类型编码
     * @return 类型枚举
     */
    private SupportType requireType(String code) {
        SupportType type = SupportType.findByCode(code);
        if (type == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请选择反馈类型");
        }
        return type;
    }

    /**
     * 解析并校验分类归属。
     *
     * @param type 工单类型
     * @param code 分类编码
     * @return 分类枚举
     */
    private SupportCategory requireCategory(SupportType type, String code) {
        SupportCategory category = SupportCategory.findByTypeAndCode(type, code);
        if (category == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请选择有效的反馈分类");
        }
        return category;
    }

    /**
     * 规整并校验问题描述。
     *
     * @param description 原始描述
     * @return 去除首尾空白后的描述
     */
    private String normalizeDescription(String description) {
        String value = trimToNull(description);
        if (value == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请填写问题描述");
        }
        if (value.length() < MIN_DESCRIPTION_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "问题描述至少 10 个字");
        }
        if (value.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "问题描述不能超过 1000 字");
        }
        return value;
    }

    /**
     * 规整并校验消息正文。
     *
     * @param content 原始正文
     * @param label   字段名称，用于错误提示
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
     * 计算创建内容摘要。
     *
     * <p>摘要只用于识别同一幂等键下的内容变化，不包含学生编号（唯一约束已包含），
     * 使用 SHA-256 十六进制字符串与数据库列长度一致。</p>
     *
     * @param type        工单类型
     * @param category    分类
     * @param request     原始请求
     * @param description 规整后的描述
     * @param imageKeys   校验后的图片键
     * @return 64 位十六进制摘要
     */
    private String buildRequestHash(SupportType type, SupportCategory category,
                                    CreateSupportTicketRequest request,
                                    String description, List<String> imageKeys) {
        String source = type.getCode() + "|" + category.getCode() + "|"
                + nullToEmpty(request.getOrderId()) + "|" + nullToEmpty(request.getWindowId()) + "|"
                + description + "|" + imageKeys + "|" + nullToEmpty(trimToNull(request.getDeviceInfo()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // JDK 必须提供 SHA-256；缺失说明运行环境损坏，不能降级为不需要幂等。
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
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
     * 截断可选文本。
     *
     * @param value     原始文本
     * @param maxLength 最大长度
     * @return 去除首尾空白并截断后的文本；空值返回 null
     */
    private String truncate(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
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
     * 空串转 null，用于游标参数。
     *
     * @param value 原始游标
     * @return 非空游标或 null
     */
    private String blankToNull(String value) {
        return trimToNull(value);
    }

    /**
     * 空值转空串，用于摘要拼接。
     *
     * @param value 输入值
     * @return 非空字符串
     */
    private String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
