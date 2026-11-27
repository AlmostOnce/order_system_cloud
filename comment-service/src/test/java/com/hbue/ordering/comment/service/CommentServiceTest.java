package com.hbue.ordering.comment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.dto.UpdateCommentRequest;
import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.model.CommentContentDO;
import com.hbue.ordering.comment.model.CommentRowDO;
import com.hbue.ordering.comment.repository.CommentContentRepository;
import com.hbue.ordering.comment.service.impl.CommentServiceImpl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentServiceTest {
    private final CommentMapper rows = mock(CommentMapper.class);
    private final CommentMetadataWriter writer = mock(CommentMetadataWriter.class);
    private final PurchaseVerifier purchases = mock(PurchaseVerifier.class);
    private final CommentContentRepository mongo = mock(CommentContentRepository.class);
    private final ManagedImageClient images = mock(ManagedImageClient.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final CommentService service = new CommentServiceImpl(rows, writer, purchases, images, mongo, json,
            mock(AuthorAvatarService.class), mock(ReviewAuthorityService.class), mock(ReviewDiscussionService.class));
    private final String key = "a1111111-1111-4111-8111-111111111111";
    private final CreateCommentRequest input = new CreateCommentRequest(7L, 5L, 5, "味道很好", List.of(), List.of("好吃"), false);

    CommentServiceTest() {
        when(purchases.requireCompletedPurchase(7L, 5L, "Bearer signed")).thenReturn(2L);
        when(mongo.byId(anyString())).thenAnswer(a ->
                new CommentContentDO(a.getArgument(0), "味道很好", List.of(), List.of("好吃"), Instant.now(), true));
    }

    @Test
    void writesContentBeforeMetadataAndReplayDoesNotWriteTwice() throws Exception {
        AtomicReference<CommentRowDO> saved = new AtomicReference<>();
        doAnswer(a -> {
            saved.set(a.getArgument(0));
            return null;
        }).when(writer).commit(any());
        var first = service.create(9L, "同学", "Bearer signed", key, input);
        var ordered = inOrder(mongo, writer);
        ordered.verify(mongo).insert(any(CommentContentDO.class));
        ordered.verify(writer).commit(any(CommentRowDO.class));
        when(rows.byRequest(9L, key)).thenAnswer(a -> saved.get());
        var replay = service.create(9L, "同学", "Bearer signed", key, input);
        assertEquals(first.commentId(), replay.commentId());
        verify(writer, times(1)).commit(any());
        assertThrows(BusinessException.class, () -> service.create(9L, "同学", "Bearer signed", key,
                new CreateCommentRequest(7L, 5L, 1, "改了内容", List.of(), List.of(), false)));
        String response = json.writeValueAsString(first);
        for (String privateField : List.of("userId", "orderId", "requestHash", "requestKey"))
            assertFalse(response.contains(privateField));
    }

    @Test
    void sqlFailureLeavesUnpublishedContentForReconciliation() {
        doThrow(new IllegalStateException("SQL unavailable")).when(writer).commit(any());
        assertThrows(IllegalStateException.class, () -> service.create(9L, "同学", "Bearer signed", key, input));
        verify(mongo).insert(any(CommentContentDO.class));
        verify(mongo, never()).removeIfPending(anyString());
    }

    @Test
    void duplicatePurchaseRejectsBeforeWritingMongo() {
        when(rows.byPurchase(7L, 5L)).thenReturn(new CommentRowDO(key, 9L, "同学", 7L, 5L, 2L, 5, key, "hash", null, false, false));
        assertThrows(BusinessException.class, () -> service.create(9L, "同学", "Bearer signed", key, input));
        verify(mongo, never()).insert(any(CommentContentDO.class));
    }

    @Test
    void persistsImageKeysWhileReturningDisplayUrls() {
        String fileKey = "0123456789abcdef0123456789abcdef";
        when(images.requireImageKey(fileKey, 9L, "COMMENT_IMAGE", null)).thenReturn(fileKey);
        when(mongo.byId(anyString())).thenAnswer(a ->
                new CommentContentDO(a.getArgument(0), "正文", List.of(fileKey), List.of(), Instant.now(), true));
        var response = service.create(9L, "同学", "Bearer signed", key,
                new CreateCommentRequest(7L, 5L, 5, "正文", List.of(fileKey.toUpperCase()), List.of(), false));
        var saved = org.mockito.ArgumentCaptor.forClass(CommentContentDO.class);
        verify(mongo).insert(saved.capture());
        assertEquals(List.of(fileKey), saved.getValue().imageFileKeys());
        // 与Mongo正文持久化一致的标记必须交给元数据事务，保证发布后即可有图分页。
        var metadata = org.mockito.ArgumentCaptor.forClass(CommentRowDO.class);
        verify(writer).commit(metadata.capture());
        assertEquals(Boolean.TRUE, metadata.getValue().hasImages());
        assertEquals(List.of("/api/files/" + fileKey + "/content"), response.images());
    }

    @Test
    void ratingOnlyNormalizesOmittedOptionalFields() {
        when(mongo.byId(anyString())).thenAnswer(a ->
                new CommentContentDO(a.getArgument(0), "", List.of(), List.of(), Instant.now(), true));
        var response = service.create(9L, "同学", "Bearer signed", key,
                new CreateCommentRequest(7L, 5L, 4, null, null, null, null));
        assertEquals(4, response.score());
        assertEquals("", response.content());
        var saved = org.mockito.ArgumentCaptor.forClass(CommentContentDO.class);
        verify(mongo).insert(saved.capture());
        assertEquals("", saved.getValue().body());
        assertTrue(saved.getValue().imageFileKeys().isEmpty());
        var metadata = org.mockito.ArgumentCaptor.forClass(CommentRowDO.class);
        verify(writer).commit(metadata.capture());
        assertEquals(Boolean.FALSE, metadata.getValue().hasImages());
        assertTrue(saved.getValue().tags().isEmpty());
    }

    /**
     * 修改评价时先改正文再改元数据，两处使用同一份评分与带图标记。
     */
    @Test
    void reviseWritesContentBeforeMetadata() {
        String id = key;
        when(rows.byId(id)).thenReturn(row(id, 5));
        when(mongo.byId(id)).thenAnswer(a ->
                new CommentContentDO(id, "旧正文", List.of(), List.of(), Instant.now(), true));
        when(mongo.updateContent(anyString(), anyString(), any(), any())).thenReturn(1L);

        service.revise(9L, List.of("CUSTOMER"), id, new UpdateCommentRequest(4, "新正文", null, null));

        var ordered = inOrder(mongo, writer);
        ordered.verify(mongo).updateContent(eq(id), eq("新正文"), any(), any());
        ordered.verify(writer).revise(id, 5L, 5, 4, false);
    }

    /**
     * 元数据事务失败时正文必须回滚到修改前，避免"提示失败但内容已变"。
     */
    @Test
    void reviseRollsBackContentWhenMetadataFails() {
        String id = key;
        when(rows.byId(id)).thenReturn(row(id, 5));
        when(mongo.byId(id)).thenAnswer(a ->
                new CommentContentDO(id, "旧正文", List.of(), List.of(), Instant.now(), true));
        when(mongo.updateContent(anyString(), anyString(), any(), any())).thenReturn(1L);
        doThrow(new IllegalStateException("db down"))
                .when(writer).revise(anyString(), anyLong(), anyInt(), anyInt(), anyBoolean());

        assertThrows(IllegalStateException.class,
                () -> service.revise(9L, List.of("CUSTOMER"), id, new UpdateCommentRequest(5, "新正文", null, null)));
        verify(mongo).updateContent(eq(id), eq("旧正文"), any(), any());
    }

    /**
     * 删除只改元数据状态，正文保留在内容库中便于追责。
     */
    @Test
    void removeDelegatesToMetadataWriterAndKeepsContent() {
        String id = key;
        var row = row(id, 5);
        when(rows.byId(id)).thenReturn(row);

        service.remove(9L, List.of("CUSTOMER"), id);

        verify(writer).remove(row);
        verify(mongo, never()).removeIfPending(anyString());
    }

    /**
     * 切换筛选后沿用旧游标必须提示重新加载，而不是静默返回空页。
     */
    @Test
    void pageRejectsCursorThatDoesNotMatchCurrentFilter() {
        String cursor = "b2222222-2222-4222-8222-222222222222";
        // 按当前筛选回读游标为 0，说明游标属于旧筛选或已被删除。
        when(rows.countScopedCursor(cursor, 9L, null, 5, null)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.page(9L, null, cursor, 20, 5, null));

        assertEquals(CommonErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(rows, never()).pageByUser(anyLong(), anyString(), anyInt(), any(), any());
    }

    /**
     * 游标仍在当前范围内时正常分页，不额外报错。
     */
    @Test
    void pageAcceptsCursorWithinCurrentFilter() {
        String cursor = "b2222222-2222-4222-8222-222222222222";
        when(rows.countScopedCursor(cursor, 9L, null, 5, null)).thenReturn(1);
        when(rows.pageByUser(9L, cursor, 21, 5, null)).thenReturn(List.of());

        var result = service.page(9L, null, cursor, 20, 5, null);

        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
        verify(rows).pageByUser(9L, cursor, 21, 5, null);
    }

    /**
     * 构造一条已发布评价，供修改与删除用例复用。
     *
     * @param id    评价标识
     * @param score 评分
     * @return 评价元数据
     */
    private CommentRowDO row(String id, int score) {
        return new CommentRowDO(id, 9L, "同学", 7L, 5L, 2L, score, id, "hash", LocalDateTime.now(), false, false);
    }

}
