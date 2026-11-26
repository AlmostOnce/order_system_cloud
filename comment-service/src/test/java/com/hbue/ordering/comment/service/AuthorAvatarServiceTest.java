package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.client.CommentUserClient;
import com.hbue.ordering.comment.service.impl.AuthorAvatarServiceImpl;
import com.hbue.ordering.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuthorAvatarServiceTest {
    @Test
    void deduplicatesAndFetchesOnceWhileIgnoringUnrequestedUsers() {
        var users = mock(CommentUserClient.class);
        when(users.avatars(List.of(9L, 10L), "internal")).thenReturn(
                ApiResponse.success(Map.of(9L, "/api/files/avatar/content", 11L, "unexpected")));
        var result = new AuthorAvatarServiceImpl(users, "internal").resolve(List.of(9L, 9L, 10L));
        assertEquals(Map.of(9L, "/api/files/avatar/content"), result);
        verify(users, times(1)).avatars(List.of(9L, 10L), "internal");
    }

    @Test
    void unavailableUserServiceFallsBackToDefaultAvatar() {
        var users = mock(CommentUserClient.class);
        when(users.avatars(List.of(9L), "internal")).thenThrow(new IllegalStateException());
        assertTrue(new AuthorAvatarServiceImpl(users, "internal").resolve(List.of(9L)).isEmpty());
    }

    @Test
    void emptyPageDoesNotCallUserService() {
        var users = mock(CommentUserClient.class);
        assertTrue(new AuthorAvatarServiceImpl(users, "internal").resolve(List.of()).isEmpty());
        verifyNoInteractions(users);
    }

    /**
     * 作者数超过批量上限时应分片回源并合并结果，而不是抛异常导致整页 500。
     */
    @Test
    void splitsLargePagesIntoSeveralBatchesInsteadOfFailing() {
        var users = mock(CommentUserClient.class);
        var ids = java.util.stream.LongStream.rangeClosed(1, 120).boxed().toList();
        when(users.avatars(ids.subList(0, 50), "internal"))
                .thenReturn(ApiResponse.success(Map.of(1L, "/a1")));
        when(users.avatars(ids.subList(50, 100), "internal"))
                .thenReturn(ApiResponse.success(Map.of(51L, "/a51")));
        when(users.avatars(ids.subList(100, 120), "internal"))
                .thenReturn(ApiResponse.success(Map.of(101L, "/a101")));

        var result = new AuthorAvatarServiceImpl(users, "internal").resolve(ids);

        assertEquals(Map.of(1L, "/a1", 51L, "/a51", 101L, "/a101"), result);
        verify(users, times(3)).avatars(anyList(), eq("internal"));
    }
}
