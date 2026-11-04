package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.EmailCodeSender;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayPasswordRecoveryServiceTest {
    private final UserMapper users=mock(UserMapper.class);
    private final RedisOperationsService redis=mock(RedisOperationsService.class);
    private final EmailCodeSender sender=mock(EmailCodeSender.class);
    private final PasswordEncoder passwords=mock(PasswordEncoder.class);
    private final Map<String,String> values=new HashMap<>();
    private final PayPasswordRecoveryServiceImpl service=new PayPasswordRecoveryServiceImpl(users,redis,sender,passwords,"s".repeat(32));
    private UserDO user;
    @BeforeEach void setup() {
        user=new UserDO();user.setUserId(9L);user.setEmail("user@example.com");user.setStatus(1);
        user.setCredentialVersion(2L);user.setPasswordHash("login-hash");user.setPayPasswordHash("pay-hash");
        when(users.selectPayPasswordRecovery(9L)).thenReturn(user);
        when(users.selectPayPasswordRecoveryForUpdate(9L)).thenReturn(user);
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(1L);
        when(passwords.matches("login-pass","login-hash")).thenReturn(true);
        doAnswer(call->{ values.put(call.getArgument(0),call.getArgument(1));return null; })
                .when(redis).setValue(anyString(),anyString(),any(Duration.class));
        when(redis.getValue(anyString())).thenAnswer(call->values.get(call.getArgument(0)));
    }
    @Test void wrongLoginNeverIssuesProofAndAttemptsAreBounded() {
        assertThrows(BusinessException.class,()->service.authorize(9L,"wrong"));assertTrue(values.isEmpty());
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(6L);
        assertThrows(BusinessException.class,()->service.authorize(9L,"login-pass"));assertTrue(values.isEmpty());
    }
    @Test void missingEmailOrUnsetPasswordCannotRecover() {
        user.setEmail(null);assertThrows(BusinessException.class,()->service.authorize(9L,"login-pass"));
        user.setEmail("user@example.com");user.setPayPasswordHash(null);
        assertThrows(BusinessException.class,()->service.authorize(9L,"login-pass"));
    }
    @Test void sendsOnlyAfterCaptchaToServerSelectedEmail() {
        var proof=service.authorize(9L,"login-pass");assertEquals("u***@example.com",proof.maskedEmail());
        assertThrows(BusinessException.class,()->service.send(9L,proof.authorizationToken(),"captcha","wrong"));
        verifyNoInteractions(sender);
        when(redis.getAndDelete("auth:captcha:captcha")).thenReturn("AbCd");
        service.send(9L,proof.authorizationToken(),"captcha","abcd");
        verify(sender).sendPayPasswordReset(eq("user@example.com"),matches("[0-9]{6}"));
        assertTrue(values.values().stream().allMatch(value->value.matches("[a-f0-9]{64}(:0)?")));
    }
    @Test void wrongCodeAndCrossUserCannotIssueGrant() {
        String token=service.authorize(9L,"login-pass").authorizationToken();
        when(redis.executeScript(eq(EmailVerificationService.CONSUME),anyList(),any(),eq("5"))).thenReturn(0L);
        assertThrows(BusinessException.class,()->service.verify(9L,token,"012345"));
        assertThrows(BusinessException.class,()->service.verify(10L,token,"012345"));
        assertTrue(values.keySet().stream().noneMatch(key->key.contains(":grant:")));
    }
    private String grant() {
        String token=service.authorize(9L,"login-pass").authorizationToken();
        when(redis.executeScript(eq(EmailVerificationService.CONSUME),anyList(),any(),eq("5"))).thenReturn(1L);
        when(redis.executeScript(eq(EmailVerificationService.CONSUME_MATCHING_TICKET),anyList(),any())).thenAnswer(call->{
            java.util.List<String> keys=call.getArgument(1);
            return values.remove(keys.getFirst(),call.getArgument(2))?1L:0L;
        });
        return service.verify(9L,token,"012345").resetToken();
    }
    @Test void samePasswordsRetainGrantAndSuccessIsSingleUse() {
        String token=grant();when(passwords.matches("old-pay","pay-hash")).thenReturn(true);
        assertThrows(BusinessException.class,()->service.confirm(9L,token,"old-pay"));
        assertThrows(BusinessException.class,()->service.confirm(9L,token,"login-pass"));
        verify(users,never()).updatePayPassword(anyLong(),anyString());
        when(passwords.encode("new-pay")).thenReturn("new-hash");when(users.updatePayPassword(9L,"new-hash")).thenReturn(1);
        service.confirm(9L,token,"new-pay");
        assertThrows(BusinessException.class,()->service.confirm(9L,token,"new-pay"));
        verify(users,times(1)).updatePayPassword(9L,"new-hash");
        verify(redis).delete("user:pay-password:fail:9");verify(redis).delete("user:pay-password:lock:9");
        assertEquals("login-hash",user.getPasswordHash());assertEquals(2L,user.getCredentialVersion());
        verify(users,never()).resetLoginPassword(anyLong(),anyString(),anyLong(),anyString());
    }
    @Test void changedMailboxOrPasswordRejectsProof() {
        String token=grant();user.setEmail("changed@example.com");
        assertThrows(BusinessException.class,()->service.confirm(9L,token,"new-pay"));
        user.setEmail("user@example.com");user.setPayPasswordHash("changed-hash");
        assertThrows(BusinessException.class,()->service.confirm(9L,token,"new-pay"));
        verify(users,never()).updatePayPassword(anyLong(),anyString());
    }
    @Test void unrelatedOrExpiredGrantDoesNotWrite() {
        assertThrows(BusinessException.class,()->service.confirm(9L,"f".repeat(64),"new-pay"));
        verify(users,never()).updatePayPassword(anyLong(),anyString());
    }
}
