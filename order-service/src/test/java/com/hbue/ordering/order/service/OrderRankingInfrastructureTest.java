package com.hbue.ordering.order.service;

import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.common.redis.RedissonInfrastructureConfig;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OrderRankingInfrastructureTest {
    @Test
    void orderClasspathCanCreateRequiredDistributedLockBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedissonInfrastructureConfig.class)
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(DistributedLockService.class));
                });
    }
}
