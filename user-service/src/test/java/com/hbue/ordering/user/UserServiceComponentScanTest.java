package com.hbue.ordering.user;

import com.hbue.ordering.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.beans.factory.config.BeanDefinition;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户服务组件扫描范围测试。
 *
 * @author order-system
 * @date 2026-09-25
 */
class UserServiceComponentScanTest {

    /**
     * 验证用户服务启动时会扫描到公共异常处理器。
     */
    @Test
    void shouldFindCommonExceptionHandlerInApplicationScanPackages() {
        // 按用户服务启动类声明的扫描包查找全局异常处理器。
        SpringBootApplication application = UserServiceApplication.class
                .getAnnotation(SpringBootApplication.class);
        String[] basePackages = application.scanBasePackages();

        // 未显式声明扫描包时，Spring Boot 默认使用启动类所在包。
        if (basePackages.length == 0) {
            basePackages = new String[]{
                    ClassUtils.getPackageName(UserServiceApplication.class)
            };
        }

        // 使用 Spring 组件扫描器筛选公共的 REST 异常处理器。
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(
                new AnnotationTypeFilter(RestControllerAdvice.class)
        );

        // 公共异常处理器必须出现在启动类实际声明的扫描范围中。
        boolean exceptionHandlerFound = false;
        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates =
                    scanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                if (GlobalExceptionHandler.class.getName()
                        .equals(candidate.getBeanClassName())) {
                    exceptionHandlerFound = true;
                    break;
                }
            }
            if (exceptionHandlerFound) {
                break;
            }
        }

        assertTrue(
                exceptionHandlerFound,
                "用户服务组件扫描范围必须包含 common-web 的全局异常处理器"
        );
    }
}
