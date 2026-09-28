package com.hbue.ordering.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.dto.request.OrderItemCreateRequest;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.service.OrderPersistenceService;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import com.hbue.ordering.user.api.client.UserServiceClient;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

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

    /** 单个订单允许的最大菜品行数。 */
    private static final int MAX_ORDER_ITEMS = OrderCreateRequest.MAX_ITEM_COUNT;

    /** 新订单的初始状态。 */
    private static final String INITIAL_ORDER_STATUS = "待支付";

    /** 订单表 DECIMAL(10,2) 能保存的最大金额。 */
    private static final BigDecimal MAX_ORDER_AMOUNT =
            new BigDecimal("99999999.99");

    /** 用户服务远程客户端。 */
    private final UserServiceClient userServiceClient;

    /** 商品服务核价客户端。 */
    private final ProductServiceClient productServiceClient;

    /** 订单头和明细原子持久化服务。 */
    private final OrderPersistenceService orderPersistenceService;

    /** 订单明细 Mapper。 */
    private final OrderItemMapper orderItemMapper;

    /**
     * 创建订单业务服务。
     *
     * @param userServiceClient 用户服务远程客户端
     * @param productServiceClient 商品服务核价客户端
     * @param orderPersistenceService 订单原子持久化服务
     * @param orderItemMapper 订单明细 Mapper
     */
    public OrderServiceImpl(
            UserServiceClient userServiceClient,
            ProductServiceClient productServiceClient,
            OrderPersistenceService orderPersistenceService,
            OrderItemMapper orderItemMapper
    ) {
        this.userServiceClient = userServiceClient;
        this.productServiceClient = productServiceClient;
        this.orderPersistenceService = orderPersistenceService;
        this.orderItemMapper = orderItemMapper;
    }

    /**
     * 远程查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
     */
    @Override
    public UserBasicVO getUserBasic(Long userId) {
        ApiResponse<UserBasicVO> response = userServiceClient.getUserById(userId);
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
        OrderDO orderDO = getById(orderId);
        if (orderDO == null || !userId.equals(orderDO.getUserId())) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }

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
     * @param userId 当前登录用户 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 窗口、菜品清单和备注
     * @return 新订单详情；同键同请求时返回已有订单
     */
    @Override
    public OrderDetailVO createOrder(
            Long userId,
            String idempotencyKey,
            OrderCreateRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateCreateRequest(request);

        String requestHash = calculateRequestHash(request);
        OrderDO existingOrder = baseMapper.selectByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        );
        if (existingOrder != null) {
            return replayOrder(existingOrder, requestHash);
        }

        List<ProductQuoteVO> productQuotes = queryProductQuotes(request);
        List<OrderItemDO> orderItems = buildOrderItems(request, productQuotes);
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

        UserBasicVO userBasicVO = getUserBasic(userId);
        if (userBasicVO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在"
            );
        }

        OrderDO orderDO = buildOrder(
                userId,
                idempotencyKey,
                request,
                requestHash,
                totalAmount
        );

        try {
            orderPersistenceService.saveOrderWithItems(orderDO, orderItems);
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
     * 校验幂等键是否符合数据库列约束。
     *
     * @param idempotencyKey 客户端幂等键
     */
    private void validateIdempotencyKey(String idempotencyKey) {
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
        ProductQuoteRequest quoteRequest = new ProductQuoteRequest();
        quoteRequest.setWindowId(request.getWindowId());
        List<Long> productIds = new ArrayList<>();
        for (OrderItemCreateRequest item : request.getItems()) {
            productIds.add(item.getProductId());
        }
        quoteRequest.setProductIds(productIds);

        ApiResponse<ProductQuoteResponse> response =
                productServiceClient.quote(quoteRequest);
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, request.getWindowId().toString());

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

            String remark = request.getRemark();
            if (remark == null) {
                digest.update((byte) 0);
            } else {
                digest.update((byte) 1);
                updateDigest(digest, remark);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
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
        return baseMapper.selectByUserId(userId);
    }
}
