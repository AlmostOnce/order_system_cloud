package com.hbue.ordering.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.order.mapper.SalesRankingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesRankingServiceTest {
    @Test
    void invalidLimitDoesNotTouchDatabaseOrRedis() {
        var redis = mock(StringRedisTemplate.class);
        var mapper = mock(SalesRankingMapper.class);
        var locks = mock(DistributedLockService.class);
        var service = new SalesRankingService(redis, mapper, locks, new ObjectMapper());
        assertThrows(BusinessException.class, () -> service.list(0));
        assertThrows(BusinessException.class, () -> service.list(101));
        verifyNoInteractions(redis, mapper, locks);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void readySortedSetReturnsSalesAndNeverRecountsOrders() {
        var redis = mock(StringRedisTemplate.class);
        var mapper = mock(SalesRankingMapper.class);
        var locks = mock(DistributedLockService.class);
        String member = "00000000000000000008:"
                + "{\"rank\":0,\"productId\":7,\"windowId\":1,\"productName\":\"鸡肉饭\",\"sales\":0}";
        when(redis.execute(any(RedisScript.class), anyList(), eq("50")))
                .thenReturn(List.of("2026-10-22T00:00:00Z", member, "12"));
        var result = new SalesRankingService(redis, mapper, locks, new ObjectMapper()).list(50);
        assertEquals(1, result.items().size());
        assertEquals(1, result.items().getFirst().rank());
        assertEquals(12, result.items().getFirst().sales());
        assertEquals("鸡肉饭", result.items().getFirst().productName());
        verifyNoInteractions(mapper, locks);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void emptyReadyRankingIsARealEmptyResult() {
        var redis = mock(StringRedisTemplate.class);
        var mapper = mock(SalesRankingMapper.class);
        var locks = mock(DistributedLockService.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("50")))
                .thenReturn(List.of("2026-10-22T00:00:00Z"));
        assertTrue(new SalesRankingService(redis, mapper, locks, new ObjectMapper()).list(50).items().isEmpty());
        verifyNoInteractions(mapper, locks);
    }
}
