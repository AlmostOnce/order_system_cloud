package com.hbue.ordering.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 图片验证码响应 VO。
 *
 * <p>只返回验证码标识与图片内容，不返回验证码明文。客户端提交登录请求时
 * 需要原样回传 {@code captchaId}，输入框中用户看到的字符由客户端提交为
 * {@code captchaCode}。</p>
 *
 * @author order-system
 * @date 2026-10-21
 */
@Getter
@Builder
public class CaptchaVO {

    /**
     * 验证码标识，对应 Redis 中保存答案的 Key。
     */
    private String captchaId;

    /**
     * PNG 图片的 Base64 数据 URI，可直接作为图片组件的 src。
     */
    private String imageBase64;

    /**
     * 验证码有效期，单位为秒。
     */
    private Long expiresIn;
}
