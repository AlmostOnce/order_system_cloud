package com.hbue.ordering.auth.service;

import com.hbue.ordering.auth.vo.CaptchaVO;
import com.hbue.ordering.common.core.exception.BusinessException;

/**
 * 图片验证码服务。
 *
 * <p>负责生成一次性图片验证码、把答案写入 Redis，并在登录时校验客户端提交的
 * 验证码。答案只保存在 Redis 中，不落库、不写入日志。</p>
 *
 * @author order-system
 * @date 2026-10-21
 */
public interface CaptchaService {

    /**
     * 生成一张新的图片验证码。
     *
     * @param clientIp 请求来源 IP，用于限制单个 IP 的生成频率
     * @return 验证码标识、图片 Base64 与有效期
     * @throws BusinessException 同一 IP 生成过于频繁或 Redis 不可用时抛出
     */
    CaptchaVO generate(String clientIp);

    /**
     * 校验验证码，校验成功或失败都会使该验证码立即失效。
     *
     * @param captchaId 验证码标识
     * @param captchaCode 用户输入的验证码字符
     * @return 验证码存在且与答案一致时返回 true
     * @throws BusinessException Redis 不可用、无法确认校验结果时抛出
     */
    boolean verify(String captchaId, String captchaCode);
}
