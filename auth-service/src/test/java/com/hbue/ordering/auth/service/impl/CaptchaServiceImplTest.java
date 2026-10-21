package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.vo.CaptchaVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图片验证码服务测试。
 *
 * @author order-system
 * @date 2026-10-21
 */
class CaptchaServiceImplTest {

    /**
     * 测试使用的验证码有效期。
     */
    private static final Duration TTL = Duration.ofMinutes(5L);

    /**
     * 创建待测服务，固定长度、尺寸与限流阈值便于断言。
     *
     * @param redisOperationsService Redis 测试替身
     * @return 图片验证码服务
     */
    private CaptchaServiceImpl createService(
            RedisOperationsService redisOperationsService
    ) {
        return new CaptchaServiceImpl(
                redisOperationsService,
                4,
                TTL,
                120,
                40,
                20
        );
    }

    /**
     * 验证生成接口返回标识与图片，并把小写答案按 TTL 写入 Redis。
     */
    @Test
    void shouldGenerateCaptchaAndStoreLowerCaseAnswer() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        when(redisOperationsService.increment(anyString(), any(Duration.class)))
                .thenReturn(1L);

        CaptchaVO captcha = createService(redisOperationsService)
                .generate("127.0.0.1");

        // 标识与有效期必须返回给客户端。
        assertNotNull(captcha.getCaptchaId());
        assertEquals(TTL.toSeconds(), captcha.getExpiresIn());

        // 图片必须是可直接用于 src 的 PNG 数据 URI。
        assertTrue(
                captcha.getImageBase64()
                        .startsWith("data:image/png;base64,")
        );

        // 答案按 captchaId 存储，长度与配置一致且统一为小写。
        ArgumentCaptor<String> answerCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(redisOperationsService).setValue(
                eq("auth:captcha:" + captcha.getCaptchaId()),
                answerCaptor.capture(),
                eq(TTL)
        );
        assertEquals(4, answerCaptor.getValue().length());
        assertEquals(
                answerCaptor.getValue().toLowerCase(),
                answerCaptor.getValue()
        );
    }

    /**
     * 验证校验时忽略大小写，并通过 GETDEL 消费掉验证码。
     */
    @Test
    void shouldVerifyCaseInsensitivelyAndConsumeCaptcha() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        when(redisOperationsService.increment(anyString(), any(Duration.class)))
                .thenReturn(1L);

        CaptchaServiceImpl captchaService =
                createService(redisOperationsService);
        CaptchaVO captcha = captchaService.generate("127.0.0.1");

        // 取出服务写入 Redis 的答案，模拟用户输入其大写形式。
        ArgumentCaptor<String> answerCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(redisOperationsService).setValue(
                anyString(),
                answerCaptor.capture(),
                eq(TTL)
        );
        String answer = answerCaptor.getValue();

        when(redisOperationsService.getAndDelete(
                "auth:captcha:" + captcha.getCaptchaId()
        )).thenReturn(answer);

        assertTrue(captchaService.verify(
                captcha.getCaptchaId(),
                answer.toUpperCase()
        ));

        // 校验必须走原子读取删除，不能只读取。
        verify(redisOperationsService).getAndDelete(
                "auth:captcha:" + captcha.getCaptchaId()
        );
    }

    /**
     * 验证已被消费或已过期的验证码无法再次通过。
     */
    @Test
    void shouldRejectCaptchaWhenItWasAlreadyConsumed() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        when(redisOperationsService.getAndDelete(anyString()))
                .thenReturn(null);

        CaptchaServiceImpl captchaService =
                createService(redisOperationsService);

        assertFalse(captchaService.verify("captcha-id", "ABCD"));
    }

    /**
     * 验证同一 IP 超过每分钟生成上限时返回频率限制错误。
     */
    @Test
    void shouldRejectGenerateWhenRateLimitExceeded() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        when(redisOperationsService.increment(anyString(), any(Duration.class)))
                .thenReturn(21L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> createService(redisOperationsService)
                        .generate("127.0.0.1")
        );

        assertEquals(
                CommonErrorCode.TOO_MANY_REQUESTS,
                exception.getErrorCode()
        );
    }

    /**
     * 验证 Redis 不可用时生成接口返回服务暂不可用，而不是返回无法校验的图片。
     */
    @Test
    void shouldReturnServiceUnavailableWhenRedisFails() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        when(redisOperationsService.increment(anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> createService(redisOperationsService)
                        .generate("127.0.0.1")
        );

        assertEquals(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );
    }
}
