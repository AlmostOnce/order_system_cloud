package com.hbue.ordering.user.api.vo;
public record EmailEnrollmentChallengeVO(String authorizationToken,String roleCode,long expiresIn) {
    @Override public String toString() {return "EmailEnrollmentChallengeVO[REDACTED]";}
}
