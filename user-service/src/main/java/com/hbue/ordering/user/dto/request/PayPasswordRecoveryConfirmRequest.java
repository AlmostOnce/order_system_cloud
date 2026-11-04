package com.hbue.ordering.user.dto.request;
import jakarta.validation.constraints.*;
public record PayPasswordRecoveryConfirmRequest(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String resetToken, @NotBlank @Size(min=6,max=20) String newPayPassword) {
    @Override public String toString() { return "PayPasswordRecoveryConfirmRequest[REDACTED]"; }
}
