package com.hbue.ordering.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.auth.dto.request.PasswordRecoveryCodeRequest;
import com.hbue.ordering.auth.service.CaptchaService;
import com.hbue.ordering.auth.service.PasswordRecoveryService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.PasswordRecoveryClient;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
import feign.FeignException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {
    private final PasswordRecoveryClient users;
    private final CaptchaService captcha;
    private final RedisOperationsService redis;
    private final ObjectMapper json;
    public PasswordRecoveryServiceImpl(PasswordRecoveryClient users,CaptchaService captcha,RedisOperationsService redis,ObjectMapper json) {
        this.users=users;this.captcha=captcha;this.redis=redis;this.json=json;
    }
    public void send(PasswordRecoveryCodeRequest body,String source) {
        limit("send",source,10);
        if(!captcha.verify(body.captchaId(),body.captchaCode()))throw new BusinessException(CommonErrorCode.BAD_REQUEST,"图片验证码错误或已过期");
        call(()->users.send(new PasswordRecoveryCodeCommand(body.email())));
    }
    public PasswordRecoveryGrantVO verify(PasswordRecoveryVerifyCommand body,String source) {
        limit("verify",source,30);
        var grant=call(()->users.verify(body));
        if(grant==null||grant.resetToken()==null||!grant.resetToken().matches("[a-f0-9]{64}"))throw unavailable();
        return grant;
    }
    public void confirm(PasswordRecoveryConfirmCommand body,String source) {
        limit("confirm",source,20);call(()->users.confirm(body));
    }
    private void limit(String action,String source,int max) {
        String hash;
        try {hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
        Long count=redis.increment("password-recovery:ip:"+action+":"+hash,Duration.ofMinutes(10));
        if(count==null||count>max)throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,"操作过于频繁，请稍后再试");
    }
    private <T> T call(Supplier<ApiResponse<T>> operation) {
        try {
            var response=operation.get();
            if(response==null)throw unavailable();
            if(!"COMMON_000".equals(response.getCode()))throw failure(response.getCode(),response.getMessage());
            return response.getData();
        } catch(FeignException error) {
            if(error.status()==400||error.status()==409||error.status()==422||error.status()==429) {
                try {var body=json.readTree(error.contentUTF8());if(body==null)throw unavailable();throw failure(body.path("code").asText(),body.path("message").asText());}
                catch(com.fasterxml.jackson.core.JsonProcessingException malformed){throw unavailable();}
            }
            throw unavailable();
        }
    }
    private BusinessException failure(String code,String message) {
        for(var error:CommonErrorCode.values())if(error.code().equals(code))
            return new BusinessException(error,message==null||message.isBlank()?"验证失败，请重新获取验证码":message);
        return unavailable();
    }
    private BusinessException unavailable(){return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"找回密码服务暂不可用，请稍后再试");}
}
