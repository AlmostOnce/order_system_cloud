package com.hbue.ordering.comment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hbue.ordering.comment.mapper.RatingBatchMapper;
import com.hbue.ordering.comment.service.impl.RatingBatchServiceImpl;
import com.hbue.ordering.common.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RatingBatchServiceTest {
    @Test
    void selectsTheCorrectRatingSource() {
        var repository = mock(RatingBatchMapper.class);
        when(repository.windows(List.of(3L))).thenReturn(List.of(new RatingBatchMapper.Row(3L, 2, 4.5)));
        var service = new RatingBatchServiceImpl(repository);
        var result = service.get("windows", List.of(3L));
        assertEquals(4.5, result.getFirst().averageScore());
        verify(repository).windows(List.of(3L));
        verify(repository, never()).products(anyList());
    }
    @Test
    void rejectsInvalidOrOversizedRequests() {
        var repository = mock(RatingBatchMapper.class);
        var service = new RatingBatchServiceImpl(repository);
        assertThrows(BusinessException.class, () -> service.get("unknown", List.of(1L)));
        assertThrows(BusinessException.class, () -> service.get("products", List.of(0L)));
        assertThrows(BusinessException.class, () -> service.get("windows", java.util.Collections.nCopies(101, 1L)));
        verifyNoInteractions(repository);
    }
}
