package com.hbue.ordering.order.vo;

/**
 * 取餐二维码视图，仅通过当前订单所属顾客的专用接口返回。
 *
 * <p>只返回 PNG 的 Base64 正文，不返回取餐码本身：数字码仍由
 * {@code /orders/{orderId}/pickup-code} 单独下发，二维码接口不扩大敏感信息的暴露面。
 * 匿名与脱敏对本场景不适用，但响应同样禁止被日志完整打印。</p>
 *
 * @param imageBase64 不含 {@code data:image/png;base64,} 前缀的 PNG Base64
 */
public record OrderPickupQrCodeVO(String imageBase64) {
    @Override
    public String toString() {
        // 二维码等价于取餐凭据，禁止通过日志或链路追踪输出 Base64 正文。
        return "OrderPickupQrCodeVO[redacted]";
    }
}
