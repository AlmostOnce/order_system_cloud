package com.hbue.ordering.cart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis Hash 持久化的一项购物车数据，不映射 MySQL 表。
 *
 * <p>名称与编码仅作为失效菜品的展示快照；不保存可用于结算的价格。每次修改生成
 * 新 revision，后续按提交快照清理时可识别用户在下单期间的并发修改。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDO {
    /** 菜品所属窗口 ID，Redis Lua JSON 中用字符串保存以避免 BIGINT 精度丢失。 */
    private Long windowId;
    /** 菜品 ID，与窗口 ID 共同组成 Hash Field。 */
    private Long productId;
    /** 用户期望购买的数量，不代表库存已预留。 */
    private Integer quantity;
    /** 用户是否勾选此项，失效菜品即使勾选也不计入可结算金额。 */
    private Boolean checked;
    /** 本次修改的唯一版本，删除重建后也不得复用旧版本。 */
    private String revision;
    /** 最近加入时由商品服务提供的名称，仅用于失效项展示。 */
    private String productNameSnapshot;
    /** 最近加入时由商品服务提供的菜品编码。 */
    private String productCodeSnapshot;
}
