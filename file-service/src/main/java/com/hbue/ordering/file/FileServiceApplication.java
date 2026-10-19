package com.hbue.ordering.file;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件服务启动类。
 *
 * <p>负责启动文件服务、扫描文件元数据访问接口并注册到 Nacos。
 * 本服务不依赖其它业务服务，文件存储介质与类型校验均在进程内完成，
 * 因此不启用 Feign 客户端。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@MapperScan("com.hbue.ordering.file.mapper")
public class FileServiceApplication {

    /**
     * 启动文件服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
