package com.hbue.ordering.auth.service;

import com.hbue.ordering.auth.dto.request.PasswordRecoveryCodeRequest;
import com.hbue.ordering.user.api.dto.*;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;

public interface PasswordRecoveryService {
    void send(PasswordRecoveryCodeRequest body, String source);
    PasswordRecoveryGrantVO verify(PasswordRecoveryVerifyCommand body, String source);
    void confirm(PasswordRecoveryConfirmCommand body, String source);
}
