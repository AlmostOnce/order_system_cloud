package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 用真实 HTTP 边界验证文件归属，避免 Mapper mock 掩盖缺失字段。 */
class ManagedImageClientTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    private ManagedImageClient client(String data, int status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/files/" + KEY, exchange -> {
            requests.incrementAndGet();
            assertEquals("internal-test", exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            byte[] body = ("{\"code\":\"COMMON_000\",\"data\":" + data + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return new ManagedImageClient("http://127.0.0.1:" + server.getAddress().getPort(), "internal-test");
    }

    private String meta(long uploader, String bizType, Object ref, String contentType) {
        return "{\"fileKey\":\"" + KEY + "\",\"uploaderId\":" + uploader
                + ",\"bizType\":\"" + bizType + "\",\"bizRef\":" + ref
                + ",\"contentType\":\"" + contentType + "\"}";
    }

    @Test
    void shouldAcceptOwnedImageForExactBusinessAndUseRelativeGatewayUrl() throws Exception {
        var client = client(meta(7, "USER_AVATAR", null, "image/png"), 200);
        assertEquals("/api/files/" + KEY + "/content", client.requireImage(KEY, 7L, "USER_AVATAR", null));
        assertEquals(1, requests.get());
    }

    @Test
    void shouldRejectAnotherUploadersImage() throws Exception {
        var client = client(meta(8, "USER_AVATAR", null, "image/png"), 200);
        assertThrows(BusinessException.class, () -> client.requireImage(KEY, 7L, "USER_AVATAR", null));
    }

    @Test
    void shouldRejectWrongTargetEvenWhenUploaderMatches() throws Exception {
        var client = client(meta(7, "PRODUCT_IMAGE", 11, "image/png"), 200);
        assertThrows(BusinessException.class, () -> client.requireImage(KEY, 7L, "PRODUCT_IMAGE", 12L));
    }

    @Test
    void shouldRejectNonImageOrMissingFile() throws Exception {
        var client = client(meta(7, "USER_AVATAR", null, "application/pdf"), 200);
        assertThrows(BusinessException.class, () -> client.requireImage(KEY, 7L, "USER_AVATAR", null));
    }

    @Test
    void shouldRejectDeletedFiles() throws Exception {
        var client = client("null", 404);
        assertThrows(BusinessException.class, () -> client.requireImage(KEY, 7L, "USER_AVATAR", null));
    }

    @Test
    void shouldRejectPathInjectionBeforeMakingRequest() throws Exception {
        var client = client("null", 200);
        assertThrows(BusinessException.class, () -> client.requireImage("../../users", 7L, "USER_AVATAR", null));
        assertEquals(0, requests.get());
    }
}
