package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.EmailCodeSender;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordRecoveryServiceTest {
    private final UserMapper users=mock(UserMapper.class);
    private final RedisOperationsService redis=mock(RedisOperationsService.class);
    private final EmailCodeSender sender=mock(EmailCodeSender.class);
    private final PasswordEncoder encoder=mock(PasswordEncoder.class);
    private final PasswordRecoveryServiceImpl recovery=new PasswordRecoveryServiceImpl(users,redis,sender,encoder,"s".repeat(32));
    @Test void unknownEmailReturnsWithoutCreatingOrSending() {
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(1L);
        recovery.send(" USER@EXAMPLE.COM ");
        verify(users).selectRecoveryByEmail("user@example.com");verifyNoInteractions(sender);
    }
    @Test void realEmailStoresOnlyDigestAndSendsSixDigits() {
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(1L);
        var user=new UserDO();user.setUserId(9L);when(users.selectRecoveryByEmail("user@example.com")).thenReturn(user);
        recovery.send("user@example.com");
        verify(sender).sendPasswordReset(eq("user@example.com"),matches("[0-9]{6}"));
        verify(redis).setValue(startsWith("password-recovery:code:"),matches("[a-f0-9]{64}:0"),eq(Duration.ofMinutes(10)));
    }
    @Test void wrongCodeCannotProduceResetGrant() {
        when(redis.executeScript(eq(EmailVerificationService.CONSUME),anyList(),any(),eq("5"))).thenReturn(0L);
        assertThrows(BusinessException.class,()->recovery.verify("user@example.com","012345"));
        verifyNoInteractions(users);verify(redis,never()).setValue(anyString(),anyString(),any(Duration.class));
    }
    @Test void successfulVerificationCreatesShortLivedAccountBoundGrant() {
        when(redis.executeScript(eq(EmailVerificationService.CONSUME),anyList(),any(),eq("5"))).thenReturn(1L);
        var user=new UserDO();user.setUserId(9L);user.setCredentialVersion(2L);
        when(users.selectRecoveryByEmail("user@example.com")).thenReturn(user);
        var grant=recovery.verify("user@example.com","012345");
        assertTrue(grant.resetToken().matches("[a-f0-9]{64}"));assertEquals(300,grant.expiresIn());
        verify(redis).setValue(startsWith("password-recovery:grant:"),eq("9\n2\nuser@example.com"),eq(Duration.ofMinutes(5)));
    }
    private void currentPassword(long version) {
        var user=new UserDO();user.setUserId(9L);user.setEmail("user@example.com");
        user.setCredentialVersion(version);user.setPasswordHash("old-hash");
        when(users.selectPasswordRecoveryForUpdate(9L)).thenReturn(user);
    }
    @Test void samePasswordRejectsWithoutConsumingProofOrChangingSessions() {
        when(redis.getValue(anyString())).thenReturn("9\n2\nuser@example.com");currentPassword(2L);
        when(encoder.matches("old-password","old-hash")).thenReturn(true);
        var error=assertThrows(BusinessException.class,()->recovery.confirm("a".repeat(64),"old-password"));
        assertEquals("新密码不能与旧密码相同，请设置其他密码",error.getMessage());
        verify(redis,never()).executeScript(eq(EmailVerificationService.CONSUME_MATCHING_TICKET),anyList(),any());
        verify(users,never()).resetLoginPassword(anyLong(),anyString(),anyLong(),anyString());
        verify(encoder,never()).encode(anyString());
        when(redis.executeScript(eq(EmailVerificationService.CONSUME_MATCHING_TICKET),anyList(),any())).thenReturn(1L);
        when(encoder.encode("different-password")).thenReturn("new-hash");
        when(users.resetLoginPassword(9L,"user@example.com",2L,"new-hash")).thenReturn(1);
        recovery.confirm("a".repeat(64),"different-password");
        verify(users).resetLoginPassword(9L,"user@example.com",2L,"new-hash");
    }
    @Test void resetConsumesProofAndUsesAtomicVersionCheckedWrite() {
        currentPassword(2L);
        when(redis.executeScript(eq(EmailVerificationService.CONSUME_MATCHING_TICKET),anyList(),any())).thenReturn(1L);
        when(redis.getValue(startsWith("password-recovery:grant:"))).thenReturn("9\n2\nuser@example.com",null);
        when(encoder.encode("new-password")).thenReturn("bcrypt-hash");
        when(users.resetLoginPassword(9L,"user@example.com",2L,"bcrypt-hash")).thenReturn(1);
        recovery.confirm("a".repeat(64),"new-password");
        assertThrows(BusinessException.class,()->recovery.confirm("a".repeat(64),"new-password"));
        verify(users,times(1)).resetLoginPassword(9L,"user@example.com",2L,"bcrypt-hash");
    }
    @Test void changedEmailOrCredentialVersionRejectsOldGrant() {
        currentPassword(3L);
        when(redis.getValue(anyString())).thenReturn("9\n2\nuser@example.com");
        when(encoder.encode("new-password")).thenReturn("bcrypt-hash");
        assertThrows(BusinessException.class,()->recovery.confirm("a".repeat(64),"new-password"));
    }
    @Test void weakPasswordDoesNotConsumeProof() {
        assertThrows(BusinessException.class,()->recovery.confirm("a".repeat(64),"123"));verifyNoInteractions(redis,users);
    }
}
