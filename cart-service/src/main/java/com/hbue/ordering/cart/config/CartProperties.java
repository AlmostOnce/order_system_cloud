package com.hbue.ordering.cart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 购物车容量、单项数量与存储生命周期配置。
 *
 * <p>限制购物车最多 100 类菜品，使单窗口批量核价不超过现有商品契约的上限。
 * Key 前缀用于隔离购物车与其它 Redis 业务；计时单位统一转为毫秒传给 Lua。</p>
 *
 * @param maxItems 每个用户允许保存的最大菜品种类数，范围 1~100
 * @param maxQuantity 每项菜品的最大购买数量
 * @param timeToLive 最后一次修改后的保留时长，读取不会续期
 * @param keyPrefix 用户购物车 Hash 的命名空间前缀
 * @author order-system
 * @date 2026-10-17
 */
@ConfigurationProperties(prefix = "cart")
public record CartProperties(
        @DefaultValue("100") int maxItems,
        @DefaultValue("99") int maxQuantity,
        @DefaultValue("30d") Duration timeToLive,
        @DefaultValue("cart:user:") String keyPrefix
) {
    /**
     * 启动时拒绝非法配置，避免运行后出现永久 Key 或无法完成的核价请求。
     */
    public CartProperties {
        // 上限与商品核价请求的最大 100 个 ID 保持兼容。
        if (maxItems < 1 || maxItems > 100 || maxQuantity < 1) {
            throw new IllegalArgumentException("购物车种类上限须为 1~100，单项数量上限须大于零");
        }
        if (timeToLive == null || timeToLive.isNegative() || timeToLive.toMillis() < 1
                || keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("购物车 TTL 必须为正数，Redis Key 前缀不能为空");
        }
    }
}
