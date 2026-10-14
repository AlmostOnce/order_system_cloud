package com.hbue.ordering.common.web.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档（OpenAPI / Swagger UI）共享配置。
 *
 * <p>各业务服务引入 springdoc 后，无需各自重复编写文档配置：
 * 本类随组件扫描生效，为每个服务提供统一的文档信息、JWT Bearer 安全方案
 * 与网关访问前缀。</p>
 *
 * <p>未引入 springdoc 的模块（如 common-response）不会加载本配置；
 * 可通过 {@code springdoc.api-docs.enabled=false} 关闭本配置及接口文档能力。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Configuration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(
        name = "springdoc.api-docs.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OpenApiDocumentationConfig {

    /** JWT Bearer 安全方案名称，需与各接口文档中的引用保持一致。 */
    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    /** 文档标题，缺省回落到服务名。 */
    private final String serviceTitle;

    /** 文档描述，可为空。 */
    private final String serviceDescription;

    /** 文档版本号，缺省为 1.0.0。 */
    private final String serviceVersion;

    /** 网关基础地址，用于修正经网关聚合后的 "Try it out" 目标地址。 */
    private final String gatewayBaseUrl;

    /** 本服务在网关上的路径前缀，如 /api/users。 */
    private final String gatewayPath;

    /**
     * 创建接口文档共享配置。
     *
     * @param serviceTitle 文档标题
     * @param serviceDescription 文档描述
     * @param serviceVersion 文档版本号
     * @param gatewayBaseUrl 网关基础地址
     * @param gatewayPath 本服务的网关路径前缀
     */
    public OpenApiDocumentationConfig(
            @Value("${springdoc.service-title:${spring.application.name:接口服务}}")
            String serviceTitle,
            @Value("${springdoc.service-description:}")
            String serviceDescription,
            @Value("${springdoc.service-version:1.0.0}")
            String serviceVersion,
            @Value("${springdoc.gateway-base-url:}")
            String gatewayBaseUrl,
            @Value("${springdoc.gateway-path:}")
            String gatewayPath
    ) {
        this.serviceTitle = serviceTitle;
        this.serviceDescription = serviceDescription;
        this.serviceVersion = serviceVersion;
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.gatewayPath = gatewayPath;
    }

    /**
     * 构建服务接口文档定义。
     *
     * @return OpenAPI 文档定义
     */
    @Bean
    public OpenAPI openApi() {
        OpenAPI openApi = new OpenAPI()
                .info(buildInfo())
                .components(buildComponents())
                // 全局声明 Bearer 安全要求，使 UI 出现 Authorize 按钮。
                .addSecurityItem(
                        new SecurityRequirement().addList(BEARER_SCHEME_NAME)
                );

        // 显式指定网关地址，规避经网关重写路径后 server 生成错误的问题。
        Server server = buildServer();
        if (server != null) {
            openApi.addServersItem(server);
        }

        return openApi;
    }

    /**
     * 构建文档基本信息。
     *
     * @return 文档信息
     */
    private Info buildInfo() {
        Info info = new Info()
                .title(serviceTitle)
                .version(serviceVersion);

        // 描述留空时不写入，避免 UI 出现空白段落。
        if (serviceDescription != null && !serviceDescription.isBlank()) {
            info.description(serviceDescription);
        }

        return info;
    }

    /**
     * 构建安全方案集合，注册 JWT Bearer 认证。
     *
     * @return 安全方案集合
     */
    private Components buildComponents() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .name(BEARER_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("登录后获取的 Access Token，填入时无需再加 Bearer 前缀。");

        return new Components()
                .addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme);
    }

    /**
     * 构建文档 server 地址。
     *
     * @return 网关地址，未配置网关基础地址时返回 {@code null} 交由 springdoc 自动推断
     */
    private Server buildServer() {
        // 未配置网关地址时，交由 springdoc 按当前请求地址推断。
        if (gatewayBaseUrl == null || gatewayBaseUrl.isBlank()) {
            return null;
        }

        String url = gatewayPath == null || gatewayPath.isBlank()
                ? gatewayBaseUrl
                : gatewayBaseUrl + gatewayPath;

        return new Server().url(url).description("经网关访问");
    }
}
