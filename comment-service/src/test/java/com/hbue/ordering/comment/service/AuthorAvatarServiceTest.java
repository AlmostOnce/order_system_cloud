package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.client.CommentUserClient;
import com.hbue.ordering.comment.service.impl.AuthorAvatarServiceImpl;
import com.hbue.ordering.common.response.ApiResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorAvatarServiceTest {
    @Test void deduplicatesAndFetchesOnceWhileIgnoringUnrequestedUsers() {
        var users = mock(CommentUserClient.class);
        when(users.avatars(List.of(9L, 10L), "internal")).thenReturn(
                ApiResponse.success(Map.of(9L, "/api/files/avatar/content", 11L, "unexpected")));
        var result = new AuthorAvatarServiceImpl(users, "internal").resolve(List.of(9L, 9L, 10L));
        assertEquals(Map.of(9L, "/api/files/avatar/content"), result);
        verify(users, times(1)).avatars(List.of(9L, 10L), "internal");
    }
    @Test void unavailableUserServiceFallsBackToDefaultAvatar() {
        var users = mock(CommentUserClient.class);
        when(users.avatars(List.of(9L), "internal")).thenThrow(new IllegalStateException());
        assertTrue(new AuthorAvatarServiceImpl(users, "internal").resolve(List.of(9L)).isEmpty());
    }
    @Test void emptyPageDoesNotCallUserService() {
        var users = mock(CommentUserClient.class);
        assertTrue(new AuthorAvatarServiceImpl(users, "internal").resolve(List.of()).isEmpty());
        verifyNoInteractions(users);
    }
}
