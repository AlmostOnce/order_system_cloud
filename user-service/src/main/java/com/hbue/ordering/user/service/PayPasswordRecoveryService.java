package com.hbue.ordering.user.service;
import com.hbue.ordering.user.vo.PayPasswordRecoveryAuthorizationVO;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
public interface PayPasswordRecoveryService {
    PayPasswordRecoveryAuthorizationVO authorize(Long userId, String loginPassword);
    void send(Long userId, String token, String captchaId, String captchaCode);
    PasswordRecoveryGrantVO verify(Long userId, String token, String code);
    void confirm(Long userId, String token, String password);
}
