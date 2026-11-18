package com.hbue.ordering.cart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis Hash 持久化的一项购物车数据，不映射 MySQL 表。
 *
 * <p>名称与编码仅作为失效菜品的展示快照；不保存可用于结算的价格。每次修改生成
 * 新 revision，后续按提交快照清理时可识别用户在下单期间的并发修改。规格通过规范键区分，
 * 旧 Redis JSON 没有规格字段时统一解释为 base，因而无需清空或迁移用户购物车。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDO {
    /**
     * 菜品所属窗口 ID，Redis Lua JSON 中用字符串保存以避免 BIGINT 精度丢失。
     */
    private Long windowId;
    /**
     * 菜品 ID，与窗口 ID 共同组成 Hash Field。
     */
    private Long productId;
    /**
     * 用户期望购买的数量，不代表库存已预留。
     */
    private Integer quantity;
    /**
     * 用户是否勾选此项，失效菜品即使勾选也不计入可结算金额。
     */
    private Boolean checked;
    /**
     * 本次修改的唯一版本，删除重建后也不得复用旧版本。
     */
    private String revision;
    /**
     * 最近加入时由商品服务提供的名称，仅用于失效项展示。
     */
    private String productNameSnapshot;
    /**
     * 最近加入时由商品服务提供的菜品编码。
     */
    private String productCodeSnapshot;
    /**
     * 规格组合的规范身份；base 表示无规格，其他值为升序选项编号。
     */
    private String selectionKey;
    /**
     * 最近加入时由商品服务生成的规格文字快照，仅用于当前规格失效时显示。
     */
    private String specificationTextSnapshot;

    /**
     * 保持旧代码与 Redis 相关静态检查所用七参构造兼容；历史行视为无规格菜品。
     *
     * @param windowId            菜品所属窗口
     * @param productId           菜品编号
     * @param quantity            购买数量
     * @param checked             是否勾选
     * @param revision            并发修改版本
     * @param productNameSnapshot 菜品名称快照
     * @param productCodeSnapshot 菜品编码快照
     */
    public CartItemDO(Long windowId, Long productId, Integer quantity, Boolean checked, String revision,
                      String productNameSnapshot, String productCodeSnapshot) {
        this(windowId, productId, quantity, checked, revision, productNameSnapshot, productCodeSnapshot,
                CartSelectionKey.BASE, "");
    }
}
