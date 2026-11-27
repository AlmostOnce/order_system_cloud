package com.hbue.ordering.cart.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车规格键的规范化规则。该键同时参与 Redis Hash Field、订单清理核对和客户端变体身份，
 * 因此只能接受商品服务生成的升序正整数编号，不能按规格名称拼接。
 */
public final class CartSelectionKey {
    /**
     * 无规格菜品固定使用的键，保持原 Redis Hash Field 不变。
     */
    public static final String BASE = "base";
    /**
     * 每道菜最多10个单选规格组，因此一个键最多有10个选项编号。
     */
    private static final int MAX_OPTION_COUNT = 10;

    private CartSelectionKey() {
    }

    /**
     * 将旧版 Redis JSON 或旧版订单响应缺失的规格键解释为无规格键。
     *
     * @param value Redis/接口中的规格键
     * @return 非空且符合规范的规格键
     * @throws IllegalArgumentException 非法键不是兼容缺省值时抛出
     */
    public static String normalize(String value) {
        String key = value == null ? BASE : value;
        if (!isValid(key)) {
            throw new IllegalArgumentException("购物车规格键格式不正确");
        }
        return key;
    }

    /**
     * 校验键内选项 ID 为正数、严格升序且不超过规格组上限，拒绝重复或非规范键。
     *
     * @param key 待检查的规格键
     * @return 键可安全用于 Redis Field 和商品报价时为 true
     */
    public static boolean isValid(String key) {
        if (BASE.equals(key)) {
            return true;
        }
        if (key == null || key.isBlank() || key.length() > 210) {
            return false;
        }
        String[] parts = key.split("-", -1);
        if (parts.length == 0 || parts.length > MAX_OPTION_COUNT) {
            return false;
        }
        long previous = 0;
        for (String part : parts) {
            if (part.isEmpty() || !part.matches("[1-9][0-9]*")) {
                return false;
            }
            try {
                long optionId = Long.parseLong(part);
                if (optionId <= previous) {
                    return false;
                }
                previous = optionId;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从规范键还原商品服务核价所需的选项 ID；保持字符串键，避免 Lua/cjson 改写 BIGINT 精度。
     *
     * @param key 已校验或兼容归一后的规格键
     * @return 按升序排列的选项 ID；base 返回空列表
     */
    public static List<Long> optionIds(String key) {
        String normalized = normalize(key);
        if (BASE.equals(normalized)) {
            return List.of();
        }
        String[] parts = normalized.split("-");
        List<Long> ids = new ArrayList<>(parts.length);
        for (String part : parts) {
            ids.add(Long.parseLong(part));
        }
        return List.copyOf(ids);
    }

    /**
     * 生成本用户 Hash 内单个菜品规格行的稳定身份。
     *
     * @param productId    菜品编号
     * @param selectionKey 规格键
     * @return 菜品与规格组合身份
     */
    public static String identity(Long productId, String selectionKey) {
        return productId + ":" + normalize(selectionKey);
    }
}
