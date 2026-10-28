package com.hbue.ordering.comment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
@SpringBootApplication(scanBasePackages = {"com.hbue.ordering.comment", "com.hbue.ordering.common"})
@EnableFeignClients(basePackages = "com.hbue.ordering.comment.client")
@EnableScheduling
@EnableMethodSecurity
public class CommentServiceApplication {
    public static void main(String[] args) {
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "comment-service").toString());
        SpringApplication.run(CommentServiceApplication.class, args);
    }
}
