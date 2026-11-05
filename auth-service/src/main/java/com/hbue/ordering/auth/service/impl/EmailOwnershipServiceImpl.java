package com.hbue.ordering.auth.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.auth.dto.request.EmailOwnershipSendRequest;
import com.hbue.ordering.auth.service.*;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.EmailOwnershipClient;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.*;
import feign.FeignException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
@Service
public class EmailOwnershipServiceImpl implements EmailOwnershipService {
    private final EmailOwnershipClient users;
    private final CaptchaService captcha;
    private final RedisOperationsService redis;
    private final ObjectMapper json;
    public EmailOwnershipServiceImpl(EmailOwnershipClient users,CaptchaService captcha,RedisOperationsService redis,ObjectMapper json){
        this.users=users;this.captcha=captcha;this.redis=redis;this.json=json;
    }
    public void sendRegistration(EmailOwnershipSendRequest body,String source){
        limit("registration:send",source,10);checkCaptcha(body);
        call(()->users.sendRegistration(new RegistrationEmailSendCommand(body.email())));
    }
    public EmailRegistrationGrantVO verifyRegistration(RegistrationEmailVerifyCommand body,String source){
        limit("registration:verify",source,30);
        var result=call(()->users.verifyRegistration(body));
        if(result==null || result.verificationToken()==null || !result.verificationToken().matches("[a-f0-9]{64}")
                || result.expiresIn()<=0 || result.expiresIn()>300)throw unavailable();
        return result;
    }
    public EmailEnrollmentChallengeVO challenge(UserBasicVO user){
        var result=call(()->users.challenge(new EmailEnrollmentChallengeCommand(user.getUserId(),user.getCredentialVersion())));
        if(result==null || result.authorizationToken()==null || !result.authorizationToken().matches("[a-f0-9]{64}")
                || result.expiresIn()<=0 || result.expiresIn()>600 || !user.getRoleCode().equals(result.roleCode()))throw unavailable();
        return result;
    }
    public void sendEnrollment(EmailOwnershipSendRequest body,String source){
        limit("enrollment:send",source,10);checkCaptcha(body);
        call(()->users.sendEnrollment(new EmailEnrollmentSendCommand(body.authorizationToken(),body.email())));
    }
    public void confirmEnrollment(EmailEnrollmentConfirmCommand body,String source){
        limit("enrollment:confirm",source,30);call(()->users.confirmEnrollment(body));
    }
    private void checkCaptcha(EmailOwnershipSendRequest body){
        if(!captcha.verify(body.captchaId(),body.captchaCode()))throw new BusinessException(CommonErrorCode.BAD_REQUEST,"图片验证码错误或已过期");
    }
    private void limit(String action,String source,int maximum){
        try{
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
            Long count=redis.increment("email-ownership:ip:"+action+":"+hash,Duration.ofMinutes(10));
            if(count==null || count>maximum)throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,"操作过于频繁，请稍后重试");
        }catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    private <T> T call(Supplier<ApiResponse<T>> operation){
        try{
            var response=operation.get();if(response==null)throw unavailable();
            if(!"COMMON_000".equals(response.getCode()))throw failure(response.getCode(),response.getMessage());
            return response.getData();
        }catch(FeignException error){
            if(error.status()==400 || error.status()==409 || error.status()==429){
                try{var body=json.readTree(error.contentUTF8());if(body==null)throw unavailable();throw failure(body.path("code").asText(),body.path("message").asText());}
                catch(com.fasterxml.jackson.core.JsonProcessingException malformed){throw unavailable();}
            }
            throw unavailable();
        }
    }
    private BusinessException failure(String code,String message){
        for(var error:CommonErrorCode.values())if(error.code().equals(code))return new BusinessException(error,message==null || message.isBlank()?"邮箱验证失败":message);
        return unavailable();
    }
    private static BusinessException unavailable(){return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"邮箱验证服务暂不可用，请稍后重试");}
}
