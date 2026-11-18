package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayPasswordRecoveryAuthorizeRequest(@NotBlank @Size(max = 64) String loginPassword) {
    @Override
    public String toString() {
        return "PayPasswordRecoveryAuthorizeRequest[REDACTED]";
    }
}
