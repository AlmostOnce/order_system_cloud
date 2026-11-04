package com.hbue.ordering.user.dto.request;
import jakarta.validation.constraints.*;
public record PayPasswordRecoveryCodeRequest(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String authorizationToken, @NotBlank @Pattern(regexp="[A-Za-z0-9-]{1,64}") String captchaId, @NotBlank @Size(max=8) String captchaCode) {
    @Override public String toString() { return "PayPasswordRecoveryCodeRequest[REDACTED]"; }
}
