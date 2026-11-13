package com.hbue.ordering.payment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.payment.channel.BalancePaymentChannel;
import com.hbue.ordering.payment.channel.MockChannelNotifier;
import com.hbue.ordering.payment.channel.PaymentChannel;
import com.hbue.ordering.payment.channel.PaymentChannelRegistry;
import com.hbue.ordering.payment.dto.OrderInternalDTO;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.dto.request.MockPayRequest;
import com.hbue.ordering.payment.dto.request.PaymentCreateRequest;
import com.hbue.ordering.payment.enums.PaymentStatus;
import com.hbue.ordering.payment.feign.OrderInternalClient;
import com.hbue.ordering.order.api.dto.OrderPaidCommand;
import com.hbue.ordering.payment.vo.PaymentCloseResult;
import com.hbue.ordering.payment.mapper.PaymentMapper;
import com.hbue.ordering.payment.model.PaymentDO;
import com.hbue.ordering.payment.service.PaymentService;
import com.hbue.ordering.payment.vo.CheckoutInfoVO;
import com.hbue.ordering.payment.vo.PaymentCreateVO;
import com.hbue.ordering.payment.vo.PaymentStatusVO;
import com.hbue.ordering.user.api.client.UserBalanceInternalClient;
import com.hbue.ordering.user.api.dto.UserBalanceDeductCommand;
import com.hbue.ordering.user.api.vo.UserBalanceDeductResultVO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 支付业务服务实现。
 *
 * <p>职责边界：本类负责支付业务编排，不跨服务直接访问用户库或订单库；用户余额扣款
 * 通过 user-service 内部契约完成，订单推进通过 order-service 内部契约完成。
 * 跨服务调用不参与本地数据库事务，因此远程失败必须显式记录，不能伪装成分布式原子提交。</p>
 *
 * <p>幂等与一致性设计（双重防护）：
 * 第一重为支付单"仅 CREATED 可标记结果"的条件更新，并发回调只产生一个赢家；
 * 第二重为订单服务侧的乐观锁状态推进，即便第一重失效也只允许一次生效。
 * 订单推进调用仅从条件更新赢家发出，天然单次。</p>
 *
 * <p>渠道编排：下单渠道由请求中的 channelCode 经 {@link PaymentChannelRegistry}
 * 路由，渠道适配器只负责下单/验签，状态机与跨服务调用留在本类。
 * 余额渠道（BALANCE）为同步结算渠道：创建支付单后立即调用 user-service
 * 内部接口扣款（账务在 user-service 同库同事务完成，本服务不跨服务开事务），
 * 扣款成功即标记支付成功并推进订单，不存在异步回调。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Service
public class PaymentServiceImpl
        extends ServiceImpl<PaymentMapper, PaymentDO>
        implements PaymentService {

    /** 日志记录器。 */
    private com.hbue.ordering.payment.service.PaymentOrderGateService paymentGate;
    @org.springframework.beans.factory.annotation.Autowired
    void setPaymentGate(com.hbue.ordering.payment.service.PaymentOrderGateService gate) { this.paymentGate=gate; }

    private static final Logger log = LoggerFactory.getLogger(
            PaymentServiceImpl.class
    );

    /** 支付单号的时间格式。 */
    private static final DateTimeFormatter PAYMENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 新支付单的初始状态。 */
    private static final String INITIAL_PAYMENT_STATUS = "CREATED";

    /** 订单待支付的数据库状态值。 */
    private static final String ORDER_STATUS_PENDING_PAYMENT = "待支付";

    /** 通知结果：支付成功。 */
    private static final String NOTIFY_STATUS_SUCCESS = "SUCCESS";

    /** 通知结果：支付失败。 */
    private static final String NOTIFY_STATUS_FAILED = "FAILED";

    /** 统一成功码。 */
    private static final String SUCCESS_CODE = "COMMON_000";

    /** 请求未指定渠道时的缺省渠道标识。 */
    private static final String DEFAULT_CHANNEL_CODE = "MOCK";

    /** 余额扣款流水的业务类型。 */
    private static final String BALANCE_BIZ_TYPE = "PAYMENT";

    /** 订单服务内部客户端。 */
    private final OrderInternalClient orderInternalClient;

    /** 支付渠道注册表（按渠道标识路由）。 */
    private final PaymentChannelRegistry channelRegistry;

    /** 模拟渠道通知发送器。 */
    private final MockChannelNotifier mockChannelNotifier;

    /** 用户余额内部调用客户端。 */
    private final UserBalanceInternalClient userBalanceInternalClient;

    /** JSON 解析工具（解析下游失败响应体的业务提示）。 */
    private final ObjectMapper objectMapper;

    /** 用户服务内部接口共享密钥。 */
    private final String internalScopeToken;

    /**
     * 创建支付业务服务。
     *
     * @param orderInternalClient 订单服务内部客户端
     * @param channelRegistry 支付渠道注册表
     * @param mockChannelNotifier 模拟渠道通知发送器
     * @param userBalanceInternalClient 用户余额内部调用客户端
     * @param objectMapper JSON 解析工具
     * @param internalScopeToken 用户服务内部接口共享密钥
     */
    public PaymentServiceImpl(
            OrderInternalClient orderInternalClient,
            PaymentChannelRegistry channelRegistry,
            MockChannelNotifier mockChannelNotifier,
            UserBalanceInternalClient userBalanceInternalClient,
            ObjectMapper objectMapper,
            @Value("${internal.scope.token:}") String internalScopeToken
    ) {
        this.orderInternalClient = orderInternalClient;
        this.channelRegistry = channelRegistry;
        this.mockChannelNotifier = mockChannelNotifier;
        this.userBalanceInternalClient = userBalanceInternalClient;
        this.objectMapper = objectMapper;
        this.internalScopeToken = internalScopeToken;
    }

    /**
     * 为当前用户的待支付订单创建或复用支付单，并按所选渠道编排结算。
     *
     * <p>订单归属、状态和金额以订单服务返回的数据为准；同一订单的活动支付单由查询和唯一索引
     * 双重保护。余额渠道执行内部扣款，其它渠道返回收银台凭证等待验签回调。</p>
     *
     * @param userId 当前登录顾客 ID
     * @param request 创建支付单请求（含渠道标识与余额渠道的支付密码）
     * @return 支付单创建结果
     */
    @Override
    public PaymentCreateVO createPayment(Long userId, PaymentCreateRequest request) {
        PaymentChannel channel=channelRegistry.require(resolveChannelCode(request));
        Long orderId=request.getOrderId();
        OrderInternalDTO order=queryOrderInternal(orderId,userId);
        PaymentDO settled=baseMapper.selectSuccessByOrderId(orderId);
        if(settled!=null) { advanceOrderPaid(settled); return PaymentCreateVO.from(settled,null); }
        PaymentDO payment=paymentGate.whileOpen(orderId,()->{
            PaymentDO concurrentSuccess=baseMapper.selectSuccessByOrderId(orderId);
            if(concurrentSuccess!=null)return concurrentSuccess;
            // 创建与关闭意图共用支付库锁；远程扣款在提交该短事务后执行。
            if(!ORDER_STATUS_PENDING_PAYMENT.equals(order.getStatus()))
                throw new BusinessException(CommonErrorCode.CONFLICT,"订单当前状态不允许发起支付");
            if(order.getPaymentExpiresAt()==null || System.currentTimeMillis()>=order.getPaymentExpiresAt())
                throw new BusinessException(CommonErrorCode.CONFLICT,"订单已超过支付期限");
            PaymentDO existing=baseMapper.selectActiveByOrderId(orderId);
            if(existing!=null) {
                if(!channel.channelCode().equals(existing.getChannelCode()))
                    throw new BusinessException(CommonErrorCode.CONFLICT,"请使用原支付渠道完成支付");
                return existing;
            }
            PaymentDO created=buildPayment(order,channel);
            try {save(created);return created;}
            catch(DuplicateKeyException collision) {
                PaymentDO winner=baseMapper.selectActiveByOrderId(orderId);
                if(winner==null)throw collision;
                if(!channel.channelCode().equals(winner.getChannelCode()))throw new BusinessException(CommonErrorCode.CONFLICT,"请使用原支付渠道");
                return winner;
            }
        });
        if("SUCCESS".equals(payment.getStatus())) {advanceOrderPaid(payment);return PaymentCreateVO.from(payment,null);}
        if(BalancePaymentChannel.CHANNEL_CODE.equals(payment.getChannelCode()))
            return settleWithBalance(payment,channel,request.getPayPassword());
        return PaymentCreateVO.from(payment,channel.createPayment(payment));
    }

    /**
     * 解析请求中的渠道标识，缺省按 MOCK 处理。
     *
     * @param request 创建支付单请求
     * @return 渠道标识
     */
    private String resolveChannelCode(PaymentCreateRequest request) {
        String channelCode = request.getChannelCode();
        return channelCode == null || channelCode.isBlank()
                ? DEFAULT_CHANNEL_CODE
                : channelCode;
    }

    /**
     * 余额渠道结算：先由用户服务按支付单号幂等扣款，再条件更新本地支付单并推进订单。
     *
     * <p>用户服务的扣款账务事务归用户服务所有，本地事务不能覆盖该远程副作用。
     * 若远程扣款成功而本地更新未完成，重试仍使用相同支付单号作为扣款业务引用，
     * 依赖用户服务的幂等约束避免再次扣款。扣款失败直接抛出业务异常，支付单保持 CREATED 供重试；
     * 扣款成功但条件更新未命中（并发已有赢家或已终态）时不再推进订单，
     * 仅回读支付单渲染结果。</p>
     *
     * @param paymentDO 待结算支付单
     * @param channel 余额渠道实现
     * @param payPassword 支付密码
     * @return 支付单创建结果
     */
    private PaymentCreateVO settleWithBalance(
            PaymentDO paymentDO,
            PaymentChannel channel,
            String payPassword
    ) {
        PaymentChannel.ChannelPaymentCreation creation =
                channel.createPayment(paymentDO);
        // 先扣款后置成功：扣款接口以 paymentNo 作为幂等业务引用，跨服务间不存在本地原子事务。
        deductBalance(paymentDO, payPassword);

        int updated = baseMapper.markSuccessIfCreated(
                paymentDO.getPaymentNo(),
                paymentDO.getAmount(),
                creation.getChannelTradeNo()
        );
        if (updated == 1) {
            // 仅条件更新的唯一赢家推进订单，避免并发重试重复触发跨服务状态变更。

            log.info("余额支付成功，paymentNo={}，orderId={}，金额={}",
                    paymentDO.getPaymentNo(),
                    paymentDO.getOrderId(),
                    paymentDO.getAmount());
        }

        PaymentDO latest=baseMapper.selectByPaymentNo(paymentDO.getPaymentNo());
        if(latest!=null && "SUCCESS".equals(latest.getStatus())) advanceOrderPaid(latest);
        return PaymentCreateVO.from(latest,null);
    }

    /**
     * 调用用户服务内部接口扣减余额。
     *
     * <p>余额不足、未设置支付密码、支付密码错误、密码锁定等业务拒绝
     * 按下游返回的 HTTP 状态码映射为本地业务异常，原样透出提示；
     * 连接失败或 5xx 统一按服务不可用处理，绝不吞掉失败把支付单标记成功。</p>
     *
     * @param paymentDO 支付单
     * @param payPassword 支付密码
     */
    private void deductBalance(PaymentDO paymentDO, String payPassword) {
        UserBalanceDeductCommand command = new UserBalanceDeductCommand();
        command.setBizType(BALANCE_BIZ_TYPE);
        command.setBizRef(paymentDO.getPaymentNo());
        command.setAmount(paymentDO.getAmount());
        command.setPayPassword(payPassword);
        command.setRemark("余额支付订单：" + paymentDO.getOrderNo());

        ApiResponse<UserBalanceDeductResultVO> response;
        try {
            response = userBalanceInternalClient.deduct(
                    paymentDO.getUserId(),
                    internalScopeToken,
                    command
            );
        } catch (FeignException exception) {
            throw mapBalanceFailure(exception);
        }
        if (response == null
                || !SUCCESS_CODE.equals(response.getCode())
                || response.getData() == null) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "余额支付暂时不可用，请稍后重试"
            );
        }
    }

    /**
     * 把用户服务余额扣款的失败响应映射为本地业务异常。
     *
     * @param exception Feign 异常
     * @return 对应的业务异常
     */
    private BusinessException mapBalanceFailure(FeignException exception) {
        CommonErrorCode errorCode = switch (exception.status()) {
            case 400 -> CommonErrorCode.BAD_REQUEST;
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 409 -> CommonErrorCode.CONFLICT;
            case 429 -> CommonErrorCode.TOO_MANY_REQUESTS;
            default -> CommonErrorCode.SERVICE_UNAVAILABLE;
        };
        return new BusinessException(
                errorCode,
                extractFailureMessage(exception, errorCode)
        );
    }

    /**
     * 从失败响应体中提取业务提示，解析失败时退回错误码默认文案。
     *
     * @param exception Feign 异常
     * @param fallback 兜底错误码
     * @return 提示文案
     */
    private String extractFailureMessage(
            FeignException exception,
            CommonErrorCode fallback
    ) {
        String body = exception.contentUTF8();
        if (body != null && !body.isBlank()) {
            try {
                ApiResponse<?> response = objectMapper.readValue(
                        body,
                        ApiResponse.class
                );
                if (response.getMessage() != null
                        && !response.getMessage().isBlank()) {
                    return response.getMessage();
                }
            } catch (Exception parseException) {
                log.warn("解析余额扣款失败响应体失败：{}", parseException.getMessage());
            }
        }
        return fallback.message();
    }

    /**
     * 查询支付单状态（校验归属）。
     *
     * @param userId 当前登录顾客 ID
     * @param paymentId 支付单 ID
     * @return 支付单状态视图
     */
    @Override
    public PaymentStatusVO getPaymentStatus(Long userId, Long paymentId) {
        return PaymentStatusVO.from(getOwnedPayment(userId, paymentId));
    }

    /**
     * 按订单查询最近一笔支付单（校验归属）。
     *
     * @param userId 当前登录顾客 ID
     * @param orderId 订单 ID
     * @return 支付单状态视图
     */
    @Override
    public PaymentStatusVO getLatestByOrder(Long userId, Long orderId) {
        PaymentDO paymentDO = baseMapper.selectLatestByOrderId(orderId);
        if (paymentDO == null || !userId.equals(paymentDO.getUserId())) {
            // 订单或支付单不存在、以及归属不符，统一按不存在处理防泄露。
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "支付单不存在"
            );
        }
        return PaymentStatusVO.from(paymentDO);
    }

    /**
     * 处理渠道异步通知：按支付单记录选择渠道验签，随后核验金额并执行条件状态更新，
     * 只有成功更新支付单的请求才推进订单状态。
     *
     * @param request 渠道通知请求体
     * @param signature 渠道侧签名
     * @return 统一响应
     */
    @Override
    public ApiResponse<Void> handleNotify(
            ChannelNotifyRequest request,
            String signature
    ) {
        // 刻意先查单后验签：不同渠道的验签密钥与算法不同，
        // 必须由支付单上记录的 channelCode 决定用哪个渠道实现验签。
        PaymentDO paymentDO = baseMapper.selectByPaymentNo(
                request.getPaymentNo()
        );
        if (paymentDO == null) {
            log.warn("渠道通知找不到支付单，paymentNo={}", request.getPaymentNo());
            return ApiResponse.failure(
                    CommonErrorCode.NOT_FOUND,
                    "支付单不存在"
            );
        }

        PaymentChannel channel = channelRegistry.find(
                paymentDO.getChannelCode()
        );
        if (channel == null || !channel.verifyNotify(request, signature)) {
            log.warn("渠道通知验签失败，paymentNo={}，channelCode={}",
                    request.getPaymentNo(),
                    paymentDO.getChannelCode());
            return ApiResponse.failure(
                    CommonErrorCode.UNAUTHORIZED,
                    "回调验签失败"
            );
        }

        // 只有验签通过的回调才计入有效通知次数，避免伪造请求污染回调重试统计。
        // 无条件累计回调次数，重放行为可在支付单上直接观察。
        baseMapper.touchNotify(paymentDO.getPaymentNo());

        boolean success = NOTIFY_STATUS_SUCCESS.equalsIgnoreCase(
                request.getStatus()
        );
        // 状态字段已包含在签名串中；除 SUCCESS 外的已验签结果按失败通知处理。
        if (!success) {
            return handleFailedNotify(paymentDO, request);
        }
        return handleSuccessNotify(paymentDO, request);
    }

    /**
     * 处理支付成功通知。
     *
     * @param paymentDO 支付单
     * @param request 渠道通知请求体
     * @return 统一响应
     */
    private ApiResponse<Void> handleSuccessNotify(
            PaymentDO paymentDO,
            ChannelNotifyRequest request
    ) {
        BigDecimal paidAmount = parsePaidAmount(request);
        if (paidAmount == null
                || paidAmount.compareTo(paymentDO.getAmount()) != 0) {
            log.warn("渠道通知金额与应付金额不一致，paymentNo={}，应付={}，实收={}",
                    paymentDO.getPaymentNo(),
                    paymentDO.getAmount(),
                    request.getPaidAmount());
            return ApiResponse.failure(
                    CommonErrorCode.CONFLICT,
                    "回调金额与应付金额不一致"
            );
        }

        if(!"CREATED".equals(paymentDO.getStatus())) return handleDuplicatedNotify(paymentDO);
        if("MOCK".equals(paymentDO.getChannelCode())) {
            OrderInternalDTO order=queryOrderInternal(paymentDO.getOrderId(),paymentDO.getUserId());
            if(order.getPaymentExpiresAt()==null || System.currentTimeMillis()>=order.getPaymentExpiresAt()) {
                // 无真实外部扣款的演示渠道可直接拒绝迟到成功，真实渠道必须实现关单/退款协议。
                return ApiResponse.failure(CommonErrorCode.CONFLICT,"支付通知已超过订单支付期限");
            }
        }
        // 幂等核心：仅 CREATED 可标记成功，并发回调只产生一个赢家。
        int updated;
        try {
            updated=paymentGate.whileOpen(paymentDO.getOrderId(),()->baseMapper.markSuccessIfCreated(
                    paymentDO.getPaymentNo(),paidAmount,request.getChannelTradeNo()));
        } catch(BusinessException closed) {
            if(CommonErrorCode.CONFLICT.code().equals(closed.getErrorCode().code())) return handleDuplicatedNotify(paymentDO);
            throw closed;
        }
        if (updated == 0) {
            // 条件更新未命中意味着支付单已被并发回调或其它流程推进到终态，回读后判定幂等结果。
            return handleDuplicatedNotify(paymentDO);
        }

        // 本线程是唯一赢家，负责推进订单状态。
        advanceOrderPaid(paymentDO);
        log.info("支付成功，paymentNo={}，orderId={}，实收={}",
                paymentDO.getPaymentNo(),
                paymentDO.getOrderId(),
                paidAmount);
        return ApiResponse.success(null);
    }

    /**
     * 处理重复或迟到的成功通知。
     *
     * @param paymentDO 支付单
     * @return 统一响应（返回成功让渠道停止重试）
     */
    private ApiResponse<Void> handleDuplicatedNotify(PaymentDO paymentDO) {
        PaymentDO latestPayment = baseMapper.selectByPaymentNo(
                paymentDO.getPaymentNo()
        );
        PaymentStatus latestStatus = PaymentStatus.findByCode(
                latestPayment.getStatus()
        );
        if (latestStatus == PaymentStatus.SUCCESS) {
            advanceOrderPaid(latestPayment);
            // 成功事实存在时重试推进，修复之前丢失的确认。
            log.info("重复的支付成功通知，paymentNo={}", paymentDO.getPaymentNo());
            return ApiResponse.success(null);
        }

        // 迟到乱序通知（支付单已 FAILED/CLOSED）：不改变终态，
        // 返回成功让渠道停止重试；真实渠道场景应触发人工核查或退款。
        log.warn("支付单已终态，忽略迟到的成功通知，paymentNo={}，当前状态={}",
                paymentDO.getPaymentNo(),
                latestPayment.getStatus());
        return ApiResponse.success(null);
    }

    /**
     * 处理支付失败通知。
     *
     * @param paymentDO 支付单
     * @param request 渠道通知请求体
     * @return 统一响应
     */
    private ApiResponse<Void> handleFailedNotify(
            PaymentDO paymentDO,
            ChannelNotifyRequest request
    ) {
        int updated = baseMapper.markFailedIfCreated(
                paymentDO.getPaymentNo(),
                "渠道通知支付失败"
        );
        if (updated == 0) {
            // 已终态的支付单收到失败通知按幂等处理。
            log.info("支付单已终态，忽略失败通知，paymentNo={}",
                    paymentDO.getPaymentNo());
            return ApiResponse.success(null);
        }
        log.info("渠道通知支付失败，paymentNo={}，可重新发起支付",
                paymentDO.getPaymentNo());
        return ApiResponse.success(null);
    }

    /**
     * 调用订单服务推进订单状态：待支付到已支付。
     *
     * <p>订单已被取消（CONFLICT）或订单服务不可用时，支付事实保持 SUCCESS
     * 不回滚——支付事实必须如实记录；学习项目不做自动退款与跨服务事务补偿，
     * 记录 ERROR 日志人工处置。该方法吞掉已捕获的 Feign 失败，避免把已成功收款误报为支付失败。</p>
     *
     * @param paymentDO 支付单
     */
    private void advanceOrderPaid(PaymentDO paymentDO) {
        try {
            // 调用前回读支付表，避免支付对象是在置 SUCCESS 之前读取的旧实例而丢失 success_time。
            PaymentDO confirmed = baseMapper.selectByPaymentNo(paymentDO.getPaymentNo());
            if (confirmed == null || !"SUCCESS".equals(confirmed.getStatus()) || confirmed.getSuccessTime() == null) {
                log.error("无法读取已确认支付的成功时间，paymentNo={}，orderId={}", paymentDO.getPaymentNo(), paymentDO.getOrderId());
                return;
            }
            ApiResponse<Void> response = orderInternalClient.markPaid(
                    confirmed.getOrderId(), new OrderPaidCommand(confirmed.getSuccessTime()));
            if (response == null
                    || !SUCCESS_CODE.equals(response.getCode())) {
                // 不回滚已记录的支付成功事实；将无法推进的订单留给告警和人工核查。
                String message = response == null ? null : response.getMessage();
                log.error("订单服务拒绝推进订单状态，paymentNo={}，orderId={}，原因={}",
                        paymentDO.getPaymentNo(),
                        paymentDO.getOrderId(),
                        message);
            }
        } catch (FeignException exception) {
            // 网络错误无法证明订单端是否处理成功，不能在这里重复扣款或回滚支付状态。
            log.error("订单服务推进调用失败，paymentNo={}，orderId={}，状态={}",
                    paymentDO.getPaymentNo(),
                    paymentDO.getOrderId(),
                    exception.status(),
                    exception);
        }
    }

    /**
     * 查询订单内部信息并校验归属。
     *
     * @param orderId 订单 ID
     * @param userId 当前登录顾客 ID
     * @return 订单内部信息
     */
    private OrderInternalDTO queryOrderInternal(Long orderId, Long userId) {
        ApiResponse<OrderInternalDTO> response;
        try {
            response = orderInternalClient.getOrder(orderId);
        } catch (FeignException exception) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "订单服务暂时不可用"
            );
        }
        if (response == null
                || !SUCCESS_CODE.equals(response.getCode())
                || response.getData() == null) {
            // 查单不可用或未找到时停止创建支付单，避免基于不完整订单事实收费。
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }

        OrderInternalDTO order = response.getData();
        if (!userId.equals(order.getUserId())) {
            // 对其他用户的订单按不存在处理，避免泄露订单信息。
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }
        return order;
    }

    /**
     * 查询归属校验后的支付单。
     *
     * @param userId 当前登录顾客 ID
     * @param paymentId 支付单 ID
     * @return 支付单
     */
    private PaymentDO getOwnedPayment(Long userId, Long paymentId) {
        PaymentDO paymentDO = getById(paymentId);
        if (paymentDO == null || !userId.equals(paymentDO.getUserId())) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "支付单不存在"
            );
        }
        return paymentDO;
    }

    /**
     * 组装服务端控制的支付单持久化字段。
     *
     * @param order 订单内部信息
     * @param channel 已路由的渠道实现
     * @return 新支付单持久化对象
     */
    private PaymentDO buildPayment(
            OrderInternalDTO order,
            PaymentChannel channel
    ) {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(PAYMENT_TIME_FORMAT);
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        PaymentDO payment = new PaymentDO();
        payment.setPaymentNo("PAY" + time + random);
        payment.setOrderId(order.getOrderId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(order.getUserId());
        payment.setChannelCode(channel.channelCode());
        // 金额取订单快照，绝不取客户端输入。
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(INITIAL_PAYMENT_STATUS);
        payment.setNotifyCount(0);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        return payment;
    }

    /**
     * 解析通知实收金额；格式非法时返回 null 交由金额校验拒绝。
     *
     * @param request 渠道通知请求体
     * @return 实收金额
     */
    private BigDecimal parsePaidAmount(ChannelNotifyRequest request) {
        try {
            return new BigDecimal(request.getPaidAmount());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 查询模拟收银台页面信息。
     *
     * @param paymentNo 业务支付单号
     * @return 收银台信息
     */
    @Override
    public CheckoutInfoVO getCheckoutInfo(String paymentNo) {
        PaymentDO paymentDO = baseMapper.selectByPaymentNo(paymentNo);
        if (paymentDO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "支付单不存在"
            );
        }
        return CheckoutInfoVO.builder()
                .paymentNo(paymentDO.getPaymentNo())
                .orderNo(paymentDO.getOrderNo())
                .amount(paymentDO.getAmount())
                .status(paymentDO.getStatus())
                .build();
    }

    /**
     * 模拟渠道确认支付：生成渠道交易号并异步发起回调。
     *
     * <p>仅进行中（CREATED）的支付单可确认；
     * 确认后由异步线程模拟渠道向回调入口发起带签名通知。</p>
     *
     * @param paymentNo 业务支付单号
     * @param request 确认支付请求
     * @return 渠道交易号
     */
    @Override
    public String confirmMockPayment(String paymentNo, MockPayRequest request) {
        PaymentDO paymentDO = baseMapper.selectByPaymentNo(paymentNo);
        if (paymentDO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "支付单不存在"
            );
        }
        if (!INITIAL_PAYMENT_STATUS.equals(paymentDO.getStatus())) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "支付单已处理，无需重复确认"
            );
        }

        String outcome = request == null || request.getOutcome() == null
                ? NOTIFY_STATUS_SUCCESS
                : request.getOutcome();
        String paidAmount = request == null || request.getPaidAmount() == null
                ? paymentDO.getAmount().toPlainString()
                : request.getPaidAmount();

        // 渠道交易号由渠道生成；这里与真实渠道一致在确认时生成。
        String channelTradeNo = "MOCK"
                + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        mockChannelNotifier.notifyAsync(
                paymentNo,
                channelTradeNo,
                paidAmount,
                outcome
        );
        return channelTradeNo;
    }

    @Override
    public PaymentCloseResult closeUnpaidOrder(Long orderId) {
        // 此方法只关闭支付，不调用订单服务，避免关单与订单Seata分支发生反向等待。
        paymentGate.beginClose(orderId);
        boolean paid=false;
        java.time.LocalDateTime paidAt=null;
        for(PaymentDO payment:baseMapper.selectAllByOrderId(orderId)) {
            if("SUCCESS".equals(payment.getStatus())) {
                paid=true;
                if (payment.getSuccessTime() != null && (paidAt == null || payment.getSuccessTime().isBefore(paidAt))) paidAt=payment.getSuccessTime();
                continue;
            }
            if(BalancePaymentChannel.CHANNEL_CODE.equals(payment.getChannelCode())) {
                var response=userBalanceInternalClient.close(payment.getUserId(),internalScopeToken,
                        new com.hbue.ordering.user.api.dto.BalanceCloseCommand(BALANCE_BIZ_TYPE,payment.getPaymentNo(),payment.getAmount()));
                if(response==null || !SUCCESS_CODE.equals(response.getCode()) || response.getData()==null)
                    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"无法确认余额结算结果");
                if("PAID".equals(response.getData())) {
                    if(!"CREATED".equals(payment.getStatus()))
                        throw new BusinessException(CommonErrorCode.CONFLICT,"支付流水与支付单终态冲突，请核查");
                    baseMapper.markSuccessIfCreated(payment.getPaymentNo(),payment.getAmount(),"BAL-RECOVER-"+payment.getPaymentNo());
                    // 余额服务已确认款项时重新读取支付表，不能依赖关单前加载的 CREATED 快照。
                    PaymentDO recovered = baseMapper.selectByPaymentNo(payment.getPaymentNo());
                    if (recovered == null || !"SUCCESS".equals(recovered.getStatus()) || recovered.getSuccessTime() == null)
                        throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"无法确认余额支付的成功时间");
                    paid=true;
                    if (paidAt == null || recovered.getSuccessTime().isBefore(paidAt)) paidAt=recovered.getSuccessTime();
                    continue;
                }
                if(!"CLOSED".equals(response.getData()))
                    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"未知余额结算结果");
            } else if(!"MOCK".equals(payment.getChannelCode())) {
                throw new BusinessException(CommonErrorCode.CONFLICT,"当前渠道尚未实现安全关单");
            }
            baseMapper.closeIfCreated(payment.getPaymentNo());
        }
        if (paid && paidAt == null) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"成功支付记录缺少成功时间");
        return new PaymentCloseResult(paid?"PAID":"CLOSED", paidAt);
    }
}
