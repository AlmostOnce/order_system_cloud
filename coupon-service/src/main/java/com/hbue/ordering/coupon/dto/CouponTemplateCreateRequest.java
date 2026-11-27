package com.hbue.ordering.coupon.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 总经理创建平台券模板的请求；出资方和发行方由服务端固定为 PLATFORM。
 */
@Data
public class CouponTemplateCreateRequest {
    /**
     * 展示标题，避免用户端列表出现不可辨识的券。
     */
    @NotBlank(message = "优惠券标题不能为空")
    @Size(max = 120, message = "优惠券标题不能超过 120 个字符")
    private String title;
    /**
     * 订单适用商品金额需达到此门槛。
     */
    @NotNull(message = "满减门槛不能为空")
    @DecimalMin(value = "0.01", message = "满减门槛必须大于 0")
    private BigDecimal minSpend;
    /**
     * 固定减免额必须小于门槛，确保首期订单仍有正数应付金额。
     */
    @NotNull(message = "优惠金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠金额必须大于 0")
    private BigDecimal discountAmount;
    /**
     * 平台活动的总领取上限，数据库原子条件更新保证并发不超发。
     */
    @NotNull(message = "发行数量不能为空")
    @Positive(message = "发行数量必须大于 0")
    private Long totalQuantity;
    /**
     * 空列表表示全校适用；有值时为限定窗口 ID 列表。
     */
    @Size(max = 100, message = "单个优惠券最多限定 100 个窗口")
    private List<@NotNull @Positive Long> windowIds;
    /**
     * 开始领取时间，按项目本地时间 Asia/Shanghai 解释。
     */
    @NotNull(message = "开始领取时间不能为空")
    private LocalDateTime claimStartAt;
    /**
     * 结束领取时间。
     */
    @NotNull(message = "结束领取时间不能为空")
    private LocalDateTime claimEndAt;
    /**
     * 券可用起始时间。
     */
    @NotNull(message = "可用开始时间不能为空")
    private LocalDateTime validFrom;
    /**
     * 券可用截止时间。
     */
    @NotNull(message = "可用截止时间不能为空")
    private LocalDateTime validUntil;
}
