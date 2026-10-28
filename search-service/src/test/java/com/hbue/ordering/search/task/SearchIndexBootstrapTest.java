package com.hbue.ordering.search.task;

import static org.mockito.Mockito.*;

import com.hbue.ordering.search.service.ProductSearchService;
import org.junit.jupiter.api.Test;

class SearchIndexBootstrapTest {
    @Test
    void waitsForApplicationReadinessAndOnlySynchronizesOnce() {
        var service = mock(ProductSearchService.class);
        var bootstrap = new SearchIndexBootstrap(service);
        bootstrap.synchronize();
        verifyNoInteractions(service);
        bootstrap.markApplicationReady();
        bootstrap.synchronize();
        bootstrap.synchronize();
        verify(service, times(1)).synchronizeExistingWindows();
    }

    @Test
    void retriesAfterSynchronizationFailure() {
        var service = mock(ProductSearchService.class);
        when(service.synchronizeExistingWindows()).thenThrow(new IllegalStateException("暂不可用")).thenReturn(1);
        var bootstrap = new SearchIndexBootstrap(service);
        bootstrap.markApplicationReady();
        bootstrap.synchronize();
        bootstrap.synchronize();
        verify(service, times(2)).synchronizeExistingWindows();
    }
}
