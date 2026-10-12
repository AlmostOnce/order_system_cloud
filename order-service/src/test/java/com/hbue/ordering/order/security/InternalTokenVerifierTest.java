package com.hbue.ordering.order.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内部调用共享密钥校验器测试。
 */
class InternalTokenVerifierTest {

    private static final String TOKEN = "internal_order_token_dev_2026";

    @Test
    void shouldAcceptCorrectToken() {
        InternalTokenVerifier verifier = new InternalTokenVerifier(TOKEN);

        assertTrue(verifier.verify(TOKEN));
    }

    @Test
    void shouldRejectWrongToken() {
        InternalTokenVerifier verifier = new InternalTokenVerifier(TOKEN);

        assertFalse(verifier.verify("wrong-token"));
        assertFalse(verifier.verify(TOKEN + "x"));
        assertFalse(verifier.verify(TOKEN.substring(0, TOKEN.length() - 1)));
    }

    @Test
    void shouldRejectNullOrBlankToken() {
        InternalTokenVerifier verifier = new InternalTokenVerifier(TOKEN);

        assertFalse(verifier.verify(null));
        assertFalse(verifier.verify(" "));
    }
}
