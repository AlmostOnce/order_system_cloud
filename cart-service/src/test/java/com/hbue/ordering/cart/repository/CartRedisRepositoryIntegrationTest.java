package com.hbue.ordering.cart.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.cart.config.CartProperties;
import com.hbue.ordering.cart.dto.request.CartSubmittedItemRequest;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Redis 上的购物车 Lua 集成测试。
 *
 * <p>显式设置 CART_REDIS_IT=true 才运行；连接参数从 CART_TEST_REDIS_* 环境变量读取。
 * 使用独立随机前缀，仅删除本测试创建的两个用户 Key，绝不清库或扫描删除业务数据。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@EnabledIfEnvironmentVariable(named = "CART_REDIS_IT", matches = "true")
class CartRedisRepositoryIntegrationTest {
    /** 测试命名空间，即使并行启动另一轮集成测试也不会冲突。 */
    private static final String PREFIX = "cart:integration:" + UUID.randomUUID() + ":user:";
    /** 本测试独有的 Redis 连接工厂。 */
    private static LettuceConnectionFactory connectionFactory;
    /** 真实 Redis 模板。 */
    private static StringRedisTemplate redis;
    /** 默认容量约束下的被测组件。 */
    private CartRedisRepository repository;

    /** 下单快照只删除版本未变的条目，重复清理及其它用户、窗口均安全。 */
    @Test
    void shouldRemoveOnlyUnchangedSubmittedItems() {
        repository.add(1L, 1L, 11L, 2, "饭", "RICE");
        repository.add(1L, 1L, 12L, 1, "汤", "SOUP");
        repository.add(1L, 10L, 11L, 2, "饭", "RICE");
        repository.add(2L, 1L, 11L, 2, "饭", "RICE");
        String revision = repository.findAll(1L).getFirst().getRevision();
        List<CartSubmittedItemRequest> snapshot = List.of(new CartSubmittedItemRequest(11L, 2, revision));
        assertEquals(1L, repository.removeSubmitted(1L, 1L, snapshot));
        assertEquals(0L, repository.removeSubmitted(1L, 1L, snapshot));
        assertEquals(2, repository.findAll(1L).size());
        assertEquals(1, repository.findAll(2L).size());
    }

    /** 数量改后再改回、取消勾选、删除后重新添加，都不能被旧提交快照删除。 */
    @Test
    void shouldKeepModifiedAndRecreatedItems() {
        repository.add(1L, 1L, 11L, 2, "饭", "RICE");
        String revision = repository.findAll(1L).getFirst().getRevision();
        List<CartSubmittedItemRequest> snapshot = List.of(new CartSubmittedItemRequest(11L, 2, revision));
        repository.updateQuantity(1L, 1L, 11L, 3);
        repository.updateQuantity(1L, 1L, 11L, 2);
        assertEquals(0L, repository.removeSubmitted(1L, 1L, snapshot));
        repository.remove(1L, 1L, 11L);
        repository.add(1L, 1L, 11L, 2, "饭", "RICE");
        assertEquals(0L, repository.removeSubmitted(1L, 1L, snapshot));
        assertEquals(2, repository.findAll(1L).getFirst().getQuantity());
        repository.updateChecked(1L, 1L, 11L, false);
        assertEquals(0L, repository.removeSubmitted(1L, 1L, snapshot));
    }

    /** 任一候选值损坏时，整个脚本在删除前失败，不能只清掉前几项。 */
    @Test
    void shouldValidateAllCleanupCandidatesBeforeDeleting() {
        repository.add(1L, 1L, 11L, 2, "饭", "RICE");
        String revision = repository.findAll(1L).getFirst().getRevision();
        redis.opsForHash().put(PREFIX + "1", "1:12", "{broken");
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> repository.removeSubmitted(1L, 1L, List.of(
                        new CartSubmittedItemRequest(11L, 2, revision),
                        new CartSubmittedItemRequest(12L, 1, "old")))).getErrorCode());
        assertTrue(redis.opsForHash().hasKey(PREFIX + "1", "1:11"));
    }

    /** 清理不续期剩余商品；没有购物车时不创建空 Key。 */
    @Test
    void shouldPreserveTtlAndNotCreateMissingCartDuringCleanup() {
        repository.add(1L, 1L, 11L, 2, "饭", "RICE");
        repository.add(1L, 1L, 12L, 1, "汤", "SOUP");
        String revision = repository.findAll(1L).getFirst().getRevision();
        redis.expire(PREFIX + "1", Duration.ofSeconds(5));
        List<CartSubmittedItemRequest> snapshot = List.of(new CartSubmittedItemRequest(11L, 2, revision));
        assertEquals(1L, repository.removeSubmitted(1L, 1L, snapshot));
        assertTrue(redis.getExpire(PREFIX + "1", TimeUnit.MILLISECONDS) <= 5000);
        repository.remove(1L, 1L, 12L);
        assertEquals(0L, repository.removeSubmitted(1L, 1L, snapshot));
        assertFalse(redis.hasKey(PREFIX + "1"));
    }

    /** 清理的大主键保持字符串精度，最后一项删除后不保留空 Hash。 */
    @Test
    void shouldCleanLargeIdsWithoutPrecisionLoss() {
        long id = 9007199254740993L;
        repository.add(1L, id, id, 2, "大主键菜品", "BIG");
        String revision = repository.findAll(1L).getFirst().getRevision();
        assertEquals(1L, repository.removeSubmitted(1L, id,
                List.of(new CartSubmittedItemRequest(id, 2, revision))));
        assertFalse(redis.hasKey(PREFIX + "1"));
    }

    /** 精确匹配版本也不能删除数量不符或已取消勾选的条目。 */
    @Test
    void shouldKeepDifferentQuantityAndUncheckedItems() {
        repository.add(1L, 1L, 11L, 2, "饭", "RICE");
        String revision = repository.findAll(1L).getFirst().getRevision();
        assertEquals(0L, repository.removeSubmitted(1L, 1L,
                List.of(new CartSubmittedItemRequest(11L, 1, revision))));
        repository.updateChecked(1L, 1L, 11L, false);
        String uncheckedRevision = repository.findAll(1L).getFirst().getRevision();
        assertEquals(0L, repository.removeSubmitted(1L, 1L,
                List.of(new CartSubmittedItemRequest(11L, 2, uncheckedRevision))));
        assertEquals(1, repository.findAll(1L).size());
    }

    /** 清理与再次加购并发时，新加的一份必须保留，不能落入 Java 先查后删的竞态。 */
    @Test
    void shouldKeepNewIntentWhenCleanupRacesWithAdd() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int attempt = 0; attempt < 30; attempt++) {
                repository.remove(1L, 1L, 11L);
                repository.add(1L, 1L, 11L, 2, "饭", "RICE");
                String revision = repository.findAll(1L).getFirst().getRevision();
                List<CartSubmittedItemRequest> snapshot = List.of(new CartSubmittedItemRequest(11L, 2, revision));
                CountDownLatch start = new CountDownLatch(1);
                Future<Long> cleanup = executor.submit(() -> {
                    start.await();
                    return repository.removeSubmitted(1L, 1L, snapshot);
                });
                Future<?> add = executor.submit(() -> {
                    start.await();
                    repository.add(1L, 1L, 11L, 1, "饭", "RICE");
                    return null;
                });
                start.countDown();
                long removed = cleanup.get(5, TimeUnit.SECONDS);
                add.get(5, TimeUnit.SECONDS);
                // 清理先执行则剩新加的 1 份；加购先执行则版本已变，原 2 份与新增 1 份均保留。
                assertEquals(removed == 1 ? 1 : 3, repository.findAll(1L).getFirst().getQuantity());
            }
        }
    }

    /** 创建实际 Redis 连接，不读取或输出业务密码。 */
    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("CART_TEST_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("CART_TEST_REDIS_PORT", "9096")));
        String password = System.getenv("CART_TEST_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    /** 每条用例开始前仅清理测试前缀下的两个固定 Key。 */
    @BeforeEach
    void setUp() {
        cleanTestKeys();
        repository = repositoryWithLimit(100);
    }

    /** 每条用例结束时清理自身创建的数据，失败用例也执行清理。 */
    @AfterEach
    void cleanTestKeys() {
        redis.delete(List.of(PREFIX + "1", PREFIX + "2"));
    }

    /** 关闭测试自身的连接与线程资源。 */
    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    /** 同菜品累加数量，用户 Key 隔离，修改数量不覆盖勾选。 */
    @Test
    void shouldMergeAndIsolateUsers() {
        repository.add(1L, 1L, 11L, 2, "饭", "RICE");
        repository.add(1L, 1L, 11L, 3, "饭", "RICE");
        repository.add(2L, 1L, 11L, 1, "饭", "RICE");
        repository.updateChecked(1L, 1L, 11L, false);
        repository.updateQuantity(1L, 1L, 11L, 4);
        assertEquals(4, repository.findAll(1L).getFirst().getQuantity());
        assertFalse(repository.findAll(1L).getFirst().getChecked());
        assertEquals(1, repository.findAll(2L).getFirst().getQuantity());
    }

    /** Redis Lua JSON 不得把超过 2^53 的 BIGINT 标识截断。 */
    @Test
    void shouldPreserveLargeIdsAcrossUpdates() {
        long id = 9007199254740993L;
        repository.add(1L, id, id, 1, "大主键菜品", "BIG");
        repository.updateQuantity(1L, id, id, 2);
        assertEquals(id, repository.findAll(1L).getFirst().getWindowId());
        assertEquals(id, repository.findAll(1L).getFirst().getProductId());
    }

    /** 不存在的条目不能通过数量修改意外新增。 */
    @Test
    void shouldNotCreateMissingItemsOnUpdate() {
        assertEquals(CommonErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> repository.updateQuantity(1L, 1L, 11L, 2)).getErrorCode());
        assertEquals(CommonErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> repository.updateChecked(1L, 1L, 11L, false)).getErrorCode());
        assertFalse(redis.hasKey(PREFIX + "1"));
    }

    /** 清空窗口 1 不会清空窗口 10，重复删除保持幂等。 */
    @Test
    void shouldClearOnlyRequestedWindowAndDeleteEmptyHash() {
        repository.add(1L, 1L, 11L, 1, "饭", "RICE");
        repository.add(1L, 10L, 21L, 1, "面", "NOODLE");
        repository.clearWindow(1L, 1L);
        assertEquals(10L, repository.findAll(1L).getFirst().getWindowId());
        repository.remove(1L, 10L, 21L);
        repository.remove(1L, 10L, 21L);
        assertFalse(redis.hasKey(PREFIX + "1"));
    }

    /** 首次写入带 TTL，查询不会把即将过期的购物车续期到 30 天。 */
    @Test
    void shouldExpireAfterWritesButNotAfterReads() {
        repository.add(1L, 1L, 11L, 1, "饭", "RICE");
        assertTrue(redis.getExpire(PREFIX + "1", TimeUnit.MILLISECONDS) > 0);
        redis.expire(PREFIX + "1", Duration.ofSeconds(5));
        repository.findAll(1L);
        long ttl = redis.getExpire(PREFIX + "1", TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 5000);
        repository.updateQuantity(1L, 1L, 11L, 2);
        assertTrue(redis.getExpire(PREFIX + "1", TimeUnit.SECONDS) > 60);
    }

    /** 每次修改及删除后重建都使用新版本，为后续快照清理提供可靠比较依据。 */
    @Test
    void shouldNeverReuseRevision() {
        repository.add(1L, 1L, 11L, 1, "饭", "RICE");
        String first = repository.findAll(1L).getFirst().getRevision();
        repository.updateChecked(1L, 1L, 11L, false);
        String second = repository.findAll(1L).getFirst().getRevision();
        repository.remove(1L, 1L, 11L);
        repository.add(1L, 1L, 11L, 1, "饭", "RICE");
        String third = repository.findAll(1L).getFirst().getRevision();
        assertNotEquals(first, second);
        assertNotEquals(first, third);
        assertNotEquals(second, third);
    }

    /** 并发累加不能超过最终数量上限，也不能丢失成功的增加操作。 */
    @Test
    void shouldEnforceQuantityLimitUnderConcurrentAdds() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            tasks.add(() -> {
                try {
                    repository.add(1L, 1L, 11L, 1, "饭", "RICE");
                    return 1;
                } catch (BusinessException exception) {
                    assertEquals(CommonErrorCode.BAD_REQUEST, exception.getErrorCode());
                    return 0;
                }
            });
        }
        assertEquals(99, runConcurrent(tasks));
        assertEquals(99, repository.findAll(1L).getFirst().getQuantity());
    }

    /** 多实例同时添加不同菜品时，容量判断与插入必须是同一个原子动作。 */
    @Test
    void shouldEnforceCapacityUnderConcurrentNewItems() throws Exception {
        CartRedisRepository limited = repositoryWithLimit(2);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (long index = 1; index <= 20; index++) {
            long productId = index;
            tasks.add(() -> {
                try {
                    limited.add(1L, 1L, productId, 1, "饭", "RICE");
                    return 1;
                } catch (BusinessException exception) {
                    assertEquals(CommonErrorCode.BAD_REQUEST, exception.getErrorCode());
                    return 0;
                }
            });
        }
        assertEquals(2, runConcurrent(tasks));
        assertEquals(2, limited.findAll(1L).size());
    }

    /** 异常 JSON 返回 503 且修改不覆盖原始数据，删除仍可用于清理坏条目。 */
    @Test
    void shouldRejectCorruptDataWithoutOverwritingIt() {
        redis.opsForHash().put(PREFIX + "1", "1:11", "{broken");
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> repository.findAll(1L)).getErrorCode());
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> repository.updateQuantity(1L, 1L, 11L, 2)).getErrorCode());
        assertEquals("{broken", redis.opsForHash().get(PREFIX + "1", "1:11"));
        repository.remove(1L, 1L, 11L);
        assertFalse(redis.hasKey(PREFIX + "1"));
    }

    /**
     * 创建指定容量的访问组件，所有实例连接同一测试命名空间。
     *
     * @param maxItems 菜品容量
     * @return 真实 Redis 持久化组件
     */
    private CartRedisRepository repositoryWithLimit(int maxItems) {
        return new CartRedisRepository(redis, new ObjectMapper(),
                new CartProperties(maxItems, 99, Duration.ofDays(30), PREFIX));
    }

    /**
     * 使用独立线程并发执行操作并汇总成功数量。
     *
     * @param tasks 每个任务成功返回 1，预期的限制拒绝返回 0
     * @return 成功操作数量
     * @throws Exception 任一任务出现非预期错误时向测试传播
     */
    private int runConcurrent(List<Callable<Integer>> tasks) throws Exception {
        try (var executor = Executors.newFixedThreadPool(12)) {
            int succeeded = 0;
            for (Future<Integer> result : executor.invokeAll(tasks)) {
                succeeded += result.get();
            }
            return succeeded;
        }
    }
}
