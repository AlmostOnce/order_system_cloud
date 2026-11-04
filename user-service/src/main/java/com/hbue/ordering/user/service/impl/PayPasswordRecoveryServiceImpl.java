package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.EmailCodeSender;
import com.hbue.ordering.user.service.PayPasswordRecoveryService;
import com.hbue.ordering.user.vo.PayPasswordRecoveryAuthorizationVO;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 独立用途的支付密码恢复；所有凭据均绑定当前用户和账号凭据快照。 */
@Service
public class PayPasswordRecoveryServiceImpl implements PayPasswordRecoveryService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "pay-password-recovery:";
    private final UserMapper users;
    private final RedisOperationsService redis;
    private final EmailCodeSender sender;
    private final PasswordEncoder passwords;
    private final String secret;
    public PayPasswordRecoveryServiceImpl(UserMapper users, RedisOperationsService redis,
            EmailCodeSender sender, PasswordEncoder passwords,
            @Value("${email.verification.secret:}") String secret) {
        this.users=users; this.redis=redis; this.sender=sender; this.passwords=passwords; this.secret=secret;
    }
    @Override
    public PayPasswordRecoveryAuthorizationVO authorize(Long id, String password) {
        configured();
        limit("login:"+id, Duration.ofMinutes(10), 5);
        UserDO user = available(users.selectPayPasswordRecovery(id));
        if (password==null || password.isBlank() || password.length()>64
                || !passwords.matches(password,user.getPasswordHash())) throw bad("登录密码不正确");
        String token=randomToken();
        redis.setValue(key("authorization",id,token),snapshot(user),Duration.ofMinutes(10));
        String email=user.getEmail();
        return new PayPasswordRecoveryAuthorizationVO(token,email.substring(0,1)+"***"+email.substring(email.indexOf('@')));
    }
    @Override
    public void send(Long id, String token, String captchaId, String captchaCode) {
        configured();
        UserDO user=available(users.selectPayPasswordRecovery(id));
        String proof=proof("authorization",id,token,user);
        if(captchaId==null || !captchaId.matches("[A-Za-z0-9-]{1,64}") || captchaCode==null || captchaCode.length()>8)
            throw bad("请填写有效图片验证码");
        String expected=redis.getAndDelete("auth:captcha:"+captchaId);
        if(expected==null || !expected.equalsIgnoreCase(captchaCode.strip())) throw bad("图片验证码错误或已过期");
        limit("send:minute:"+id,Duration.ofMinutes(1),1);
        limit("send:window:"+id,Duration.ofMinutes(10),3);
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));
        String value=digest("code",id+"\n"+token+"\n"+proof+"\n"+code)+":0";
        String key=key("code",id,token);
        redis.setValue(key,value,Duration.ofMinutes(10));
        try { sender.sendPayPasswordReset(user.getEmail(),code); }
        catch(RuntimeException error) {
            redis.executeScript(EmailVerificationService.REMOVE_IF_SAME,List.of(key),value.substring(0,64));
            throw error;
        }
    }
    @Override
    public PasswordRecoveryGrantVO verify(Long id, String token, String code) {
        configured();
        UserDO user=available(users.selectPayPasswordRecovery(id));
        String proof=proof("authorization",id,token,user);
        if(code==null || !code.matches("[0-9]{6}")) throw bad("请输入六位邮箱验证码");
        Long consumed=redis.executeScript(EmailVerificationService.CONSUME,List.of(key("code",id,token)),
                digest("code",id+"\n"+token+"\n"+proof+"\n"+code),"5");
        if(!Long.valueOf(1).equals(consumed)) throw bad("邮箱验证码错误、已过期或已使用");
        // 登录凭据仅可转化为一个重置授权，不能通过反复发信重复使用。
        consume("authorization",id,token,proof);
        String grant=randomToken();
        redis.setValue(key("grant",id,grant),proof,Duration.ofMinutes(5));
        return new PasswordRecoveryGrantVO(grant,300);
    }
    @Override
    @Transactional
    public void confirm(Long id, String token, String password) {
        configured();
        if(password==null || password.isBlank() || password.length()<6 || password.length()>20
                || password.getBytes(StandardCharsets.UTF_8).length>72) throw bad("支付密码长度须为6至20位");
        UserDO user=available(users.selectPayPasswordRecoveryForUpdate(id));
        String proof=proof("grant",id,token,user);
        if(passwords.matches(password,user.getPasswordHash())) throw bad("支付密码不得与登录密码相同");
        if(passwords.matches(password,user.getPayPasswordHash())) throw bad("新支付密码不得与原支付密码相同");
        consume("grant",id,token,proof);
        if(users.updatePayPassword(id,passwords.encode(password))!=1) throw bad("账号状态已变化，请重新验证");
        // 成功重置允许恢复支付；不改余额、流水、登录密码或登录凭据版本。
        redis.delete("user:pay-password:fail:"+id);
        redis.delete("user:pay-password:lock:"+id);
    }
    private UserDO available(UserDO user) {
        if(user==null || !Integer.valueOf(1).equals(user.getStatus()) || user.getDeletedAt()!=null)
            throw bad("账号不可用");
        if(user.getEmail()==null || !user.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw bad("账号未绑定有效邮箱，请先在个人资料中绑定邮箱");
        if(user.getPayPasswordHash()==null || user.getPayPasswordHash().isBlank()) throw bad("尚未设置支付密码，请使用首次设置入口");
        return user;
    }
    private String proof(String purpose,Long id,String token,UserDO user) {
        if(token==null || !token.matches("[a-f0-9]{64}")) throw bad("验证已失效，请重新验证登录密码");
        String value=redis.getValue(key(purpose,id,token));
        if(value==null || !value.equals(snapshot(user))) throw bad("验证已过期或账号信息已变化，请重新验证登录密码");
        return value;
    }
    private void consume(String purpose,Long id,String token,String value) {
        Long result=redis.executeScript(EmailVerificationService.CONSUME_MATCHING_TICKET,List.of(key(purpose,id,token)),value);
        if(!Long.valueOf(1).equals(result)) throw bad("验证已失效，请重新验证登录密码");
    }
    private String snapshot(UserDO user) {
        return digest("snapshot",user.getUserId()+"\n"+user.getEmail()+"\n"+user.getCredentialVersion()+"\n"+user.getPasswordHash()+"\n"+user.getPayPasswordHash());
    }
    private void limit(String suffix,Duration ttl,int max) {
        Long count=redis.increment(PREFIX+suffix,ttl);
        if(count==null || count>max) throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,"操作过于频繁，请稍后再试");
    }
    private String key(String purpose,Long id,String token) { return PREFIX+purpose+":"+id+":"+digest(purpose,token); }
    private String randomToken() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private String digest(String purpose,String value) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((PREFIX+purpose+"\n"+value).getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.GeneralSecurityException error) { throw new IllegalStateException("支付密码恢复凭据计算失败",error); }
    }
    private void configured() {
        if(secret==null || secret.length()<32) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"邮箱验证服务尚未配置");
    }
    private static BusinessException bad(String message) { return new BusinessException(CommonErrorCode.BAD_REQUEST,message); }
}
