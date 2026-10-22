package com.hbue.ordering.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.web.security.JwtUserIdentity;
import com.hbue.ordering.common.web.security.PublicUserIdentityLookup;
import com.hbue.ordering.user.api.vo.UserPublicVO;
import com.hbue.ordering.user.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicUserIdentityTest {
    private static final String UUID = "12345678-1234-4234-8234-123456789abc";

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveOnlyVerifiedUuidWithoutChangingRawToken() {
        ObjectProvider<PublicUserIdentityLookup> provider = mock(ObjectProvider.class);
        PublicUserIdentityLookup lookup = mock(PublicUserIdentityLookup.class);
        when(provider.getIfAvailable()).thenReturn(lookup);
        when(lookup.findInternalId(UUID)).thenReturn(7L);
        var identity = new JwtUserIdentity(provider, "http://unused", "internal-test");
        Jwt original = Jwt.withTokenValue("signed-original").header("alg", "RS256").subject(UUID)
                .claim("username", "用户").claim("roles", List.of("CUSTOMER"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        Jwt principal = identity.toInternalPrincipal(original);
        assertEquals(UUID, original.getSubject());
        assertEquals("7", principal.getSubject());
        assertEquals(UUID, principal.getClaimAsString("user_uuid"));
        assertEquals(original.getTokenValue(), principal.getTokenValue());
        verify(lookup).findInternalId(UUID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectLegacyNumericTokenRatherThanParsingUuidDigitsAsId() {
        ObjectProvider<PublicUserIdentityLookup> provider = mock(ObjectProvider.class);
        var identity = new JwtUserIdentity(provider, "http://unused", "internal-test");
        Jwt old = Jwt.withTokenValue("old").header("alg", "RS256").subject("7").build();
        assertThrows(JwtException.class, () -> identity.toInternalPrincipal(old));
        verifyNoInteractions(provider);
    }

    @Test
    void publicResponsesMustNotSerializeInternalUserId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String profile = mapper.writeValueAsString(new UserProfileVO(UUID, "用户", "CUSTOMER", null, null, null, null));
        String user = mapper.writeValueAsString(new UserPublicVO(UUID, "用户", "CUSTOMER"));
        assertFalse(profile.contains("userId"));
        assertFalse(user.contains("userId"));
        assertTrue(profile.contains(UUID));
    }
}
