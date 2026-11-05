package com.hbue.ordering.auth.service;
import com.hbue.ordering.auth.dto.request.EmailOwnershipSendRequest;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.*;
public interface EmailOwnershipService {
    void sendRegistration(EmailOwnershipSendRequest body,String source);
    EmailRegistrationGrantVO verifyRegistration(RegistrationEmailVerifyCommand body,String source);
    EmailEnrollmentChallengeVO challenge(UserBasicVO verifiedPasswordUser);
    void sendEnrollment(EmailOwnershipSendRequest body,String source);
    void confirmEnrollment(EmailEnrollmentConfirmCommand body,String source);
}
