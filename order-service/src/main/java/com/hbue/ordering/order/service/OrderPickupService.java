package com.hbue.ordering.order.service;

import com.hbue.ordering.order.vo.OrderStatusVO;

/**
 * 取餐码的生成与核销。
 *
 * <p>顾客侧只能读取自己订单的取餐码，服务端以 JWT 中的用户编号校验归属，越权与不存在统一返回 404；
 * 商家侧凭六位数字取餐码核销，先按操作者的窗口数据范围过滤，再以带原状态条件的 UPDATE 完成
 * "待取货 → 已完成"，并发重复核销只会有一次成功。</p>
 *
 * <p>核销失败会写审计日志但不在日志中记录取餐码明文，避免凭日志即可冒领。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
public interface OrderPickupService {

    /**
     * 读取当前用户名下订单的取餐码，不存在时按需生成。
     *
     * @param userId  当前登录用户编号
     * @param orderId 订单主键
     * @return 六位数字取餐码
     */
    String getOwnPickupCode(Long userId, Long orderId);

    /**
     * 读取当前用户名下订单取餐码对应的二维码图片。
     *
     * <p>二维码内容就是 {@link #getOwnPickupCode(Long, Long)} 返回的六位取餐码，
     * 因此归属、状态与退款校验完全复用同一入口，不额外放宽二维码的可展示范围。</p>
     *
     * @param userId  当前登录用户编号
     * @param orderId 订单主键
     * @return PNG 的 Base64 字符串，不带 data URL 前缀
     */
    String getOwnPickupQrCode(Long userId, Long orderId);

    /**
     * 商家核销取餐码，把订单从待取货推进到已完成。
     *
     * @param operatorId 核销操作者编号
     * @param pickupCode 六位数字取餐码
     * @return 核销后的订单状态
     */
    OrderStatusVO verifyPickup(Long operatorId, String pickupCode);
}
