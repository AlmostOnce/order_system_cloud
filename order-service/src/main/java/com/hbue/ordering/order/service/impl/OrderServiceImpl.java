package com.hbue.ordering.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.user.api.client.UserServiceClient;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 订单业务服务实现。
 *
 * @author order-system
 * @date 2026-09-19
 */
@Service
public class OrderServiceImpl
        extends ServiceImpl<OrderMapper, OrderDO>
        implements OrderService {

    private final UserServiceClient userServiceClient;

    /**
     * 创建订单业务服务。
     *
     * @param userServiceClient 用户服务远程客户端
     */
    public OrderServiceImpl(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    /**
     * 远程查询用户基础信息。
     *
     * @param userId 用户 ID
     * @return 用户基础信息
     */
    @Override
    public UserBasicVO getUserBasic(Long userId) {
        // 调用用户服务。
        ApiResponse<UserBasicVO> response =
                userServiceClient.getUserById(userId);

        // 返回用户服务响应中的业务数据。
        return response.getData();
    }

    /**
     * 查询当前用户的订单详情。
     *
     * @param userId 当前登录用户 ID
     * @param orderId 订单 ID
     * @return 订单详情
     */
    @Override
    public OrderDetailVO getOrderDetailById(
            Long userId,
            Long orderId
    ) {
        // 查询订单数据。
        OrderDO orderDO = getById(orderId);

        // 订单不存在时返回资源不存在。
        if (orderDO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }

        // 订单不属于当前用户时，也返回资源不存在。
        // 这样可以避免泄露其他用户是否拥有该订单。
        if (!userId.equals(orderDO.getUserId())) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }

        // 查询当前订单所属用户信息。
        UserBasicVO userBasicVO =
                getUserBasic(orderDO.getUserId());

        // 组装订单和用户信息。
        return OrderDetailVO.from(orderDO, userBasicVO);
    }

    /**
     * 创建订单。
     *
     * @param userId 当前登录用户 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 创建订单请求
     * @return 新创建的订单详情
     */
    @Override
    public OrderDetailVO createOrder(
            Long userId,
            String idempotencyKey,
            OrderCreateRequest request
    ) {
        // 幂等键是创建订单的必需标识，并限制长度以匹配数据库列。
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "Idempotency-Key 不能为空且不能超过 128 个字符"
            );
        }

        // 同一用户使用相同幂等键时，只允许重放相同请求内容。
        String requestHash = calculateRequestHash(request);
        OrderDO existingOrder = baseMapper.selectByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        );
        if (existingOrder != null) {
            return replayOrder(existingOrder, requestHash);
        }

        // 先通过用户服务确认用户存在。
        UserBasicVO userBasicVO = getUserBasic(userId);

        // 用户不存在或用户服务不可用时，终止创建订单。
        if (userBasicVO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在或用户服务暂时不可用"
            );
        }

        // 创建订单持久化对象。
        OrderDO orderDO = new OrderDO();

        // 生成订单编号。
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        orderDO.setOrderNo("ORD" + time + random);

        // 设置客户端允许提交的订单字段。
        orderDO.setUserId(userId);
        orderDO.setWindowId(request.getWindowId());
        orderDO.setTotalAmount(request.getTotalAmount());
        orderDO.setRemark(request.getRemark());
        orderDO.setIdempotencyKey(idempotencyKey);
        orderDO.setRequestHash(requestHash);

        // 设置服务端控制的初始字段。
        orderDO.setStatus("待支付");
        orderDO.setVersion(0);
        orderDO.setCreatedAt(LocalDateTime.now());
        orderDO.setUpdatedAt(LocalDateTime.now());

        // 数据库唯一约束是并发重试的最终裁决；重复键时读取已成功创建的订单。
        try {
            if (!save(orderDO)) {
                throw new BusinessException(
                        CommonErrorCode.INTERNAL_ERROR,
                        "订单创建失败"
                );
            }
        } catch (DuplicateKeyException exception) {
            OrderDO concurrentOrder =
                    baseMapper.selectByUserIdAndIdempotencyKey(
                            userId,
                            idempotencyKey
                    );

            // 如果重复键来自订单号等其他唯一约束，则保留原始数据库异常。
            if (concurrentOrder == null) {
                throw exception;
            }

            return replayOrder(concurrentOrder, requestHash);
        }

        // 返回新创建的订单详情。
        return OrderDetailVO.from(orderDO, userBasicVO);
    }

    /**
     * 返回已创建的订单，并检查重放请求内容是否一致。
     *
     * @param existingOrder 已存在的订单
     * @param requestHash 当前请求摘要
     * @return 已存在订单的详情
     */
    private OrderDetailVO replayOrder(
            OrderDO existingOrder,
            String requestHash
    ) {
        // 相同幂等键不能被复用于不同订单内容。
        if (!requestHash.equals(existingOrder.getRequestHash())) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "Idempotency-Key 已用于不同的订单请求"
            );
        }

        // 直接从持久化订单构造响应，重试时不依赖用户服务再次可用。
        return OrderDetailVO.from(existingOrder);
    }

    /**
     * 生成创建订单请求的 SHA-256 摘要。
     *
     * @param request 创建订单请求
     * @return 64 位十六进制摘要
     */
    private String calculateRequestHash(OrderCreateRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 将每个字段按长度编码，避免不同字段组合产生相同输入串。
            updateDigest(digest, request.getWindowId().toString());
            updateDigest(
                    digest,
                    request.getTotalAmount()
                            .stripTrailingZeros()
                            .toPlainString()
            );

            // null 与空备注代表不同请求，使用单独标记保留差异。
            String remark = request.getRemark();
            if (remark == null) {
                digest.update((byte) 0);
            } else {
                digest.update((byte) 1);
                updateDigest(digest, remark);
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            // Java 运行环境必须提供 SHA-256；缺少算法属于运行环境错误。
            throw new IllegalStateException(
                    "当前 Java 环境不支持 SHA-256",
                    exception
            );
        }
    }

    /**
     * 按 UTF-8 编码并使用长度前缀写入摘要。
     *
     * @param digest SHA-256 摘要器
     * @param value 请求字段
     */
    private void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(
                ByteBuffer.allocate(Integer.BYTES)
                        .putInt(bytes.length)
                        .array()
        );
        digest.update(bytes);
    }

    /**
     * 查询当前用户的订单列表。
     *
     * @param userId 当前登录用户 ID
     * @return 当前用户的订单列表
     */
    @Override
    public List<OrderDO> listByUserId(Long userId) {
        // 只查询当前用户自己的订单。
        return baseMapper.selectByUserId(userId);
    }
}
