package com.hbue.ordering.comment.config;

import com.mongodb.WriteConcern;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class CommentMongoConfig {
    @Bean
    public MongoClientSettingsBuilderCustomizer commentMongoTimeouts() {
        return builder -> builder
                .applyToClusterSettings(settings -> settings.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToSocketSettings(settings -> settings.connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(settings -> settings.minSize(0).maxSize(10))
                .writeConcern(WriteConcern.MAJORITY.withJournal(true));
    }
}
