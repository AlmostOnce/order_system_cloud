package com.hbue.ordering.order.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 取餐码二维码渲染工具。
 *
 * <p>二维码内容就是订单当前的六位取餐码，属于“静态二维码”：同一个取餐码每次渲染结果一致，
 * 不引入令牌、过期时间或额外存储。这样窗口扫码枪扫出的文本与手工输入完全一致，现有核销接口
 * 无需改动，也不会因为二维码刷新导致顾客正在出示的图片突然失效。</p>
 *
 * <p>渲染为白底黑块的 PNG 并转 Base64 返回，由学生端直接放进 image 组件。相比客户端生成，
 * 服务端渲染可以避开 uni-app x 各端 Canvas/第三方二维码库的兼容差异；图片不需要落盘，
 * 也不经过文件服务，避免为一次性图片产生存储与清理成本。</p>
 *
 * <p>该工具不处理权限与订单状态：调用方必须先通过取餐码查询的归属、状态与退款校验，
 * 否则可能把不可展示的取餐码渲染出来。</p>
 */
public final class PickupQrCodeRenderer {

    /**
     * 输出图片边长（像素）。学生端按 160 至 180 逻辑像素展示，两倍尺寸保证高密度屏幕上
     * 模块边缘清晰，同时控制 Base64 体积，避免订单详情接口响应过大。
     */
    private static final int IMAGE_SIZE = 320;

    /**
     * 二维码静区宽度（模块数）。保留 1 个模块的留白即可被常见扫码设备识别，
     * 过大只会让有效图形变小。
     */
    private static final int QUIET_ZONE = 1;

    private PickupQrCodeRenderer() {
        // 纯静态工具类，禁止实例化。
    }

    /**
     * 把取餐码渲染为 PNG 的 Base64 字符串。
     *
     * <p>使用 UTF-8 字符集与 M 级纠错：取餐码只有数字，M 级在识读率与图形密度之间更均衡。
     * 渲染失败属于服务端环境问题，抛出 {@link IllegalStateException} 交由统一异常处理返回 500，
     * 不返回半成品图片误导顾客。</p>
     *
     * @param pickupCode 已通过权限与状态校验的六位取餐码
     * @return 不含 {@code data:image/png;base64,} 前缀的 PNG Base64
     */
    public static String base64Png(String pickupCode) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(pickupCode, BarcodeFormat.QR_CODE,
                    IMAGE_SIZE, IMAGE_SIZE,
                    Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, QUIET_ZONE));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            // 固定黑码白底，避免跟随系统深色模式导致部分扫码枪反色识别失败。
            MatrixToImageWriter.writeToStream(matrix, "PNG", output,
                    new MatrixToImageConfig(0xFF000000, 0xFFFFFFFF));
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("取餐二维码生成失败", exception);
        }
    }
}
