package com.hbue.ordering.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.dto.request.OrderItemCreateRequest;
import com.hbue.ordering.order.enums.OrderStatus;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.notification.CancellationNotificationTcc;
import com.hbue.ordering.order.service.OrderPersistenceService;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.order.vo.OrderStatusVO;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import com.hbue.ordering.stock.api.client.StockServiceClient;
import com.hbue.ordering.stock.api.dto.StockDeductRequest;
import com.hbue.ordering.stock.api.dto.StockItemRequest;
import com.hbue.ordering.stock.api.dto.StockRestoreRequest;
import com.hbue.ordering.user.api.client.UserServiceClient;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 订单业务服务实现。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Service
public class OrderServiceImpl
        extends ServiceImpl<OrderMapper, OrderDO>
        implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    /** 单个订单允许的最大菜品行数。 */
    private static final int MAX_ORDER_ITEMS = OrderCreateRequest.MAX_ITEM_COUNT;

    /** 新订单的初始状态。 */
    private static final String INITIAL_ORDER_STATUS = "待支付";

    /** 订单表 DECIMAL(10,2) 能保存的最大金额。 */
    private static final BigDecimal MAX_ORDER_AMOUNT =
            new BigDecimal("99999999.99");

    /** 审计动作：商家推进订单状态。 */
    private static final String ORDER_STATUS_ADVANCE = "ORDER_STATUS_ADVANCE";

    /** 审计目标类型：订单。 */
    private static final String ORDER_TARGET_TYPE = "ORDER";

    /** 用户服务远程客户端。 */
    private final UserServiceClient userServiceClient;

    /** 商品服务核价客户端。 */
    private final ProductServiceClient productServiceClient;

    /** 订单头和明细原子持久化服务。 */
    private final OrderPersistenceService orderPersistenceService;

    /** 订单明细 Mapper。 */
    private final OrderItemMapper orderItemMapper;

    /** 数据范围判定服务。 */
    private final DataScopeService dataScopeService;

    /** 管理操作审计日志记录器。 */
    private final ManagementAuditLogger auditLogger;

    /** 库存服务内部扣减客户端。 */
    private final StockServiceClient stockServiceClient;

    /** 服务间内部接口共享密钥，调用库存服务时通过请求头传递。 */
    private final String internalScopeToken;

    @Value("${notification.cancel-tcc.enabled:false}")
    private boolean cancellationNotificationEnabled;
    private ObjectProvider<CancellationNotificationTcc> cancellationNotification;

    @Autowired
    void setCancellationNotification(ObjectProvider<CancellationNotificationTcc> provider) {
        this.cancellationNotification = provider;
    }

    /**
     * 创建订单业务服务。
     *
     * @param userServiceClient 用户服务远程客户端
     * @param productServiceClient 商品服务核价客户端
     * @param orderPersistenceService 订单原子持久化服务
     * @param orderItemMapper 订单明细 Mapper
     * @param dataScopeService 数据范围判定服务
     * @param auditLogger 管理操作审计日志记录器
     * @param stockServiceClient 库存服务内部扣减客户端
     * @param internalScopeToken 服务间内部接口共享密钥
     */
    public OrderServiceImpl(
            UserServiceClient userServiceClient,
            ProductServiceClient productServiceClient,
            OrderPersistenceService orderPersistenceService,
            OrderItemMapper orderItemMapper,
            DataScopeService dataScopeService,
            ManagementAuditLogger auditLogger,
            StockServiceClient stockServiceClient,
            @Value("${internal.scope.token:}") String internalScopeToken
    ) {
        this.userServiceClient = userServiceClient;
        this.productServiceClient = productServiceClient;
        this.orderPersistenceService = orderPersistenceService;
        this.orderItemMapper = orderItemMapper;
        this.dataScopeService = dataScopeService;
        this.auditLogger = auditLogger;
        this.stockServiceClient = stockServiceClient;
        this.internalScopeToken = internalScopeToken;
    }

    /**
     * 远程查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
     */
    @Override
    public UserBasicVO getUserBasic(Long userId) {
        // 用户信息属于 user-service 的数据边界，订单服务通过 Feign 获取，不直接读取用户库。
        ApiResponse<UserBasicVO> response = userServiceClient.getUserById(userId);
        // 远程调用无响应或返回失败时，不把不完整数据继续用于订单业务。
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "用户服务暂时不可用"
            );
        }
        return response.getData();
    }

    /**
     * 查询当前用户的订单详情。
     *
     * @param userId 当前登录用户 ID
     * @param orderId 订单 ID
     * @return 订单及其菜品快照
     */
    @Override
    public OrderDetailVO getOrderDetailById(Long userId, Long orderId) {
        // 将订单不存在和不属于当前用户统一按 404 返回，避免泄露订单是否存在。
        OrderDO orderDO = getById(orderId);
        if (orderDO == null || !userId.equals(orderDO.getUserId())) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }

        // 用户展示信息通过用户服务查询；菜品名称和价格来自下单时保存的明细快照。
        UserBasicVO userBasicVO = getUserBasic(orderDO.getUserId());
        return OrderDetailVO.from(
                orderDO,
                userBasicVO,
                orderItemMapper.selectByOrderId(orderId)
        );
    }

    /**
     * 创建订单，商品价格始终以商品服务返回值为准。
     *
     * <p>整个方法运行在 Seata 全局事务中：订单与明细写入本地库后调用库存服务扣减库存，
     * 任一环节失败（包括库存不足、库存服务不可用）都会回滚全局事务，
     * 使「订单落库」与「库存扣减」要么同时成功、要么同时不发生。</p>
     *
     * @param userId 当前登录用户 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 窗口、菜品清单和备注
     * @return 新订单详情；同键同请求时返回已有订单
     */
    @Override
    @GlobalTransactional(name = "create-order-deduct-stock", rollbackFor = Exception.class)
    public OrderDetailVO createOrder(
            Long userId,
            String idempotencyKey,
            OrderCreateRequest request
    ) {
        // 在访问数据库和远程服务前校验幂等键及业务字段，尽早拒绝无效请求。
        validateIdempotencyKey(idempotencyKey);
        validateCreateRequest(request);

        // 请求摘要用于区分同一幂等键下的安全重试与内容不同的新请求。
        String requestHash = calculateRequestHash(request);
        // 先查已有记录以快速处理普通重试；并发首次请求仍由数据库唯一约束兜底。
        OrderDO existingOrder = baseMapper.selectByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        );
        if (existingOrder != null) {
            return replayOrder(existingOrder, requestHash);
        }

        // 商品价格和可售状态以商品服务的核价结果为准，不信任客户端提交的价格。
        List<ProductQuoteVO> productQuotes = queryProductQuotes(request);
        // 将核价结果固化为订单明细快照，避免商品后续改价影响已创建订单。
        List<OrderItemDO> orderItems = buildOrderItems(request, productQuotes);
        // 总金额从服务端计算的行金额累加，并在写库前检查数据库 DECIMAL 字段上限。
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemDO orderItem : orderItems) {
            totalAmount = totalAmount.add(orderItem.getLineAmount());
        }
        if (totalAmount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "订单总金额超出系统限制"
            );
        }

        // 下单前确认用户服务仍能确认该用户存在；报价成功不代表用户资料读取一定成功。
        UserBasicVO userBasicVO = getUserBasic(userId);
        if (userBasicVO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在"
            );
        }

        // 订单号、初始状态、版本号和请求摘要由服务端生成，客户端不能指定持久化字段。
        OrderDO orderDO = buildOrder(
                userId,
                idempotencyKey,
                request,
                requestHash,
                totalAmount
        );

        try {
            // 订单头和明细必须在同一事务保存，避免出现缺少明细的半成品订单。
            orderPersistenceService.saveOrderWithItems(orderDO, orderItems);
            // 订单与明细已写入本地库，紧接着扣减库存：
            // 库存不足或库存服务不可用时抛出业务异常，由方法上的全局事务回滚订单写入。
            deductStock(orderDO.getOrderNo(), userId, request);
        } catch (DuplicateKeyException exception) {
            // 原子写入事务已回滚；使用新的一致性读取取得并发请求的赢家订单。
            OrderDO concurrentOrder = baseMapper.selectByUserIdAndIdempotencyKey(
                    userId,
                    idempotencyKey
            );
            if (concurrentOrder == null) {
                throw exception;
            }
            return replayOrder(concurrentOrder, requestHash);
        }

        return OrderDetailVO.from(orderDO, userBasicVO, orderItems);
    }

    /**
     * 调用库存服务扣减本单菜品库存。
     *
     * <p>只有库存服务返回成功业务码才算扣减成功；库存不足等业务失败会保留原始提示抛出，
     * 库存服务不可用则按 503 抛出。两种失败都会让调用方的全局事务回滚订单写入，
     * 绝不会出现「订单已落库但库存未扣减」的超卖窗口。</p>
     *
     * @param orderNo 订单号，作为库存流水的幂等业务单据号
     * @param userId 下单用户 ID，作为库存流水操作者
     * @param request 创建订单请求，提供档口与菜品明细
     */
    private void deductStock(
            String orderNo,
            Long userId,
            OrderCreateRequest request
    ) {
        // 把订单明细转换为库存扣减明细：菜品 ID、数量与下单内容完全一致。
        List<StockItemRequest> stockItems = new ArrayList<>();
        for (OrderItemCreateRequest item : request.getItems()) {
            stockItems.add(StockItemRequest.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .build());
        }

        StockDeductRequest deductRequest = StockDeductRequest.builder()
                // 订单号是库存扣减的幂等键，重复下单请求不会重复扣减同一订单的库存。
                .bizRef(orderNo)
                .windowId(request.getWindowId())
                .items(stockItems)
                .operatorId(userId)
                .remark(request.getRemark())
                .build();

        // 内部接口不携带用户 JWT，改由共享密钥鉴权。
        ApiResponse<Void> response = stockServiceClient.deduct(
                internalScopeToken,
                deductRequest
        );

        if (response == null) {
            // 无响应说明远程调用异常，按服务不可用处理，触发全局回滚。
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "库存服务暂时不可用"
            );
        }
        if (CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            return;
        }

        // 区分两类失败：服务不可用可稍后重试；业务拒绝（如库存不足）需要用户调整订单。
        String message = response.getMessage();
        if (CommonErrorCode.SERVICE_UNAVAILABLE.code().equals(response.getCode())) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    message == null || message.isBlank()
                            ? "库存服务暂时不可用"
                            : message
            );
        }
        throw new BusinessException(
                CommonErrorCode.CONFLICT,
                message == null || message.isBlank()
                        ? "库存扣减失败，请调整后重试"
                        : message
        );
    }

    /**
     * 顾客取消自己尚未支付的订单。
     *
     * @param userId 当前登录顾客 ID
     * @param orderId 订单 ID
     * @return 订单状态变更结果
     */
    @Override
    @GlobalTransactional(name = "cancel-order-restore-stock", rollbackFor = Exception.class)
    public OrderStatusVO cancelOrder(Long userId, Long orderId) {
        // 先确认订单归属；随后状态机校验确保已支付或履约中的订单不能由顾客取消。
        OrderDO orderDO = getOrderForStatusChange(orderId);
        if (userId == null || !userId.equals(orderDO.getUserId())) {
            // 对其他用户的订单按不存在处理，避免泄露订单信息。
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }

        // 仅允许待支付订单取消，实际更新仍由状态与版本号条件保护并发写入。
        OrderStatus currentStatus = getCurrentStatus(orderDO);
        if (currentStatus == OrderStatus.CANCELLED) {
            // 成功后的网络重试不重复执行回补。
            return OrderStatusVO.builder().orderId(orderId).status(currentStatus.getLabel()).build();
        }
        if (currentStatus != OrderStatus.PENDING_PAYMENT
                || !currentStatus.canTransitionTo(OrderStatus.CANCELLED)) {
            throw invalidStatusTransition();
        }

        // 先竞争状态迁移权，再回补：取消与支付竞争时只有取消成功的一方能回补。
        // 任一远程失败均抛异常，由全局事务回滚取消状态与库存分支。
        OrderStatusVO result = persistStatusTransition(orderDO, OrderStatus.CANCELLED);
        List<OrderItemDO> orderItems = orderItemMapper.selectByOrderId(orderId);
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单缺少菜品明细，无法回补库存");
        }
        List<StockItemRequest> stockItems = new ArrayList<>();
        for (OrderItemDO item : orderItems) {
            stockItems.add(StockItemRequest.builder()
                    .productId(item.getProductId()).quantity(item.getQuantity()).build());
        }
        ApiResponse<Void> response;
        try {
            response = stockServiceClient.restore(internalScopeToken,
                    StockRestoreRequest.builder()
                            .bizRef(orderDO.getOrderNo()).windowId(orderDO.getWindowId())
                            .items(stockItems).operatorId(userId).remark("顾客取消待支付订单").build());
        } catch (RuntimeException exception) {
            log.warn("取消订单时调用库存回补失败：订单 {}", orderId, exception);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "库存服务暂时不可用，请稍后重试取消");
        }
        if (response == null || CommonErrorCode.SERVICE_UNAVAILABLE.code().equals(response.getCode())) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "库存服务暂时不可用，请稍后重试取消");
        }
        if (!CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    response.getMessage() == null || response.getMessage().isBlank()
                            ? "库存回补失败，请稍后重试取消" : response.getMessage());
        }
        if (cancellationNotificationEnabled) {
            CancellationNotificationTcc participant = cancellationNotification.getIfAvailable();
            if (participant == null || !participant.prepare(orderId, orderDO.getVersion() + 1, userId))
                throw new IllegalStateException("取消通知事务准备失败");
        }
        return result;
    }

    /**
     * 商家按合法履约顺序推进订单状态。
     *
     * <p>先做细粒度数据范围校验：订单属私有数据，越权与不存在对外表现一致，
     * 统一返回 404；再校验状态机（待支付→已支付由支付回调独占，此处必然拒绝）。</p>
     *
     * @param operatorId 当前登录操作者 ID
     * @param orderId 订单 ID
     * @param targetStatus 目标状态
     * @return 订单状态变更结果
     */
    @Override
    public OrderStatusVO advanceOrderStatus(
            Long operatorId,
            Long orderId,
            OrderStatus targetStatus
    ) {
        if (targetStatus == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "订单目标状态不能为空"
            );
        }

        OrderDO orderDO = getOrderForStatusChange(orderId);

        // 订单属私有数据：不在操作者范围内时按不存在处理，避免探测订单是否存在。
        if (!dataScopeService.canAccessWindow(
                operatorId,
                orderDO.getWindowId()
        )) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }

        OrderStatus currentStatus = getCurrentStatus(orderDO);
        if (!currentStatus.canBeAdvancedByManagerTo(targetStatus)) {
            // 非法状态推进也写审计记录，便于追踪管理端的拒绝操作。
            auditLogger.record(
                    operatorId,
                    ORDER_STATUS_ADVANCE,
                    ORDER_TARGET_TYPE,
                    orderId,
                    currentStatus.getLabel(),
                    targetStatus.getLabel(),
                    false
            );
            throw invalidStatusTransition();
        }

        // 先确认数据库状态迁移成功，再记录成功审计，避免审计日志领先于真实状态。
        OrderStatusVO statusVO = persistStatusTransition(
                orderDO,
                targetStatus
        );
        auditLogger.record(
                operatorId,
                ORDER_STATUS_ADVANCE,
                ORDER_TARGET_TYPE,
                orderId,
                currentStatus.getLabel(),
                targetStatus.getLabel(),
                true
        );
        return statusVO;
    }

    /**
     * 支付回调驱动的内部订单推进：待支付到已支付。
     *
     * <p>并发场景下两个回调同时到达时，乐观锁只允许一个赢家；
     * 输家回读订单，若订单已被推进为已支付则按幂等成功处理。</p>
     *
     * @param orderId 订单 ID
     * @return 订单状态变更结果
     */
    @Override
    public OrderStatusVO markOrderPaid(Long orderId) {
        // 支付通知可能重放或并发到达，先读取状态并按状态机判定是否允许推进。
        OrderDO orderDO = getOrderForStatusChange(orderId);
        OrderStatus currentStatus = getCurrentStatus(orderDO);

        if (currentStatus == OrderStatus.PAID) {
            // 重复的支付成功通知按幂等成功处理。
            return OrderStatusVO.builder()
                    .orderId(orderId)
                    .status(currentStatus.getLabel())
                    .build();
        }
        if (currentStatus != OrderStatus.PENDING_PAYMENT
                || !currentStatus.canTransitionTo(OrderStatus.PAID)) {
            throw invalidStatusTransition();
        }

        try {
            // 状态与版本号条件更新用于竞争唯一写入权，防止重复通知重复推进订单。
            return persistStatusTransition(orderDO, OrderStatus.PAID);
        } catch (BusinessException conflictException) {
            // 并发回调竞争失败时回读订单：已被推进为已支付则幂等成功。
            OrderDO latestOrder = getById(orderId);
            if (latestOrder != null
                    && OrderStatus.PAID.getLabel().equals(latestOrder.getStatus())) {
                return OrderStatusVO.builder()
                        .orderId(orderId)
                        .status(OrderStatus.PAID.getLabel())
                        .build();
            }
            throw conflictException;
        }
    }

    /**
     * 查询待变更状态的订单；订单不存在时返回统一 404。
     *
     * @param orderId 订单 ID
     * @return 订单持久化对象
     */
    private OrderDO getOrderForStatusChange(Long orderId) {
        // 各状态变更入口复用此查询，统一处理订单缺失和对应 404 错误码。
        OrderDO orderDO = getById(orderId);
        if (orderDO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }
        return orderDO;
    }

    /**
     * 将数据库中的状态值转换为订单状态枚举。
     *
     * @param orderDO 订单持久化对象
     * @return 当前订单状态
     */
    private OrderStatus getCurrentStatus(OrderDO orderDO) {
        // 将数据库展示值映射回领域枚举，业务迁移规则集中由 OrderStatus 管理。
        OrderStatus currentStatus = OrderStatus.findByLabel(
                orderDO.getStatus()
        );
        if (currentStatus == null || orderDO.getVersion() == null) {
            // 数据库存量状态或版本号不符合约定时按内部数据异常处理。
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "订单状态数据异常"
            );
        }
        return currentStatus;
    }

    /**
     * 执行带旧状态及版本号条件的原子状态更新。
     *
     * @param orderDO 已读取的订单
     * @param targetStatus 已校验的目标状态
     * @return 订单状态变更结果
     */
    private OrderStatusVO persistStatusTransition(
            OrderDO orderDO,
            OrderStatus targetStatus
    ) {
        // 把读取到的旧状态和版本号一并交给持久化层，避免并发请求覆盖彼此的状态变更。
        boolean updated = orderPersistenceService.updateStatusIfUnchanged(
                orderDO.getOrderId(),
                orderDO.getStatus(),
                orderDO.getVersion(),
                targetStatus.getLabel()
        );
        if (!updated) {
            // 其他请求已先完成迁移，避免用旧状态覆盖新状态。
            throw invalidStatusTransition();
        }

        // 只有数据库确认受影响行数为 1 才返回变更成功。
        return OrderStatusVO.builder()
                .orderId(orderDO.getOrderId())
                .status(targetStatus.getLabel())
                .build();
    }

    /**
     * 创建统一的订单状态冲突异常。
     *
     * @return 状态不合法或并发更新冲突异常
     */
    private BusinessException invalidStatusTransition() {
        return new BusinessException(
                CommonErrorCode.CONFLICT,
                "订单当前状态不允许执行该操作"
        );
    }

    /**
     * 校验幂等键是否符合数据库列约束。
     *
     * @param idempotencyKey 客户端幂等键
     */
    private void validateIdempotencyKey(String idempotencyKey) {
        // 输入约束与数据库幂等键列保持一致，避免空键或过长键进入持久化流程。
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "Idempotency-Key 不能为空且不能超过 128 个字符"
            );
        }
    }

    /**
     * 校验订单业务字段并拒绝重复菜品行。
     *
     * @param request 创建订单请求
     */
    private void validateCreateRequest(OrderCreateRequest request) {
        // 校验窗口、明细集合及单次明细数量，防止空订单和超量请求进入核价链路。
        if (request == null
                || request.getWindowId() == null
                || request.getWindowId() <= 0
                || request.getItems() == null
                || request.getItems().isEmpty()
                || request.getItems().size() > MAX_ORDER_ITEMS) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "订单请求参数无效"
            );
        }

        // 同一订单内每种商品只允许一行；购买多个同类商品应通过 quantity 表达。
        Set<Long> productIds = new HashSet<>();
        for (OrderItemCreateRequest item : request.getItems()) {
            if (item == null
                    || item.getProductId() == null
                    || item.getProductId() <= 0
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0
                    || !productIds.add(item.getProductId())) {
                throw new BusinessException(
                        CommonErrorCode.BAD_REQUEST,
                        "菜品 ID、数量无效，且同一菜品不能重复提交"
                );
            }
        }
    }

    /**
     * 调用商品服务批量核价并校验统一响应。
     *
     * @param request 创建订单请求
     * @return 商品服务返回的可售菜品和当前价格
     */
    private List<ProductQuoteVO> queryProductQuotes(
            OrderCreateRequest request
    ) {
        // 仅把窗口和商品 ID 清单交给商品服务，由商品服务判定归属、可售状态和实际价格。
        ProductQuoteRequest quoteRequest = new ProductQuoteRequest();
        quoteRequest.setWindowId(request.getWindowId());
        List<Long> productIds = new ArrayList<>();
        for (OrderItemCreateRequest item : request.getItems()) {
            productIds.add(item.getProductId());
        }
        quoteRequest.setProductIds(productIds);

        ApiResponse<ProductQuoteResponse> response =
                productServiceClient.quote(quoteRequest);
        // 校验远程服务的统一响应结构，防止失败响应或空数据被继续当作有效报价使用。
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            String message = response == null ? null : response.getMessage();
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    message == null || message.isBlank()
                            ? "商品服务暂时不可用"
                            : message
            );
        }

        ProductQuoteResponse quoteResponse = response.getData();
        // 窗口停用属于明确业务拒绝；缺少商品列表则说明核价结果不完整。
        if (!quoteResponse.isWindowActive()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "所选窗口当前不可下单"
            );
        }
        if (quoteResponse.getProducts() == null) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "商品服务返回了无效的核价结果"
            );
        }
        return quoteResponse.getProducts();
    }

    /**
     * 将请求菜品和商品核价结果组装为订单明细快照。
     *
     * @param request 创建订单请求
     * @param productQuotes 商品服务核价结果
     * @return 已计算行金额的订单明细列表
     */
    private List<OrderItemDO> buildOrderItems(
            OrderCreateRequest request,
            List<ProductQuoteVO> productQuotes
    ) {
        // 按商品 ID 建立报价索引，之后按客户端请求顺序组装订单行。
        Map<Long, ProductQuoteVO> quoteByProductId = new HashMap<>();
        for (ProductQuoteVO quote : productQuotes) {
            if (quote == null
                    || quote.getProductId() == null
                    || quoteByProductId.putIfAbsent(
                            quote.getProductId(),
                            quote
                    ) != null) {
                throw new BusinessException(
                        CommonErrorCode.SERVICE_UNAVAILABLE,
                        "商品服务返回了无效的核价结果"
                );
            }
        }

        List<OrderItemDO> items = new ArrayList<>();
        for (OrderItemCreateRequest requestedItem : request.getItems()) {
            // 任一请求商品缺少报价都整体拒绝，不静默跳过菜品以免总金额与顾客意图不符。
            ProductQuoteVO quote = quoteByProductId.get(
                    requestedItem.getProductId()
            );
            if (quote == null) {
                throw new BusinessException(
                        CommonErrorCode.BAD_REQUEST,
                        "存在无法购买的菜品，请刷新菜单后重试"
                );
            }
            if (quote.getProductCode() == null
                    || quote.getProductName() == null
                    || quote.getPrice() == null
                    || quote.getPrice().signum() < 0) {
                throw new BusinessException(
                        CommonErrorCode.SERVICE_UNAVAILABLE,
                        "商品服务返回了无效的菜品信息"
                );
            }

            // 保存商品编码、名称、单价和行金额快照，使历史订单不受菜单变更影响。
            BigDecimal lineAmount = quote.getPrice()
                    .multiply(BigDecimal.valueOf(requestedItem.getQuantity()));
            OrderItemDO item = new OrderItemDO();
            item.setProductId(quote.getProductId());
            item.setProductCodeSnapshot(quote.getProductCode());
            item.setProductNameSnapshot(quote.getProductName());
            item.setUnitPriceSnapshot(quote.getPrice());
            item.setQuantity(requestedItem.getQuantity());
            item.setLineAmount(lineAmount);
            item.setCreatedAt(LocalDateTime.now());
            items.add(item);
        }
        return items;
    }

    /**
     * 组装服务端控制的订单持久化字段。
     *
     * @param userId 当前用户 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 创建订单请求
     * @param requestHash 规范化请求摘要
     * @param totalAmount 服务端计算的订单总金额
     * @return 新订单持久化对象
     */
    private OrderDO buildOrder(
            Long userId,
            String idempotencyKey,
            OrderCreateRequest request,
            String requestHash,
            BigDecimal totalAmount
    ) {
        // 订单号包含时间和随机后缀；并发幂等仍依赖用户 ID 与幂等键的数据库唯一约束。
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        OrderDO order = new OrderDO();
        order.setOrderNo("ORD" + time + random);
        order.setUserId(userId);
        order.setWindowId(request.getWindowId());
        order.setTotalAmount(totalAmount);
        order.setRemark(request.getRemark());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestHash(requestHash);
        order.setStatus(INITIAL_ORDER_STATUS);
        order.setVersion(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }

    /**
     * 检查重放请求摘要并返回已保存的订单及明细。
     *
     * @param existingOrder 已有订单
     * @param requestHash 本次请求摘要
     * @return 已有订单详情
     */
    private OrderDetailVO replayOrder(
            OrderDO existingOrder,
            String requestHash
    ) {
        // 同一个幂等键只能重放相同请求；若请求摘要不同，返回冲突而不是复用旧订单。
        if (!requestHash.equals(existingOrder.getRequestHash())) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "Idempotency-Key 已用于不同的订单请求"
            );
        }
        return OrderDetailVO.from(
                existingOrder,
                orderItemMapper.selectByOrderId(existingOrder.getOrderId())
        );
    }

    /**
     * 对窗口、菜品 ID 和数量、备注生成稳定摘要；菜品顺序不影响请求语义。
     *
     * @param request 创建订单请求
     * @return 请求内容的 SHA-256 摘要
     */
    private String calculateRequestHash(OrderCreateRequest request) {
        try {
            // 采用 SHA-256 对参与订单语义的字段生成摘要，不将幂等键本身纳入摘要内容。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, request.getWindowId().toString());

            // 先按商品 ID 排序，使商品行排列顺序不同但内容相同的请求得到相同摘要。
            List<OrderItemCreateRequest> sortedItems = new ArrayList<>(
                    request.getItems()
            );
            sortedItems.sort(Comparator.comparing(
                    OrderItemCreateRequest::getProductId
            ));
            updateDigest(digest, Integer.toString(sortedItems.size()));
            for (OrderItemCreateRequest item : sortedItems) {
                updateDigest(digest, item.getProductId().toString());
                updateDigest(digest, item.getQuantity().toString());
            }

            // 显式写入备注存在标记，区分 null 与空字符串这两种不同输入。
            String remark = request.getRemark();
            if (remark == null) {
                digest.update((byte) 0);
            } else {
                digest.update((byte) 1);
                updateDigest(digest, remark);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 是 Java 标准算法；若运行环境缺失，不降级为弱摘要，直接报告环境错误。
            throw new IllegalStateException(
                    "当前 Java 环境不支持 SHA-256",
                    exception
            );
        }
    }

    /**
     * 按 UTF-8 编码并使用长度前缀写入摘要，避免字段拼接歧义。
     *
     * @param digest SHA-256 摘要器
     * @param value 请求字段
     */
    private void updateDigest(MessageDigest digest, String value) {
        // 每个字段前写入 UTF-8 字节长度，避免直接拼接导致字段边界不明确。
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length)
                .array());
        digest.update(bytes);
    }

    /**
     * 查询当前用户的订单列表。
     *
     * @param userId 当前用户 ID
     * @return 订单列表
     */
    @Override
    public List<OrderDO> listByUserId(Long userId) {
        // 查询条件限定为当前用户 ID，避免列表接口跨用户暴露订单。
        return baseMapper.selectByUserId(userId);
    }
}
