package com.hbue.ordering.common.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.regex.Pattern;

/** 原始签名令牌只包含 UUID；验签和黑名单校验后才在服务端解析内部主键。 */
@Component
public class JwtUserIdentity {
    public static final Pattern PUBLIC_UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private final ObjectProvider<PublicUserIdentityLookup> localLookup;
    private final RestClient userClient;
    private final String internalToken;

    public JwtUserIdentity(ObjectProvider<PublicUserIdentityLookup> localLookup,
            @Value("${identity.user-service-base-url:http://localhost:9092}") String userServiceBaseUrl,
            @Value("${internal.scope.token:}") String internalToken) {
        this.localLookup = localLookup;
        this.internalToken = internalToken;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.userClient = RestClient.builder().baseUrl(userServiceBaseUrl).requestFactory(factory).build();
    }

    public Jwt toInternalPrincipal(Jwt verified) {
        String uuid = verified.getSubject();
        if (uuid == null || !PUBLIC_UUID.matcher(uuid).matches()) {
            throw new JwtException("身份令牌版本已更新，请重新登录");
        }
        PublicUserIdentityLookup local = localLookup.getIfAvailable();
        Long userId = local != null ? local.findInternalId(uuid) : remoteLookup(uuid);
        if (userId == null || userId <= 0) throw new JwtException("用户不存在或已停用");
        var claims = new HashMap<>(verified.getClaims());
        // 这是服务端认证主体视图，不重签或修改传给客户端的原始令牌。
        claims.put("sub", String.valueOf(userId));
        claims.put("user_uuid", uuid);
        return new Jwt(verified.getTokenValue(), verified.getIssuedAt(), verified.getExpiresAt(), verified.getHeaders(), claims);
    }

    private Long remoteLookup(String uuid) {
        if (internalToken == null || internalToken.isBlank()) throw new JwtException("身份解析内部凭据未配置");
        try {
            JsonNode response = userClient.get().uri("/internal/users/identities/{uuid}", uuid)
                    .header("X-Internal-Token", internalToken).retrieve().body(JsonNode.class);
            if (response == null || !"COMMON_000".equals(response.path("code").asText())
                    || !response.path("data").isIntegralNumber()) throw new JwtException("无法确认用户身份");
            return response.path("data").asLong();
        } catch (RestClientException exception) {
            throw new JwtException("身份服务暂不可用", exception);
        }
    }
}
