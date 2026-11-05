package com.hbue.ordering.user.service;
import com.hbue.ordering.user.api.vo.*;
public interface EmailOwnershipService {
    void sendRegistration(String email);
    EmailRegistrationGrantVO verifyRegistration(String email,String code);
    void consumeRegistration(String email,String token);
    EmailEnrollmentChallengeVO challenge(Long userId,long credentialVersion);
    void sendEnrollment(String token,String email);
    void confirmEnrollment(String token,String email,String code);
}
