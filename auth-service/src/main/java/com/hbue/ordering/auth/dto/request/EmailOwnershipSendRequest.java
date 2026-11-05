package com.hbue.ordering.auth.dto.request;
import jakarta.validation.constraints.*;
public record EmailOwnershipSendRequest(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{1,64}") String captchaId,
        @NotBlank @Size(max=8) String captchaCode,
        @Pattern(regexp="[a-f0-9]{64}") String authorizationToken) {
    @Override public String toString(){return "EmailOwnershipSendRequest[REDACTED]";}
}
