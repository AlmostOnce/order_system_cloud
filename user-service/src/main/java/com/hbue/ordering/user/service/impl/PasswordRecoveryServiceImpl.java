package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.service.EmailCodeSender;
import com.hbue.ordering.user.service.PasswordRecoveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 找回密码的权威实现。
 *
 * <p>发送环节做三层限流（目标邮箱维度 1 次/分钟、3 次/10 分钟），既不泄露邮箱是否注册，
 * 也不让接口成为邮件轰炸通道。验证码与重置凭据都是 HMAC 摘要后存 Redis，
 * 库中不出现可用明文。</p>
 *
 * <p>重置凭据一次性消费且 5 分钟有效。确认时先校验密码规则，再用 Lua 原子消费凭据，
 * 最后在带邮箱与凭据版本条件的 UPDATE 中写入新密码：凭据版本或邮箱已变化时拒绝旧授权，
 * 密码与会话撤销版本同库原子更新。</p>
 *
 * <p>未配置签名密钥（{@code email.verification.secret}）时拒绝服务而不是降级为无签名校验，
 * 否则任何人都能伪造有效的验证码摘要。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    /**
     * 当前类日志对象。
     */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PasswordRecoveryServiceImpl.class);

    /**
     * 验证码生成用的安全随机源。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 用户数据访问。
     */
    private final UserMapper users;

    /**
     * 验证码与凭据的 Redis 存储。
     */
    private final RedisOperationsService redis;

    /**
     * 邮件发送器。
     */
    private final EmailCodeSender sender;

    /**
     * 密码加密器。
     */
    private final PasswordEncoder encoder;

    /**
     * 摘要签名密钥，缺失时拒绝服务。
     */
    private final String secret;

    /**
     * 创建找回密码服务。
     *
     * @param users   用户数据访问
     * @param redis   验证码与凭据存储
     * @param sender  邮件发送器
     * @param encoder 密码加密器
     * @param secret  摘要签名密钥
     */
    public PasswordRecoveryServiceImpl(UserMapper users, RedisOperationsService redis, EmailCodeSender sender,
                                       PasswordEncoder encoder, @Value("${email.verification.secret:}") String secret) {
        this.users = users;
        this.redis = redis;
        this.sender = sender;
        this.encoder = encoder;
        this.secret = secret;
    }

    public void send(String input) {
        String email = email(input);
        String target = digest("target", email);
        Long minute = redis.increment("password-recovery:send:minute:" + target, Duration.ofMinutes(1));
        Long window = redis.increment("password-recovery:send:window:" + target, Duration.ofMinutes(10));
        if (minute == null || window == null || minute > 1 || window > 3)
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
        var user = users.selectRecoveryByEmail(email);
        // 无账号时返回相同成功结果，不发送邮件、不创建任何账号。
        if (user == null) return;
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String value = digest("code", email + "\n" + code) + ":0";
        String key = codeKey(email);
        redis.setValue(key, value, Duration.ofMinutes(10));
        try {
            sender.sendPasswordReset(email, code);
        } catch (RuntimeException error) {
            redis.executeScript(EmailVerificationImpl.REMOVE_IF_SAME, List.of(key), value.substring(0, 64));
            // 对外保持相同受理结果，不借 SMTP 失败暴露邮箱是否有账号。
            log.warn("找回密码邮件发送失败，等待重新申请");
        }
    }

    public PasswordRecoveryGrantVO verify(String input, String code) {
        String email = email(input);
        if (code == null || !code.matches("[0-9]{6}")) throw bad("请输入六位邮箱验证码");
        Long verified = redis.executeScript(EmailVerificationImpl.CONSUME, List.of(codeKey(email)), digest("code", email + "\n" + code), "5");
        if (!Long.valueOf(1).equals(verified)) throw bad("邮箱验证码错误、已过期或已使用");
        var user = users.selectRecoveryByEmail(email);
        if (user == null) throw bad("验证已失效，请重新获取验证码");
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        String value = user.getUserId() + "\n" + user.getCredentialVersion() + "\n" + email;
        redis.setValue(grantKey(token), value, Duration.ofMinutes(5));
        return new PasswordRecoveryGrantVO(token, 300);
    }

    @Transactional
    public void confirm(String token, String password) {
        if (secret == null || secret.length() < 32)
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "邮箱验证服务尚未配置");
        if (password == null || password.length() < 6 || password.length() > 64 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw bad("新密码须为6至64个字符，UTF-8编码不超过72字节");
        if (token == null || !token.matches("[a-f0-9]{64}")) throw bad("验证已失效，请重新验证邮箱");
        String value = redis.getValue(grantKey(token));
        if (value == null) throw bad("验证已失效，请重新验证邮箱");
        String[] fields = value.split("\n", -1);
        if (fields.length != 3) throw bad("验证已失效，请重新验证邮箱");
        Long id;
        long version;
        try {
            id = Long.valueOf(fields[0]);
            version = Long.parseLong(fields[1]);
        } catch (NumberFormatException error) {
            throw bad("验证已失效，请重新验证邮箱");
        }
        var current = users.selectPasswordCredentialForUpdate(id);
        if (current == null || !fields[2].equals(current.getEmail())
                || !Long.valueOf(version).equals(current.getCredentialVersion()))
            throw bad("账号状态或邮箱已变化，请重新验证");
        // BCrypt 使用随机盐，必须校验明文与现有哈希，不能比较两个新生成的哈希。
        if (encoder.matches(password, current.getPasswordHash())) throw bad("新密码不能与旧密码相同，请设置其他密码");
        Long consumed = redis.executeScript(EmailVerificationImpl.CONSUME_MATCHING_TICKET,
                List.of(grantKey(token)), value);
        if (!Long.valueOf(1).equals(consumed)) throw bad("验证已失效，请重新验证邮箱");
        // 邮箱或凭据版本变化会拒绝旧授权；密码与会话撤销版本同库原子更新。
        int updated = users.resetLoginPassword(id, fields[2], version, encoder.encode(password));
        if (updated != 1) throw bad("账号状态或邮箱已变化，请重新验证");
    }

    private String email(String input) {
        if (secret == null || secret.length() < 32)
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "邮箱验证服务尚未配置");
        String value = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw bad("请输入正确的邮箱地址");
        return value;
    }

    private String digest(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("password-recovery\n" + purpose + "\n" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("找回密码凭据计算失败", error);
        }
    }

    private String codeKey(String email) {
        return "password-recovery:code:" + digest("target", email);
    }

    private String grantKey(String token) {
        return "password-recovery:grant:" + digest("grant", token);
    }

    private static BusinessException bad(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }
}
