package com.hbue.ordering.common.web.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

/** 通过文件服务验证图片归属；不跨库读取文件表，不访问客户端提供的地址。 */
@Component
public class ManagedImageClient {
    private final RestClient client;
    private final String internalToken;

    public ManagedImageClient(@Value("${file.service-base-url:http://localhost:9102}") String fileServiceBaseUrl,
            @Value("${internal.scope.token:}") String internalToken) {
        this.internalToken = internalToken;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder().baseUrl(fileServiceBaseUrl).requestFactory(factory).build();
    }

    public String requireImage(String fileKey, Long operatorId, String bizType, Long bizRef) {
        if (fileKey == null || !fileKey.matches("[a-fA-F0-9]{32}")) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "图片文件键格式不正确");
        }
        if (internalToken == null || internalToken.isBlank()) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "文件内部调用凭据未配置");
        JsonNode response;
        try {
            response = client.get().uri("/internal/files/{key}", fileKey)
                    .header("X-Internal-Token", internalToken).retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "图片不存在或已删除，请重新上传");
        } catch (RestClientException exception) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "文件服务暂不可用，请稍后重试");
        }
        if (response == null || !"COMMON_000".equals(response.path("code").asText())) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认图片信息，请稍后重试");
        }
        JsonNode data = response.path("data");
        if (!fileKey.equals(data.path("fileKey").asText())
                || !data.path("uploaderId").isIntegralNumber()
                || data.path("uploaderId").asLong() != operatorId
                || !bizType.equals(data.path("bizType").asText())
                || (bizRef == null ? (!data.path("bizRef").isNull() && !data.path("bizRef").isMissingNode())
                    : (!data.path("bizRef").isIntegralNumber() || data.path("bizRef").asLong() != bizRef))) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "图片不属于当前上传操作，请重新上传");
        }
        String type = data.path("contentType").asText();
        if (!java.util.Set.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(type)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "仅支持 JPG、PNG、GIF、WebP 图片");
        }
        return "/api/files/" + fileKey + "/content";
    }
}
