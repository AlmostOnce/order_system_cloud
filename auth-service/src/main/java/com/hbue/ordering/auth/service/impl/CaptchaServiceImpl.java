package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.service.CaptchaService;
import com.hbue.ordering.auth.vo.CaptchaVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 基于 JDK AWT 与 Redis 的图片验证码实现。
 *
 * <p>验证码答案只保存在 Redis 中，Key 为 {@code auth:captcha:} 加随机标识，
 * 带固定 TTL。校验时使用 GETDEL 原子读取并删除，因此每张验证码无论成功还是
 * 失败都只能使用一次，避免重放。</p>
 *
 * <p>图片直接由 JDK 自带的 BufferedImage 绘制，不引入第三方验证码库；
 * Spring Boot 默认以 headless 模式运行，无需图形界面。</p>
 *
 * <p>生成接口是匿名入口，按来源 IP 做分钟级计数限流，防止被用来刷 CPU。</p>
 *
 * @author order-system
 * @date 2026-10-21
 */
@Service
public class CaptchaServiceImpl implements CaptchaService {

    /**
     * 验证码答案在 Redis 中的 Key 前缀。
     */
    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";

    /**
     * 生成频次计数的 Key 前缀。
     */
    private static final String GENERATE_RATE_KEY_PREFIX =
            "auth:captcha:generate:";

    /**
     * 去掉容易混淆的 0/O/1/I 等字符，降低用户看错而误判的概率。
     */
    private static final String CHARACTER_POOL =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    /**
     * 验证码字符的颜色候选，均为深色以保证在浅色背景上可读。
     */
    private static final Color[] CHARACTER_COLORS = {
            new Color(0x21, 0x1C, 0x18),
            new Color(0x8C, 0x4A, 0x2F),
            new Color(0xB4, 0x53, 0x09)
    };

    /**
     * 密码学安全的随机数发生器，避免验证码可被预测。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Redis 基础操作组件。
     */
    private final RedisOperationsService redisOperationsService;

    /**
     * 验证码字符个数。
     */
    private final int length;

    /**
     * 验证码有效期。
     */
    private final Duration timeToLive;

    /**
     * 图片宽度，单位像素。
     */
    private final int width;

    /**
     * 图片高度，单位像素。
     */
    private final int height;

    /**
     * 单个 IP 每分钟最多可生成的次数。
     */
    private final int maxPerMinute;

    /**
     * 创建图片验证码服务并校验配置取值范围。
     *
     * @param redisOperationsService Redis 基础操作组件
     * @param length 验证码字符个数，默认 4
     * @param timeToLive 验证码有效期，默认 5 分钟
     * @param width 图片宽度，默认 120 像素
     * @param height 图片高度，默认 40 像素
     * @param maxPerMinute 单 IP 每分钟生成上限，默认 20 次
     * @throws IllegalArgumentException 任一配置值越界时抛出
     */
    public CaptchaServiceImpl(
            RedisOperationsService redisOperationsService,
            @Value("${security.captcha.length:4}") int length,
            @Value("${security.captcha.ttl:5m}") Duration timeToLive,
            @Value("${security.captcha.width:120}") int width,
            @Value("${security.captcha.height:40}") int height,
            @Value("${security.captcha.max-per-minute:20}") int maxPerMinute
    ) {
        // 字符个数过少会削弱防护效果，过多则影响图片可读性。
        if (length < 3 || length > 8) {
            throw new IllegalArgumentException(
                    "验证码字符个数必须在 3 到 8 之间"
            );
        }
        // Redis TTL 必须为正数，否则验证码写入后立即过期。
        if (timeToLive == null
                || timeToLive.isNegative()
                || timeToLive.isZero()
                || timeToLive.toMillis() < 1L) {
            throw new IllegalArgumentException(
                    "验证码有效期必须至少为 1 毫秒"
            );
        }
        // 图片过小会导致字符挤压变形，无法辨认。
        if (width < 80 || height < 30) {
            throw new IllegalArgumentException(
                    "验证码图片尺寸不能小于 80x30"
            );
        }
        // 限流阈值必须为正数，零表示完全禁止生成。
        if (maxPerMinute < 1) {
            throw new IllegalArgumentException(
                    "验证码单 IP 每分钟生成上限必须大于零"
            );
        }

        this.redisOperationsService = redisOperationsService;
        this.length = length;
        this.timeToLive = timeToLive;
        this.width = width;
        this.height = height;
        this.maxPerMinute = maxPerMinute;
    }

    /**
     * 生成图片验证码并写入 Redis。
     *
     * @param clientIp 请求来源 IP
     * @return 验证码标识、图片 Base64 与有效期
     * @throws BusinessException 生成过于频繁或 Redis 不可用时抛出
     */
    @Override
    public CaptchaVO generate(String clientIp) {
        // 先做频次限制，避免匿名接口被大量调用消耗图片绘制资源。
        checkGenerateRate(clientIp);

        // 生成随机答案，答案本身不返回给客户端。
        String answer = randomAnswer();

        // 随机标识作为 Redis Key 的一部分，客户端无法枚举有效验证码。
        String captchaId = UUID.randomUUID()
                .toString()
                .replace("-", "");

        try {
            // 答案统一存小写，校验时忽略大小写差异，避免用户因大小写误判。
            redisOperationsService.setValue(
                    CAPTCHA_KEY_PREFIX + captchaId,
                    answer.toLowerCase(),
                    timeToLive
            );
        } catch (DataAccessException exception) {
            // 无法写入答案时不能返回一张永远校验不过的图片。
            throw serviceUnavailableException();
        }

        // 绘制图片并返回给客户端。
        return CaptchaVO.builder()
                .captchaId(captchaId)
                .imageBase64(renderImage(answer))
                .expiresIn(timeToLive.toSeconds())
                .build();
    }

    /**
     * 校验验证码，无论成功失败都会消费掉该验证码。
     *
     * @param captchaId 验证码标识
     * @param captchaCode 用户输入的验证码字符
     * @return 验证码存在且与答案一致时返回 true
     * @throws BusinessException Redis 不可用、无法确认校验结果时抛出
     */
    @Override
    public boolean verify(String captchaId, String captchaCode) {
        // 参数缺失直接判失败；这里不抛参数异常，避免泄露内部校验细节。
        if (captchaId == null
                || captchaId.isBlank()
                || captchaCode == null
                || captchaCode.isBlank()) {
            return false;
        }

        try {
            // GETDEL 原子读取并删除：并发提交时只有一个请求能拿到答案。
            String expected = redisOperationsService.getAndDelete(
                    CAPTCHA_KEY_PREFIX + captchaId
            );

            // Key 不存在说明验证码已过期、已被使用或标识伪造。
            if (expected == null) {
                return false;
            }

            // 忽略首尾空白与大小写差异后比较。
            return expected.equalsIgnoreCase(captchaCode.strip());
        } catch (DataAccessException exception) {
            // 无法确认验证码状态时 fail-closed，不放行本次登录。
            throw serviceUnavailableException();
        }
    }

    /**
     * 按来源 IP 统计每分钟生成次数，超过上限时拒绝生成。
     *
     * @param clientIp 请求来源 IP
     * @throws BusinessException 超过限流阈值或 Redis 不可用时抛出
     */
    private void checkGenerateRate(String clientIp) {
        // IP 缺失时归入同一兜底分组，避免空值拼接出无意义的 Key。
        String normalizedIp = clientIp == null || clientIp.isBlank()
                ? "unknown"
                : clientIp.strip();

        try {
            // 计数窗口固定为 1 分钟，首次计数时设置 TTL，后续自增不续期。
            Long count = redisOperationsService.increment(
                    GENERATE_RATE_KEY_PREFIX + normalizedIp,
                    Duration.ofMinutes(1L)
            );

            // 无法确认计数结果时按保护组件不可用处理，不放行生成。
            if (count == null) {
                throw serviceUnavailableException();
            }

            // 超过阈值返回频率限制错误，与登录失败锁定的错误码保持一致。
            if (count > maxPerMinute) {
                throw new BusinessException(
                        CommonErrorCode.TOO_MANY_REQUESTS,
                        "验证码获取过于频繁，请稍后再试"
                );
            }
        } catch (DataAccessException exception) {
            // Redis 故障时拒绝生成，避免限流失效。
            throw serviceUnavailableException();
        }
    }

    /**
     * 生成指定长度的随机验证码答案。
     *
     * @return 由易识别字符组成的验证码
     */
    private String randomAnswer() {
        // 使用 StringBuilder 逐位拼接，避免 String 反复创建。
        StringBuilder answer = new StringBuilder(length);

        // 每一位都从预置字符集中独立随机选取。
        for (int index = 0; index < length; index++) {
            answer.append(
                    CHARACTER_POOL.charAt(
                            RANDOM.nextInt(CHARACTER_POOL.length())
                    )
            );
        }

        // 返回完整答案。
        return answer.toString();
    }

    /**
     * 把验证码绘制为 PNG 并编码为 Base64 数据 URI。
     *
     * @param answer 验证码答案
     * @return 可直接作为图片 src 的 data URI
     * @throws BusinessException PNG 编码失败时抛出
     */
    private String renderImage(String answer) {
        // 创建 RGB 图片，验证码不需要透明通道。
        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB
        );

        // 取得绘图上下文，所有绘制完成后必须释放。
        Graphics2D graphics = image.createGraphics();
        try {
            // 开启抗锯齿，让旋转后的字符边缘不发毛。
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            // 使用与登录页一致的米色背景，避免验证码图片与页面割裂。
            graphics.setColor(new Color(0xFD, 0xF6, 0xF0));
            graphics.fillRect(0, 0, width, height);

            // 绘制干扰线，增加自动化识别的难度。
            drawNoiseLines(graphics);

            // 绘制噪点。
            drawNoiseDots(graphics);

            // 逐字符绘制，每个字符带随机旋转与颜色。
            drawCharacters(graphics, answer);
        } finally {
            // 释放绘图上下文占用的本地资源。
            graphics.dispose();
        }

        try {
            // 把图片写入内存字节数组，随后转成 Base64。
            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);

            // 返回浏览器与图片组件都能直接使用的数据 URI。
            return "data:image/png;base64,"
                    + Base64.getEncoder()
                    .encodeToString(outputStream.toByteArray());
        } catch (IOException exception) {
            // PNG 编码属于内存操作，失败代表运行时异常，应明确报错。
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "验证码图片生成失败"
            );
        }
    }

    /**
     * 绘制随机干扰线。
     *
     * @param graphics 绘图上下文
     */
    private void drawNoiseLines(Graphics2D graphics) {
        // 干扰线数量按图片高度缩放，保证小图不至于被线条糊满。
        int lineCount = Math.max(2, height / 12);

        // 线条使用浅色，只影响机器识别，不遮挡字符主体。
        graphics.setStroke(new BasicStroke(1.2f));

        for (int index = 0; index < lineCount; index++) {
            // 每条线使用低透明度颜色，叠加后仍保持背景明亮。
            graphics.setColor(new Color(
                    0xC2, 0x41, 0x0C, 60 + RANDOM.nextInt(60)
            ));
            graphics.drawLine(
                    RANDOM.nextInt(width),
                    RANDOM.nextInt(height),
                    RANDOM.nextInt(width),
                    RANDOM.nextInt(height)
            );
        }
    }

    /**
     * 绘制随机噪点。
     *
     * @param graphics 绘图上下文
     */
    private void drawNoiseDots(Graphics2D graphics) {
        // 噪点数量与图片面积成正比。
        int dotCount = width * height / 30;

        for (int index = 0; index < dotCount; index++) {
            // 噪点使用半透明深色，单点不足以影响人眼辨认。
            graphics.setColor(new Color(
                    0x8C, 0x4A, 0x2F, 120
            ));
            graphics.fillRect(
                    RANDOM.nextInt(width),
                    RANDOM.nextInt(height),
                    1,
                    1
            );
        }
    }

    /**
     * 绘制带随机旋转的验证码字符。
     *
     * @param graphics 绘图上下文
     * @param answer 验证码答案
     */
    private void drawCharacters(Graphics2D graphics, String answer) {
        // 字号随图片高度缩放，保证字符既不溢出也不过于细小。
        int fontSize = (int) Math.round(height * 0.62);
        graphics.setFont(
                new Font(Font.SANS_SERIF, Font.BOLD, fontSize)
        );

        // 把可用宽度平均分给每个字符，并在前后各留出半个字符的边距。
        int step = width / (answer.length() + 1);

        for (int index = 0; index < answer.length(); index++) {
            // 字符中心横坐标：按序号等距分布。
            int centerX = step * (index + 1);
            // 基线纵坐标：让字符垂直居中。
            int baselineY = height / 2 + fontSize / 3;

            // 每个字符随机旋转，范围为 -20 度到 20 度。
            double angle = (RANDOM.nextDouble() - 0.5) * 0.7;

            // 旋转围绕字符自身中心进行，避免整体偏移。
            graphics.rotate(angle, centerX, baselineY);

            // 从预置颜色中选一种，同一张图内颜色有变化但不刺眼。
            graphics.setColor(
                    CHARACTER_COLORS[RANDOM.nextInt(
                            CHARACTER_COLORS.length
                    )]
            );

            // 以字符中心为基准向左偏移半个字宽，使字符居中绘制。
            graphics.drawString(
                    String.valueOf(answer.charAt(index)),
                    centerX - fontSize / 2,
                    baselineY
            );

            // 恢复坐标变换，避免旋转累计影响后续字符。
            graphics.rotate(-angle, centerX, baselineY);
        }
    }

    /**
     * 创建统一的验证码服务不可用异常。
     *
     * @return 映射为 HTTP 503 的业务异常
     */
    private BusinessException serviceUnavailableException() {
        // 不向客户端透露 Redis 连接等内部细节。
        return new BusinessException(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "验证码服务暂时不可用，请稍后重试"
        );
    }
}
