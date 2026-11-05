package com.hbue.ordering.user.api.dto;
public record EmailEnrollmentChallengeCommand(Long userId, long credentialVersion) {
    @Override public String toString() {return "EmailEnrollmentChallengeCommand[REDACTED]";}
}
