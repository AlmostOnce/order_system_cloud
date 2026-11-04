package com.hbue.ordering.user.dto.request;
import jakarta.validation.constraints.*;
public record PayPasswordRecoveryVerifyRequest(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String authorizationToken, @NotBlank @Pattern(regexp="[0-9]{6}") String code) {
    @Override public String toString() { return "PayPasswordRecoveryVerifyRequest[REDACTED]"; }
}
