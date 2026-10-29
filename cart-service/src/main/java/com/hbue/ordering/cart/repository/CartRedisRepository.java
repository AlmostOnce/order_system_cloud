package com.hbue.ordering.cart.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.cart.config.CartProperties;
import com.hbue.ordering.cart.dto.request.CartSubmittedItemRequest;
import com.hbue.ordering.cart.model.CartItemDO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 购物车 Redis 持久化访问层，只负责存取与原子修改，不调用商品服务。
 *
 * <p>业务专用 Lua 保留在购物车模块，避免把购物车规则混入 common-redis。读取使用
 * HGETALL 取得单个用户的一致快照；修改在一个脚本中完成检查、写入与 TTL 续期。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@Repository
public class CartRedisRepository {
    /** Redis 错误仅在持久化边界记录，不记录 Token 或请求密码。 */
    private static final Logger log = LoggerFactory.getLogger(CartRedisRepository.class);
    /** 脚本返回：目标条目不存在。 */
    private static final long MISSING = -1;
    /** 脚本返回：新增菜品会超过购物车容量。 */
    private static final long CAPACITY_EXCEEDED = -2;
    /** 脚本返回：最终数量超过单项限制。 */
    private static final long QUANTITY_EXCEEDED = -3;
    /** 脚本返回：保存的数据不符合预期结构。 */
    private static final long CORRUPT = -4;
    /** 普通购物车增删改使用同一段单 Key 原子脚本；下单清理使用独立比较删除脚本。 */
    private static final DefaultRedisScript<Long> MUTATE_SCRIPT = mutationScript();
    /** 提交快照清理脚本，版本比较与删除位于同一 Redis 原子操作。 */
    private static final DefaultRedisScript<Long> CLEANUP_SCRIPT = cleanupScript();
    /** 同步 Redis 模板，只在 Servlet 请求线程执行。 */
    private final RedisOperationsService redis;
    /** JSON 反序列化组件，与 HTTP 响应使用相同的数字类型约定。 */
    private final ObjectMapper objectMapper;
    /** 购物车命名空间、容量与过期配置。 */
    private final CartProperties properties;

    /**
     * 创建购物车存储访问组件。
     *
     * @param redis 公共 Redis 操作组件
     * @param objectMapper JSON 反序列化组件
     * @param properties 购物车存储约束
     */
    public CartRedisRepository(RedisOperationsService redis, ObjectMapper objectMapper, CartProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 读取并按窗口、菜品 ID 排序，读取不续期。
     *
     * @param userId 当前用户 ID
     * @return 所有保存的条目，缺少 Key 时为空列表
     * @throws BusinessException Redis 故障或数据无法解析时返回 503
     */
    public List<CartItemDO> findAll(Long userId) {
        try {
            Map<Object, Object> values = redis.getHashValues(key(userId));
            List<CartItemDO> items = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : values.entrySet()) {
                CartItemDO item = objectMapper.readValue(entry.getValue().toString(), CartItemDO.class);
                // 校验 Field 与内容一致，避免坏数据被当成其它窗口或菜品展示。
                if (item == null || item.getWindowId() == null || item.getWindowId() < 1
                        || item.getProductId() == null || item.getProductId() < 1
                        || item.getQuantity() == null || item.getQuantity() < 1 || item.getChecked() == null
                        || item.getRevision() == null || item.getRevision().isBlank()
                        || !field(item.getWindowId(), item.getProductId()).equals(entry.getKey())) {
                    throw unavailable("购物车数据异常，请稍后重试");
                }
                items.add(item);
            }
            items.sort(Comparator.comparing(CartItemDO::getWindowId).thenComparing(CartItemDO::getProductId));
            return items;
        } catch (DataAccessException | JsonProcessingException exception) {
            log.error("读取购物车失败，userId={}", userId, exception);
            throw unavailable("购物车暂时不可用，请稍后重试");
        }
    }

    /**
     * 原子加入菜品，并从商品事实来源保存展示快照。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param quantity 本次增加数量
     * @param productName 商品服务确认的名称
     * @param productCode 商品服务确认的编码
     * @throws BusinessException 数量或容量超限时返回 400，Redis 故障时返回 503
     */
    public void add(Long userId, Long windowId, Long productId, int quantity, String productName, String productCode) {
        mutate(userId, windowId, productId, "add", String.valueOf(quantity), productName, productCode);
    }

    /**
     * 设置已有条目的目标数量；脚本保留其它字段，避免覆盖并发勾选。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param quantity 目标数量
     */
    public void updateQuantity(Long userId, Long windowId, Long productId, int quantity) {
        mutate(userId, windowId, productId, "quantity", String.valueOf(quantity), "", "");
    }

    /**
     * 更新已有条目的勾选状态，保留同时提交的数量变更。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param checked 目标勾选状态
     */
    public void updateChecked(Long userId, Long windowId, Long productId, boolean checked) {
        mutate(userId, windowId, productId, "checked", String.valueOf(checked), "", "");
    }

    /**
     * 幂等删除单项，最后一项被移除时 Redis 会自动删除空 Hash。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     */
    public void remove(Long userId, Long windowId, Long productId) {
        mutate(userId, windowId, productId, "remove", "", "", "");
    }

    /**
     * 只清空指定窗口，不影响同一用户其它窗口及其它用户。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     */
    public void clearWindow(Long userId, Long windowId) {
        mutate(userId, windowId, 0L, "clearWindow", "", "", "");
    }

    /**
     * 按原始版本、数量及勾选状态删除已购买条目；缺少或变更条目跳过且不续期。
     * 调用前由 Service 验证订单归属、实际明细和快照参数。
     * @param userId 当前顾客 ID
     * @param windowId 本次订单窗口
     * @param submitted 下单前保存的完整购买快照
     * @return 实际删除条目数；重复清理或全部发生变化时为零
     */
    public long removeSubmitted(Long userId, Long windowId, List<CartSubmittedItemRequest> submitted) {
        try {
            List<Map<String, Object>> snapshots = new ArrayList<>();
            for (CartSubmittedItemRequest item : submitted) {
                // ID 明确编码成字符串，避免 Lua cjson 将 BIGINT 转成双精度数丢失精度。
                snapshots.add(Map.of("productId", item.productId().toString(),
                        "quantity", item.quantity(), "revision", item.revision()));
            }
            Long removed = redis.executeScript(CLEANUP_SCRIPT, List.of(key(userId)), windowId.toString(),
                    objectMapper.writeValueAsString(snapshots));
            if (removed == null || removed < 0 || removed > submitted.size()) {
                throw unavailable("购物车清理状态无法确认，请保留原始快照重试");
            }
            return removed;
        } catch (DataAccessException | JsonProcessingException exception) {
            log.error("清理已下单购物车失败，userId={}", userId, exception);
            throw unavailable("购物车暂时无法清理，请保留原始快照重试，不要重复下单");
        }
    }

    /**
     * 加载独立的安全清理脚本，不影响普通增删改查的语义。
     * @return 返回删除数量或错误标识的 Redis 脚本
     */
    private static DefaultRedisScript<Long> cleanupScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/cart-cleanup.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 执行单用户原子修改并转换脚本返回码。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID，清空窗口时为占位值零
     * @param action Lua 中的操作类型
     * @param value 数量或勾选状态
     * @param name 名称快照，非添加操作时为空
     * @param code 编码快照，非添加操作时为空
     * @throws BusinessException 业务约束拒绝或 Redis 不可用时抛出
     */
    private void mutate(Long userId, Long windowId, Long productId, String action,
                        String value, String name, String code) {
        try {
            // UUID 每次重新生成，后续删除重建也不会出现旧版本被误认为新版本的情况。
            Long result = redis.executeScript(MUTATE_SCRIPT, List.of(key(userId)), action, field(windowId, productId),
                    windowId.toString(), productId.toString(), value, UUID.randomUUID().toString(), name, code,
                    String.valueOf(properties.maxItems()), String.valueOf(properties.maxQuantity()),
                    String.valueOf(properties.timeToLive().toMillis()));
            if (result == null || result == CORRUPT) {
                throw unavailable("购物车数据状态无法确认，请稍后重试");
            }
            if (result == MISSING) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND, "购物车中不存在该菜品");
            }
            if (result == CAPACITY_EXCEEDED) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "购物车菜品种类已达上限");
            }
            if (result == QUANTITY_EXCEEDED) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "菜品数量超出购物车限制");
            }
        } catch (DataAccessException exception) {
            log.error("修改购物车失败，userId={}", userId, exception);
            throw unavailable("购物车暂时不可用，请稍后重试");
        }
    }

    /**
     * 生成用户隔离的 Redis Key，阻止空身份落入共享 Key。
     *
     * @param userId 当前用户 ID
     * @return 配置前缀与用户 ID 组成的 Key
     */
    private String key(Long userId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录用户身份无效");
        }
        return properties.keyPrefix() + userId;
    }

    /**
     * 构造单用户 Hash 内的菜品唯一标识。
     *
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @return 带分隔符的窗口与菜品组合标识
     */
    private String field(Long windowId, Long productId) {
        return windowId + ":" + productId;
    }

    /**
     * 从类路径加载脚本，Spring Data Redis 自动管理脚本 SHA 与 EVALSHA 回退。
     *
     * @return 购物车写入脚本
     */
    private static DefaultRedisScript<Long> mutationScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/cart-mutate.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 构造基础设施不可用响应，不将内部 Redis 信息暴露给客户端。
     *
     * @param message 面向客户端的提示
     * @return HTTP 503 对应业务异常
     */
    private BusinessException unavailable(String message) {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
