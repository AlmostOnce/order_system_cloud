package com.hbue.ordering.product.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.vo.WindowSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品菜单缓存读写组件。
 *
 * <p>统一封装启用窗口列表、窗口菜单和窗口菜单空值标记的读写与失效，
 * 缓存值以 JSON 字符串存储，复用现有 VO 完成序列化，不额外引入缓存专用 DTO。</p>
 *
 * <p>缓存是数据库之上的加速层而非数据来源：所有写操作都兜住
 * {@link RuntimeException} 并仅记录日志，缓存故障绝不能导致业务请求失败。</p>
 *
 * <p>过期时间在基础 TTL 上叠加随机抖动，使同一批写入的 Key 不会同时失效，
 * 避免缓存集中过期引发雪崩。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Component
public class ProductMenuCacheService {

    /** 日志对象。 */
    private static final Logger log =
            LoggerFactory.getLogger(ProductMenuCacheService.class);

    /** 缓存基础有效期，单位为秒。 */
    private static final long BASE_TTL_SECONDS = 300L;

    /** TTL 随机抖动上限，单位为秒。 */
    private static final long TTL_JITTER_SECONDS = 120L;

    /** 空值标记固定有效期，单位为秒。 */
    private static final long NULL_TTL_SECONDS = 60L;

    /** Redis 基础操作组件。 */
    private final RedisOperationsService redisOperationsService;

    /** JSON 序列化器，使用 Spring Boot 自动装配的实例。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建商品菜单缓存组件。
     *
     * @param redisOperationsService Redis 基础操作组件
     * @param objectMapper JSON 序列化器
     */
    public ProductMenuCacheService(
            RedisOperationsService redisOperationsService,
            ObjectMapper objectMapper
    ) {
        this.redisOperationsService = redisOperationsService;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询启用窗口列表缓存。
     *
     * @return 启用窗口列表；未命中或缓存异常时返回 null
     */
    public List<WindowSummaryVO> findActiveWindows() {
        String cached = redisOperationsService.getValue(
                ProductCacheKeys.ACTIVE_WINDOWS
        );
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    cached,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class,
                            WindowSummaryVO.class
                    )
            );
        } catch (Exception exception) {
            // 反序列化失败按未命中处理，由调用方回源数据库重建缓存。
            log.error("反序列化启用窗口缓存失败", exception);
            return null;
        }
    }

    /**
     * 保存启用窗口列表缓存。
     *
     * @param windows 启用窗口列表
     */
    public void saveActiveWindows(List<WindowSummaryVO> windows) {
        if (windows == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(windows);
            redisOperationsService.setValue(
                    ProductCacheKeys.ACTIVE_WINDOWS,
                    json,
                    randomTtl()
            );
        } catch (RuntimeException exception) {
            log.error("写入启用窗口缓存失败", exception);
        } catch (Exception exception) {
            log.error("序列化启用窗口列表失败", exception);
        }
    }

    /**
     * 失效启用窗口列表缓存。
     */
    public void evictActiveWindows() {
        try {
            redisOperationsService.delete(ProductCacheKeys.ACTIVE_WINDOWS);
        } catch (RuntimeException exception) {
            log.error("删除启用窗口缓存失败", exception);
        }
    }

    /**
     * 查询窗口菜单缓存。
     *
     * @param windowId 窗口 ID
     * @return 窗口菜单；未命中或缓存异常时返回 null
     */
    public WindowMenuVO findWindowMenu(Long windowId) {
        String cached = redisOperationsService.getValue(
                ProductCacheKeys.windowMenu(windowId)
        );
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, WindowMenuVO.class);
        } catch (Exception exception) {
            // 反序列化失败按未命中处理，由调用方回源数据库重建缓存。
            log.error("反序列化窗口菜单缓存失败，窗口 ID=" + windowId, exception);
            return null;
        }
    }

    /**
     * 判断窗口菜单是否已被标记为不存在。
     *
     * @param windowId 窗口 ID
     * @return 空值标记存在时返回 true
     */
    public boolean isWindowMenuMarkedAbsent(Long windowId) {
        try {
            Boolean exists = redisOperationsService.hasKey(
                    ProductCacheKeys.windowMenuNull(windowId)
            );
            return Boolean.TRUE.equals(exists);
        } catch (RuntimeException exception) {
            // 空值标记判定失败按未命中处理，由后续流程回源数据库。
            log.error("查询窗口菜单空值标记失败，窗口 ID=" + windowId, exception);
            return false;
        }
    }

    /**
     * 保存窗口菜单缓存。
     *
     * @param windowId 窗口 ID
     * @param menu 窗口菜单
     */
    public void saveWindowMenu(Long windowId, WindowMenuVO menu) {
        if (menu == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(menu);
            redisOperationsService.setValue(
                    ProductCacheKeys.windowMenu(windowId),
                    json,
                    randomTtl()
            );
        } catch (RuntimeException exception) {
            log.error("写入窗口菜单缓存失败，窗口 ID=" + windowId, exception);
        } catch (Exception exception) {
            log.error("序列化窗口菜单失败，窗口 ID=" + windowId, exception);
        }
    }

    /**
     * 写入窗口菜单空值标记。
     *
     * <p>空值标记使用固定 TTL，保证被删除或停用的窗口在标记过期后
     * 仍可重新通过数据库校验，避免数据恢复后长期被误判为不存在。</p>
     *
     * @param windowId 窗口 ID
     */
    public void saveWindowMenuAbsent(Long windowId) {
        try {
            redisOperationsService.setValue(
                    ProductCacheKeys.windowMenuNull(windowId),
                    "1",
                    Duration.ofSeconds(NULL_TTL_SECONDS)
            );
            redisOperationsService.delete(
                    ProductCacheKeys.windowMenu(windowId)
            );
        } catch (RuntimeException exception) {
            log.error("写入窗口菜单空值标记失败，窗口 ID=" + windowId, exception);
        }
    }

    /**
     * 失效窗口菜单缓存及其空值标记。
     *
     * @param windowId 窗口 ID
     */
    public void evictWindowMenu(Long windowId) {
        try {
            redisOperationsService.delete(
                    ProductCacheKeys.windowMenu(windowId)
            );
            redisOperationsService.delete(
                    ProductCacheKeys.windowMenuNull(windowId)
            );
        } catch (RuntimeException exception) {
            log.error("删除窗口菜单缓存失败，窗口 ID=" + windowId, exception);
        }
    }

    /**
     * 生成带随机抖动的缓存有效期。
     *
     * <p>基础 TTL 上叠加 0 至 {@code TTL_JITTER_SECONDS} 秒的随机偏移，
     * 使同一批次写入的 Key 过期时间错开，避免集中失效造成缓存雪崩。</p>
     *
     * @return 缓存有效期
     */
    private Duration randomTtl() {
        long extraSeconds = ThreadLocalRandom.current()
                .nextLong(TTL_JITTER_SECONDS + 1);
        return Duration.ofSeconds(BASE_TTL_SECONDS + extraSeconds);
    }
}
