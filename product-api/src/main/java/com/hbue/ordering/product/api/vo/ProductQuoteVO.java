package com.hbue.ordering.product.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务核价结果中的单个可售商品视图。
 *
 * <p>此类型用于服务间传递商品服务读取到的当前商品标识、名称和单价，
 * 供订单服务在创建订单时形成价格快照；它不是商品表实体，也不代表后续价格。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQuoteVO {

    /**
     * 菜品 ID。
     */
    private Long productId;

    /**
     * 窗口内菜品编码。
     */
    private String productCode;

    /**
     * 菜品名称。
     */
    private String productName;

    /**
     * 基础售价加所选非负加价后的权威单价，调用方不得覆盖。
     */
    private BigDecimal price;
    /**
     * 规范化选择键；无选项为base，否则将选项ID升序后以短横线连接。
     */
    private String selectionKey = "base";
    /**
     * 服务端核验后的升序选项编号，与selectionKey一致。
     */
    private List<Long> selectedOptionIds = List.of();
    /**
     * 按组显示顺序生成的规格文字，调用方可保存为订单快照；无选择时为空字符串。
     */
    private String specificationText = "";

    /**
     * 商品服务按规格组配置顺序生成的结构化选择 JSON；每项为 groupId/groupName/optionId/optionName。
     * 只包含真实选择，空选择固定为 []；名称来自商品库，订单端不得用客户端文字替换此快照。
     */
    private String specificationSelectionSnapshot = "[]";

    /**
     * 兼容原七字段核价构造；生产核价须另外写入真实结构化快照，不能由旧文字反推标签。
     */
    public ProductQuoteVO(Long productId, String productCode, String productName, BigDecimal price,
                          String selectionKey, List<Long> selectedOptionIds, String specificationText) {
        this(productId, productCode, productName, price, selectionKey, selectedOptionIds, specificationText, "[]");
    }

    /**
     * 兼容无规格调用方的原构造方式，新增字段保持base、空数组和空文字。
     */
    public ProductQuoteVO(Long productId, String productCode, String productName, BigDecimal price) {
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.price = price;
    }
}
