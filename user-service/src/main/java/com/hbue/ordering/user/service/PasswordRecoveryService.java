package com.hbue.ordering.user.service;

import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;

public interface PasswordRecoveryService {
    void send(String email);
    PasswordRecoveryGrantVO verify(String email, String code);
    void confirm(String resetToken, String newPassword);
}
