package com.hbue.ordering.user.service.impl;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.EmailCodeSender;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class EmailOwnershipServiceTest {
    private final UserMapper users=mock(UserMapper.class);
    private final RedisOperationsService redis=mock(RedisOperationsService.class);
    private final EmailCodeSender sender=mock(EmailCodeSender.class);
    private final Map<String,String> values=new HashMap<>();
    private final AtomicReference<String> code=new AtomicReference<>();
    private final EmailOwnershipServiceImpl service=new EmailOwnershipServiceImpl(users,redis,sender,"s".repeat(32));
    private UserDO user;
    @BeforeEach void setup() {
        user=new UserDO();user.setUserId(9L);user.setStatus(1);user.setRoleCode("CUSTOMER");user.setCredentialVersion(0L);user.setPasswordHash("login-hash");
        when(users.selectEmailEnrollment(9L)).thenReturn(user);when(users.selectEmailEnrollmentForUpdate(9L)).thenReturn(user);
        when(redis.increment(anyString(),any(Duration.class))).thenReturn(1L);
        when(redis.getValue(anyString())).thenAnswer(call->values.get(call.getArgument(0)));
        doAnswer(call->{values.put(call.getArgument(0),call.getArgument(1));return null;}).when(redis).setValue(anyString(),anyString(),any(Duration.class));
        doAnswer(call->{code.set(call.getArgument(1));return null;}).when(sender).sendOwnershipVerification(anyString(),anyString());
        when(redis.executeScript(eq(EmailVerificationService.CONSUME),anyList(),any(),eq("5"))).thenAnswer(call->{
            List<String> keys=call.getArgument(1);String stored=values.get(keys.getFirst());String expected=call.getArgument(2);
            if(stored!=null && stored.equals(expected+":0")){values.remove(keys.getFirst());return 1L;}return 0L;
        });
        when(redis.executeScript(eq(EmailVerificationService.CONSUME_MATCHING_TICKET),anyList(),any())).thenAnswer(call->{
            List<String> keys=call.getArgument(1);return values.remove(keys.getFirst(),call.getArgument(2))?1L:0L;
        });
    }
    @Test void registrationProofIsBoundToNormalizedEmailAndOneTimeOnly() {
        service.sendRegistration(" USER@Example.com ");verify(sender).sendOwnershipVerification(eq("user@example.com"),matches("[0-9]{6}"));
        var grant=service.verifyRegistration("user@example.com",code.get());
        assertThrows(BusinessException.class,()->service.consumeRegistration("other@example.com",grant.verificationToken()));
        service.consumeRegistration("USER@example.com",grant.verificationToken());
        assertThrows(BusinessException.class,()->service.consumeRegistration("user@example.com",grant.verificationToken()));
        verify(users,never()).completeEmailEnrollment(anyLong(),anyString());
    }
    @Test void enrollmentRequiresCurrentCredentialVersionAndCannotConsumeRegistrationProof() {
        assertThrows(BusinessException.class,()->service.challenge(9L,1L));
        service.sendRegistration("user@example.com");var registration=service.verifyRegistration("user@example.com",code.get());
        assertThrows(BusinessException.class,()->service.sendEnrollment(registration.verificationToken(),"user@example.com"));
        verify(users,never()).completeEmailEnrollment(anyLong(),anyString());
    }
    @Test void validPasswordChallengeCanBindPreviouslyMissingEmailOnlyOnce() {
        var challenge=service.challenge(9L,0L);service.sendEnrollment(challenge.authorizationToken(),"new@example.com");
        String received=code.get();assertThrows(BusinessException.class,()->service.confirmEnrollment(challenge.authorizationToken(),"other@example.com",received));
        when(users.completeEmailEnrollment(9L,"new@example.com")).thenAnswer(call->{user.setEmailVerifiedAt(LocalDateTime.now());user.setCredentialVersion(1L);return 1;});
        service.confirmEnrollment(challenge.authorizationToken(),"new@example.com",received);
        assertThrows(BusinessException.class,()->service.confirmEnrollment(challenge.authorizationToken(),"new@example.com",received));
        verify(users,times(1)).completeEmailEnrollment(9L,"new@example.com");
    }
    @Test void changedPasswordInvalidatesPendingEnrollment() {
        var challenge=service.challenge(9L,0L);user.setPasswordHash("changed-hash");
        assertThrows(BusinessException.class,()->service.sendEnrollment(challenge.authorizationToken(),"new@example.com"));verifyNoInteractions(sender);
    }
    @Test void alreadyVerifiedAccountDoesNotGetEnrollmentAuthorization() {
        user.setEmailVerifiedAt(LocalDateTime.now());assertThrows(BusinessException.class,()->service.challenge(9L,0L));
        verify(redis,never()).setValue(anyString(),anyString(),any(Duration.class));
    }
}
