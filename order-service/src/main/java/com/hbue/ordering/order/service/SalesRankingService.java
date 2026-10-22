package com.hbue.ordering.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.order.mapper.SalesRankingMapper;
import com.hbue.ordering.order.model.PaidSalesLine;
import com.hbue.ordering.order.vo.SalesRankingVO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/** MQ 增量更新，历史重建和定时对账使用同一个受 fencing 保护的临界区。 */
@Service
public class SalesRankingService {
    private static final Logger log = LoggerFactory.getLogger(SalesRankingService.class);
    static final String DATA = "ranking:{sales:v2}:data";
    static final String META = "ranking:{sales:v2}:meta";
    static final String SEEN = "ranking:{sales:v2}:seen";
    static final String READY = "ranking:{sales:v2}:ready";
    static final String GENERATION = "ranking:{sales:v2}:generation";
    private static final String LOCK = "ranking:{sales:v2}:lock";
    private static final List<String> KEYS = List.of(DATA, META, SEEN, READY, GENERATION);
    private static final long MAX_EXACT = 9_007_199_254_740_991L;
    private static final DefaultRedisScript<Long> REBUILD = script("rebuild-sales-v2.lua");
    private static final DefaultRedisScript<Long> APPLY = script("apply-paid-sales-v2.lua");
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> READ = new DefaultRedisScript<>();
    static {
        READ.setLocation(new ClassPathResource("rankings/read-sales-v2.lua"));
        READ.setResultType(List.class);
    }
    private final StringRedisTemplate redis;
    private final SalesRankingMapper mapper;
    private final DistributedLockService locks;
    private final ObjectMapper json;

    public SalesRankingService(StringRedisTemplate redis, SalesRankingMapper mapper,
                               DistributedLockService locks, ObjectMapper json) {
        this.redis = redis; this.mapper = mapper; this.locks = locks; this.json = json;
    }

    public SalesRankingVO list(int limit) {
        if (limit < 1 || limit > 100) throw new BusinessException(CommonErrorCode.BAD_REQUEST, "榜单数量应在 1 到 100 之间");
        try {
            SalesRankingVO cached = read(limit);
            if (cached != null) return cached;
            try (var lock = locks.tryAcquire(LOCK, 2, 30)) {
                cached = read(limit);
                if (cached != null) return cached;
                if (lock == null) throw unavailable();
                rebuildUnderLock();
                cached = read(limit);
                if (cached == null) throw unavailable();
                return cached;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("读取菜品销量榜失败", exception);
            throw unavailable();
        }
    }

    public void acceptPaidOrder(Long orderId) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("订单标识不正确");
        try (var lock = locks.tryAcquire(LOCK, 2, 30)) {
            if (lock == null) throw unavailable();
            // 数据丢失时，先一次性重建销量与全部已计入订单集合，再处理重投消息。
            if (!Boolean.TRUE.equals(redis.hasKey(READY))) rebuildUnderLock();
            var lines = mapper.paidOrderLines(orderId);
            if (lines.isEmpty()) throw new IllegalStateException("销量事件未找到已支付订单");
            var totals = group(lines);
            List<String> args = new ArrayList<>();
            args.add(nextGeneration()); args.add(orderId.toString()); args.add(Instant.now().toString());
            for (var entry : totals.entrySet()) {
                args.add(entry.getKey()); args.add(Long.toString(entry.getValue().quantity));
                args.add(metadata(entry.getValue()));
            }
            Long result = redis.execute(APPLY, KEYS, args.toArray());
            if (result == null || result < 0) throw unavailable();
        } catch (RuntimeException exception) {
            // 让 MQ 重试，不能吞掉后确认消费成功。
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("销量快照编码失败", exception);
        }
    }

    public void reconcile() {
        try (var lock = locks.tryAcquire(LOCK, 0, 30)) {
            if (lock != null) rebuildUnderLock();
        } catch (Exception exception) {
            log.error("销量榜对账重建失败，保留已有投影并等待下次重试", exception);
        }
    }

    private void rebuildUnderLock() throws JsonProcessingException {
        // 先递增 generation，查询期间锁过期且有新写入时，旧重建将被 Lua 拒绝。
        String generation = nextGeneration();
        String updatedAt = Instant.now().toString();
        var lines = mapper.allPaidLines();
        var totals = group(lines);
        Set<Long> orderIds = new TreeSet<>();
        for (var line : lines) orderIds.add(line.getOrderId());
        List<String> args = new ArrayList<>();
        args.add(generation); args.add(updatedAt); args.add(Integer.toString(orderIds.size()));
        for (Long orderId : orderIds) args.add(orderId.toString());
        for (var entry : totals.entrySet()) {
            args.add(entry.getKey()); args.add(Long.toString(entry.getValue().quantity));
            args.add(metadata(entry.getValue()));
        }
        Long result = redis.execute(REBUILD, KEYS, args.toArray());
        if (!Long.valueOf(1).equals(result)) throw unavailable();
    }

    private LinkedHashMap<String, Total> group(List<PaidSalesLine> lines) {
        LinkedHashMap<String, Total> totals = new LinkedHashMap<>();
        for (var line : lines) {
            if (line.getOrderId() == null || line.getOrderId() <= 0 || line.getItemId() == null || line.getItemId() <= 0
                    || line.getProductId() == null || line.getProductId() <= 0 || line.getWindowId() == null || line.getWindowId() <= 0
                    || line.getQuantity() == null || line.getQuantity() <= 0) throw unavailable();
            String member = String.format(Locale.ROOT, "%020d:%020d", line.getProductId(), line.getWindowId());
            Total total = totals.computeIfAbsent(member, ignored -> new Total());
            total.quantity = Math.addExact(total.quantity, line.getQuantity());
            if (total.quantity > MAX_EXACT) throw unavailable();
            if (total.latest == null || line.getItemId() > total.latest.getItemId()) total.latest = line;
        }
        return totals;
    }

    private String metadata(Total total) throws JsonProcessingException {
        var line = total.latest;
        var item = new SalesRankingVO.Item(0, line.getProductId(), line.getWindowId(), line.getProductName(), 0);
        return String.format(Locale.ROOT, "%020d:", line.getItemId()) + json.writeValueAsString(item);
    }

    private String nextGeneration() {
        Long next = redis.opsForValue().increment(GENERATION);
        if (next == null) throw unavailable();
        return next.toString();
    }

    private SalesRankingVO read(int limit) throws JsonProcessingException {
        List<?> values = redis.execute(READ, KEYS, String.valueOf(limit));
        if (values == null || values.isEmpty()) return null;
        if ((values.size() - 1) % 2 != 0) throw unavailable();
        List<SalesRankingVO.Item> items = new ArrayList<>();
        for (int i = 1; i + 1 < values.size(); i += 2) {
            var stored = json.readValue(((String) values.get(i)).substring(21), SalesRankingVO.Item.class);
            long sales = new java.math.BigDecimal((String) values.get(i + 1)).longValueExact();
            items.add(new SalesRankingVO.Item(items.size() + 1, stored.productId(), stored.windowId(), stored.productName(), sales));
        }
        return new SalesRankingVO((String) values.getFirst(), items);
    }

    private static DefaultRedisScript<Long> script(String resource) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("rankings/" + resource)); script.setResultType(Long.class);
        return script;
    }
    private static class Total { long quantity; PaidSalesLine latest; }
    private BusinessException unavailable() {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "销量榜暂时无法获取，请稍后下拉刷新");
    }
}
