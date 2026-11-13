package com.hbue.ordering.statistics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;
import com.hbue.ordering.statistics.mapper.SalesProjectionMapper;
import com.hbue.ordering.statistics.messaging.SalesPaidEvent;
import com.hbue.ordering.statistics.model.SalesProductTotalDO;
import com.hbue.ordering.statistics.vo.SalesRankingVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 用 Redis ZSet 提供累计销量排序，并以 statistics_db 的 MySQL 汇总作为唯一重建来源。
 *
 * <p>所有 Redis 命令统一经过 common-redis 的 RedisOperationsService；Lua 将事件去重、ZSet 更新和缓存版本
 * 一起原子提交。Redis 不可用时消息不确认，MQ 可重投；周期重建会修复死信或缓存丢失后的排名。</p>
 */
@Service
public class SalesRankingCacheService {
    private static final Logger log = LoggerFactory.getLogger(SalesRankingCacheService.class);
    private static final String DATA = "statistics:ranking:{sales:v3}:data";
    private static final String META = "statistics:ranking:{sales:v3}:meta";
    private static final String SEEN = "statistics:ranking:{sales:v3}:seen";
    private static final String READY = "statistics:ranking:{sales:v3}:ready";
    private static final String GENERATION = "statistics:ranking:{sales:v3}:generation";
    private static final String UPDATED = "statistics:ranking:{sales:v3}:updated";
    private static final String LOCK = "statistics:ranking:{sales:v3}:lock";
    private static final List<String> KEYS = List.of(DATA, META, SEEN, READY, GENERATION, UPDATED);
    private static final long MAX_EXACT = 9_007_199_254_740_991L;
    private static final DefaultRedisScript<Long> APPLY = script("apply-sales-v3.lua", Long.class);
    private static final DefaultRedisScript<Long> REBUILD = script("rebuild-sales-v3.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> READ = listScript("read-sales-v3.lua");

    private final RedisOperationsService redis;
    private final DistributedLockService locks;
    private final SalesProjectionMapper projection;
    private final ObjectMapper json;

    /** 注入统一 Redis 封装、分布式互斥和 MySQL 汇总读取能力。 */
    public SalesRankingCacheService(RedisOperationsService redis, DistributedLockService locks,
                                    SalesProjectionMapper projection, ObjectMapper json) {
        this.redis = redis;
        this.locks = locks;
        this.projection = projection;
        this.json = json;
    }

    /**
     * 读取累计榜；如果缓存不存在或版本落后，在分布式锁内从 MySQL 重建后再返回。
     *
     * @param limit 学生端需要的榜单数量，限定1至100避免无界缓存读取
     * @return 按 ZSet 分数降序生成的榜单
     */
    public SalesRankingVO list(int limit) {
        if (limit < 1 || limit > 100) throw new BusinessException(CommonErrorCode.BAD_REQUEST, "榜单数量应在 1 到 100 之间");
        long version = currentVersion();
        try {
            SalesRankingVO result = read(limit, version);
            if (result != null) return result;
            try (var lock = locks.tryAcquire(LOCK, 2, 30)) {
                version = currentVersion();
                result = read(limit, version);
                if (result != null) return result;
                if (lock == null) throw unavailable();
                rebuildUnderLock(version);
                result = read(limit, version);
                if (result == null) throw unavailable();
                return result;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("读取累计销量榜失败", exception);
            throw unavailable();
        }
    }

    /**
     * 在销量事实事务提交后增量维护 Redis；若缓存落后超过当前事件一个版本，则直接按 MySQL 全量修复。
     *
     * @param event 已验证的稳定订单事件
     * @param snapshot 事件对应的原始购买快照
     * @param eventProjectionVersion 持久化事件事务提交后的 MySQL 版本
     */
    public void apply(SalesPaidEvent event, OrderPaidSalesSnapshotVO snapshot, long eventProjectionVersion) {
        try (var lock = locks.tryAcquire(LOCK, 2, 30)) {
            if (lock == null) throw unavailable();
            long latestVersion = currentVersion();
            long cachedVersion = parseVersion(redis.getValue(READY));
            if (cachedVersion >= latestVersion) return;

            // 并发消费可能让消息乱序；只有 Redis 正好落后一版且当前事件仍是最新版本才做增量。
            if (cachedVersion != latestVersion - 1 || eventProjectionVersion != latestVersion) {
                rebuildUnderLock(latestVersion);
                return;
            }

            List<Group> groups = group(snapshot);
            List<String> args = new ArrayList<>();
            args.add(nextGeneration());
            args.add(event.orderId().toString());
            args.add(Instant.now().toString());
            args.add(Long.toString(latestVersion));
            for (Group group : groups) {
                args.add(member(group.productId, snapshot.windowId()));
                args.add(Long.toString(group.quantity));
                args.add(metadata(group.productId, snapshot.windowId(), group.latest));
            }
            Long result = redis.executeScript(APPLY, KEYS, args.toArray());
            if (result == null || result < 0) throw unavailable();
        } catch (RuntimeException exception) {
            // 让 RocketMQ 重投；MySQL 已提交时事件幂等会阻止重复增加，Redis Lua 负责补齐遗漏更新。
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("销量榜元数据编码失败", exception);
        }
    }

    /** 周期从权威 MySQL 汇总全量重建，修复 MQ 死信或 Redis 清理导致的投影遗漏。 */
    @Scheduled(initialDelayString = "${statistics.sales.reconcile-delay-ms:300000}",
            fixedDelayString = "${statistics.sales.reconcile-delay-ms:300000}")
    public void reconcile() {
        try (var lock = locks.tryAcquire(LOCK, 0, 30)) {
            if (lock != null) rebuildUnderLock(currentVersion());
        } catch (Exception exception) {
            log.error("累计销量榜重建失败，保留现有缓存并等待下次对账", exception);
        }
    }

    /** 在已经持有分布式锁的条件下，以 MySQL 事实重建完整 ZSet、元数据、去重集合和缓存版本。 */
    private void rebuildUnderLock(long version) throws JsonProcessingException {
        List<SalesProductTotalDO> rows = projection.selectAllTimeProductTotals();
        List<Long> orderIds = projection.selectAllEventOrderIds();
        if (rows == null || orderIds == null) throw unavailable();
        String generation = nextGeneration();
        List<String> args = new ArrayList<>();
        args.add(generation);
        args.add(Long.toString(version));
        args.add(Instant.now().toString());
        args.add(Integer.toString(orderIds.size()));
        // shortcut: 当前开发数据规模下事件ID一次传给Lua；接近Redis脚本参数/消息体限制前需改为影子Key分批重建。
        for (Long orderId : orderIds) args.add(orderId.toString());
        for (SalesProductTotalDO row : rows) {
            if (row.getPaidQuantity() == null || row.getPaidQuantity() <= 0 || row.getPaidQuantity() > MAX_EXACT
                    || row.getLatestItemId() == null || row.getLatestItemId() <= 0) throw unavailable();
            args.add(member(row.getProductId(), row.getWindowId()));
            args.add(row.getPaidQuantity().toString());
            args.add(metadata(row.getProductId(), row.getWindowId(), row.getProductName(), row.getLatestItemId()));
        }
        Long rebuilt = redis.executeScript(REBUILD, KEYS, args.toArray());
        if (!Long.valueOf(1).equals(rebuilt)) throw unavailable();
    }

    /** 使用 Lua 一次读取榜单、元数据与分数；版本不匹配或元数据缺失时让调用方触发重建。 */
    private SalesRankingVO read(int limit, long version) throws JsonProcessingException {
        List<?> values = redis.executeScript(READ, KEYS, Integer.toString(limit), Long.toString(version));
        if (values == null || values.isEmpty()) return null;
        if ((values.size() - 1) % 2 != 0) throw unavailable();
        List<SalesRankingVO.Item> items = new ArrayList<>();
        for (int i = 1; i + 1 < values.size(); i += 2) {
            Object rawMetadata = values.get(i);
            Object rawScore = values.get(i + 1);
            if (!(rawMetadata instanceof String metadata) || metadata.length() < 22 || !(rawScore instanceof String score)) return null;
            SalesRankingVO.Item stored = json.readValue(metadata.substring(21), SalesRankingVO.Item.class);
            long sales = new BigDecimal(score).longValueExact();
            items.add(new SalesRankingVO.Item(items.size() + 1, stored.productId(), stored.windowId(), stored.productName(), sales));
        }
        return new SalesRankingVO((String) values.getFirst(), items);
    }

    /** 对同一订单中重复出现的菜品行分组，向 Redis 只发送一次菜品/窗口增量。 */
    private List<Group> group(OrderPaidSalesSnapshotVO snapshot) {
        java.util.LinkedHashMap<Long, Group> groups = new java.util.LinkedHashMap<>();
        for (OrderPaidSalesSnapshotVO.Item item : snapshot.items()) {
            Group group = groups.computeIfAbsent(item.productId(), Group::new);
            group.quantity = Math.addExact(group.quantity, item.quantity());
            if (group.latest == null || item.itemId() > group.latest.itemId()) group.latest = item;
        }
        for (Group group : groups.values()) if (group.quantity <= 0 || group.quantity > MAX_EXACT) throw unavailable();
        return new ArrayList<>(groups.values());
    }

    /** 将同一菜品在不同窗口分开排名，避免把不可互换的窗口菜品合并。 */
    private String member(Long productId, Long windowId) {
        if (productId == null || productId <= 0 || windowId == null || windowId <= 0) throw unavailable();
        return String.format(Locale.ROOT, "%020d:%020d", productId, windowId);
    }

    /** 元数据前缀记录最新订单明细 ID，乱序消费时 Lua 可避免旧菜名覆盖新快照。 */
    private String metadata(Long productId, Long windowId, OrderPaidSalesSnapshotVO.Item item) throws JsonProcessingException {
        return metadata(productId, windowId, item.productName(), item.itemId());
    }

    /** 将固定宽度版本前缀与响应 JSON 放在同一 Hash 值中，供 Lua 原子比较及读取。 */
    private String metadata(Long productId, Long windowId, String productName, Long itemId) throws JsonProcessingException {
        SalesRankingVO.Item item = new SalesRankingVO.Item(0, productId, windowId, productName, 0);
        return String.format(Locale.ROOT, "%020d:", itemId) + json.writeValueAsString(item);
    }

    /** 生成 fencing token，旧锁持有者即使租约过期也不能覆盖较新的 Redis 状态。 */
    private String nextGeneration() {
        Long value = redis.increment(GENERATION);
        if (value == null) throw unavailable();
        return value.toString();
    }

    /** 读取投影版本；缓存故障或元数据缺失统一作为服务不可用，不猜测版本。 */
    private long currentVersion() {
        Long version = projection.selectProjectionVersion();
        if (version == null || version < 0) throw unavailable();
        return version;
    }

    /** ready Key 的缺失或损坏代表缓存尚未建立，应走完整重建。 */
    private long parseVersion(String value) {
        if (value == null) return -1;
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { return -1; }
    }

    /** 按类路径加载 Lua；执行统一经 RedisOperationsService，业务代码不直接持有 RedisTemplate。 */
    private static <T> DefaultRedisScript<T> script(String resource, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("statistics/" + resource));
        script.setResultType(resultType);
        return script;
    }

    /** List.class 是 Redis Lua 多值响应的运行期类型，单独建脚本以避免原始泛型误推断。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DefaultRedisScript<List> listScript(String resource) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("statistics/" + resource));
        script.setResultType(List.class);
        return script;
    }

    private BusinessException unavailable() {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "销量榜暂时无法获取，请稍后下拉刷新");
    }

    /** 单个菜品/窗口的订单增量聚合。 */
    private static final class Group {
        private final Long productId;
        private long quantity;
        private OrderPaidSalesSnapshotVO.Item latest;
        private Group(Long productId) { this.productId = productId; }
    }
}
