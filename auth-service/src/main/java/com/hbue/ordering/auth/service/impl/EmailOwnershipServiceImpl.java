package com.hbue.ordering.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.auth.dto.request.EmailOwnershipSendRequest;
import com.hbue.ordering.auth.service.CaptchaService;
import com.hbue.ordering.auth.service.EmailOwnershipService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.EmailOwnershipClient;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.EmailEnrollmentChallengeVO;
import com.hbue.ordering.user.api.vo.EmailRegistrationGrantVO;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * 邮箱归属注册校验与账号绑定的实现。
 *
 * <p>本类只做三件事：按来源 IP 做滑动窗口频控、校验图片验证码、转调用户服务并把失败翻译为统一业务码。
 * 邮箱是否已被占用、验证码是否正确等权威判断全部在用户服务，避免两处规则各写一份而漂移。</p>
 *
 * <p>频控键按"动作 + 来源 IP 摘要"区分：发送 10 次/10 分钟，校验与确认 30 次/10 分钟。
 * 用 SHA-256 摘要而不是明文 IP，避免 Redis 中出现可直接关联到个人的网络标识。</p>
 *
 * <p>远端失败处理原则：400/409/429 视为业务失败并原样透出错误码与提示，其余状态（超时、5xx、
 * 响应体损坏）统一收敛为 503，调用方据此提示"稍后重试"而不是误报"验证码错误"。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
@Service
public class EmailOwnershipServiceImpl implements EmailOwnershipService {
    private final EmailOwnershipClient users;
    private final CaptchaService captcha;
    private final RedisOperationsService redis;
    private final ObjectMapper json;

    public EmailOwnershipServiceImpl(EmailOwnershipClient users, CaptchaService captcha, RedisOperationsService redis, ObjectMapper json) {
        this.users = users;
        this.captcha = captcha;
        this.redis = redis;
        this.json = json;
    }

    public void sendRegistration(EmailOwnershipSendRequest body, String source) {
        limit("registration:send", source, 10);
        checkCaptcha(body);
        call(() -> users.sendRegistration(new RegistrationEmailSendCommand(body.email())));
    }

    public EmailRegistrationGrantVO verifyRegistration(RegistrationEmailVerifyCommand body, String source) {
        limit("registration:verify", source, 30);
        var result = call(() -> users.verifyRegistration(body));
        if (result == null || result.verificationToken() == null || !result.verificationToken().matches("[a-f0-9]{64}")
                || result.expiresIn() <= 0 || result.expiresIn() > 300) throw unavailable();
        return result;
    }

    public EmailEnrollmentChallengeVO challenge(UserBasicVO user) {
        var result = call(() -> users.challenge(new EmailEnrollmentChallengeCommand(user.getUserId(), user.getCredentialVersion())));
        if (result == null || result.authorizationToken() == null || !result.authorizationToken().matches("[a-f0-9]{64}")
                || result.expiresIn() <= 0 || result.expiresIn() > 600 || !user.getRoleCode().equals(result.roleCode()))
            throw unavailable();
        return result;
    }

    public void sendEnrollment(EmailOwnershipSendRequest body, String source) {
        limit("enrollment:send", source, 10);
        checkCaptcha(body);
        call(() -> users.sendEnrollment(new EmailEnrollmentSendCommand(body.authorizationToken(), body.email())));
    }

    public void confirmEnrollment(EmailEnrollmentConfirmCommand body, String source) {
        limit("enrollment:confirm", source, 30);
        call(() -> users.confirmEnrollment(body));
    }

    private void checkCaptcha(EmailOwnershipSendRequest body) {
        if (!captcha.verify(body.captchaId(), body.captchaCode()))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "图片验证码错误或已过期");
    }

    private void limit(String action, String source, int maximum) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
            Long count = redis.increment("email-ownership:ip:" + action + ":" + hash, Duration.ofMinutes(10));
            if (count == null || count > maximum)
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private <T> T call(Supplier<ApiResponse<T>> operation) {
        try {
            var response = operation.get();
            if (response == null) throw unavailable();
            if (!CommonErrorCode.SUCCESS.code().equals(response.getCode())) throw failure(response.getCode(), response.getMessage());
            return response.getData();
        } catch (FeignException error) {
            if (error.status() == 400 || error.status() == 409 || error.status() == 429) {
                try {
                    var body = json.readTree(error.contentUTF8());
                    if (body == null) throw unavailable();
                    throw failure(body.path("code").asText(), body.path("message").asText());
                } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
                    throw unavailable();
                }
            }
            throw unavailable();
        }
    }

    private BusinessException failure(String code, String message) {
        for (var error : CommonErrorCode.values())
            if (error.code().equals(code))
                return new BusinessException(error, message == null || message.isBlank() ? "邮箱验证失败" : message);
        return unavailable();
    }

    private static BusinessException unavailable() {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "邮箱验证服务暂不可用，请稍后重试");
    }
}
