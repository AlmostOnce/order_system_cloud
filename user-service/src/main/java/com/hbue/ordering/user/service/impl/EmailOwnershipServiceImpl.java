package com.hbue.ordering.user.service.impl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.api.vo.*;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

/** 注册授权与旧账号补验严格分开用途；不签发或接受普通登录Token。 */
@Service
public class EmailOwnershipServiceImpl implements EmailOwnershipService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final UserMapper users;
    private final RedisOperationsService redis;
    private final EmailCodeSender sender;
    private final String secret;
    public EmailOwnershipServiceImpl(UserMapper users,RedisOperationsService redis,EmailCodeSender sender,
            @Value("${email.verification.secret:}") String secret) {
        this.users=users;this.redis=redis;this.sender=sender;this.secret=secret;
    }
    @Override public void sendRegistration(String input) {
        String email=email(input);availableEmail(email,null);
        rate("registration:target:"+digest("email",email),1,60);
        rate("registration:window:"+digest("email",email),3,600);
        send("registration",digest("email",email),email,email);
    }
    @Override public EmailRegistrationGrantVO verifyRegistration(String input,String code) {
        String email=email(input);
        consumeCode("registration",digest("email",email),email,code);
        String token=randomToken();redis.setValue(key("registration:grant",token),email,Duration.ofMinutes(5));
        return new EmailRegistrationGrantVO(token,300);
    }
    @Override public void consumeRegistration(String input,String token) {
        String email=email(input);validToken(token);
        consumeExact(key("registration:grant",token),email);
    }
    @Override public EmailEnrollmentChallengeVO challenge(Long id,long version) {
        configured();
        if(id==null || id<=0 || version<0)throw bad("账号验证信息无效");
        UserDO user=available(users.selectEmailEnrollment(id));
        if(!Long.valueOf(version).equals(user.getCredentialVersion()))throw bad("登录凭据已变化，请重新登录");
        rate("enrollment:challenge:"+id,10,600);
        String token=randomToken();
        redis.setValue(key("enrollment:authorization",token),id+"\n"+version+"\n"+snapshot(user),Duration.ofMinutes(10));
        return new EmailEnrollmentChallengeVO(token,user.getRoleCode(),600);
    }
    @Override public void sendEnrollment(String token,String input) {
        String email=email(input);var proof=enrollment(token,false);availableEmail(email,proof.user().getUserId());
        rate("enrollment:user:"+proof.user().getUserId(),1,60);
        rate("enrollment:user-window:"+proof.user().getUserId(),3,600);
        rate("enrollment:target:"+digest("email",email),3,600);
        send("enrollment",token,email,proof.value()+"\n"+email);
    }
    @Override @Transactional public void confirmEnrollment(String token,String input,String code) {
        String email=email(input);var proof=enrollment(token,true);availableEmail(email,proof.user().getUserId());
        consumeCode("enrollment",token,proof.value()+"\n"+email,code);
        consumeExact(key("enrollment:authorization",token),proof.value());
        try {
            if(users.completeEmailEnrollment(proof.user().getUserId(),email)!=1)throw bad("账号状态已变化，请重新登录验证");
        } catch(DuplicateKeyException conflict) {throw new BusinessException(CommonErrorCode.CONFLICT,"邮箱已被其他账号使用，请重新验证其他邮箱");}
    }
    private record Enrollment(UserDO user,String value) {}
    private Enrollment enrollment(String token,boolean locked) {
        configured();validToken(token);
        String value=redis.getValue(key("enrollment:authorization",token));
        if(value==null)throw expired();
        String[] fields=value.split("\n",-1);if(fields.length!=3)throw expired();
        Long id;long version;
        try{id=Long.valueOf(fields[0]);version=Long.parseLong(fields[1]);}catch(NumberFormatException error){throw expired();}
        UserDO user=available(locked?users.selectEmailEnrollmentForUpdate(id):users.selectEmailEnrollment(id));
        if(!Long.valueOf(version).equals(user.getCredentialVersion()) || !fields[2].equals(snapshot(user)))throw expired();
        return new Enrollment(user,value);
    }
    private void send(String purpose,String target,String email,String binding) {
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));
        String value=digest(purpose+":code",binding+"\n"+code)+":0";
        String key=key(purpose+":code",target);redis.setValue(key,value,Duration.ofMinutes(10));
        try {sender.sendOwnershipVerification(email,code);}
        catch(RuntimeException error){redis.executeScript(EmailVerificationService.REMOVE_IF_SAME,List.of(key),value.substring(0,64));throw error;}
    }
    private void consumeCode(String purpose,String target,String binding,String code) {
        if(code==null || !code.matches("[0-9]{6}"))throw bad("请输入六位邮箱验证码");
        Long consumed=redis.executeScript(EmailVerificationService.CONSUME,List.of(key(purpose+":code",target)),digest(purpose+":code",binding+"\n"+code),"5");
        if(!Long.valueOf(1).equals(consumed))throw bad("邮箱验证码错误、已过期或已使用");
    }
    private void consumeExact(String key,String value) {
        Long result=redis.executeScript(EmailVerificationService.CONSUME_MATCHING_TICKET,List.of(key),value);
        if(!Long.valueOf(1).equals(result))throw expired();
    }
    private UserDO available(UserDO user) {
        if(user==null || !Integer.valueOf(1).equals(user.getStatus()) || user.getDeletedAt()!=null)throw bad("账号不可用，请重新登录");
        if(user.getEmailVerifiedAt()!=null)throw bad("邮箱已完成验证，请重新登录");
        return user;
    }
    private void availableEmail(String email,Long owner) {
        var existing=users.selectByEmail(email);
        if(existing!=null && (owner==null || !owner.equals(existing.getUserId())))
            throw new BusinessException(CommonErrorCode.CONFLICT,"邮箱已被使用");
    }
    private String email(String input) {
        configured();String email=input==null?"":input.trim().toLowerCase(Locale.ROOT);
        if(email.length()>254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw bad("请输入有效的邮箱地址");
        return email;
    }
    private String snapshot(UserDO user) {return digest("snapshot",user.getUserId()+"\n"+user.getCredentialVersion()+"\n"+user.getEmail()+"\n"+user.getPasswordHash()+"\n"+user.getRoleCode());}
    private void validToken(String token) {if(token==null || !token.matches("[a-f0-9]{64}"))throw expired();}
    private String key(String purpose,String token) {return "email-ownership:"+purpose+":"+digest(purpose,token);}
    private void rate(String suffix,int maximum,int seconds) {
        Long count=redis.increment("email-ownership:limit:"+suffix,Duration.ofSeconds(seconds));
        if(count==null || count>maximum)throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,"操作过于频繁，请稍后重试");
    }
    private String randomToken(){byte[] value=new byte[32];RANDOM.nextBytes(value);return HexFormat.of().formatHex(value);}
    private String digest(String purpose,String input) {
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("email-ownership\n"+purpose+"\n"+input).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.GeneralSecurityException error){throw new IllegalStateException("邮箱验证凭据计算失败",error);}
    }
    private void configured(){if(secret==null || secret.length()<32)throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"邮箱验证服务尚未配置");}
    private static BusinessException expired(){return bad("邮箱验证凭据已失效，请重新验证");}
    private static BusinessException bad(String message){return new BusinessException(CommonErrorCode.BAD_REQUEST,message);}
}
