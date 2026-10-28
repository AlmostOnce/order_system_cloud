package com.hbue.ordering.comment.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.model.*;
import com.hbue.ordering.comment.repository.CommentRepository;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CommentServiceTest {
    private final CommentRepository rows=mock(CommentRepository.class);
    private final CommentMetadataWriter writer=mock(CommentMetadataWriter.class);
    private final PurchaseVerifier purchases=mock(PurchaseVerifier.class);
    private final MongoTemplate mongo=mock(MongoTemplate.class);
    private final ManagedImageClient images=mock(ManagedImageClient.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final CommentService service=new CommentService(rows,writer,purchases,images,mongo,json);
    private final String key="a1111111-1111-4111-8111-111111111111";
    private final CreateCommentRequest input=new CreateCommentRequest(7L,5L,5,"味道很好",List.of(),List.of("好吃"));
    CommentServiceTest() {
        when(purchases.requireCompletedPurchase(7L,5L,"Bearer signed")).thenReturn(2L);
        when(mongo.findById(anyString(),eq(CommentContent.class))).thenAnswer(a->
                new CommentContent(a.getArgument(0),"味道很好",List.of(),List.of("好吃"),Instant.now(),true));
    }
    @Test void writesContentBeforeMetadataAndReplayDoesNotWriteTwice() throws Exception {
        AtomicReference<CommentRow> saved=new AtomicReference<>();
        doAnswer(a->{saved.set(a.getArgument(0));return null;}).when(writer).commit(any());
        var first=service.create(9L,"同学","Bearer signed",key,input);
        var ordered=inOrder(mongo,writer);
        ordered.verify(mongo).insert(any(CommentContent.class));
        ordered.verify(writer).commit(any(CommentRow.class));
        when(rows.byRequest(9L,key)).thenAnswer(a->saved.get());
        var replay=service.create(9L,"同学","Bearer signed",key,input);
        assertEquals(first.commentId(),replay.commentId());
        verify(writer,times(1)).commit(any());
        assertThrows(BusinessException.class,()->service.create(9L,"同学","Bearer signed",key,
                new CreateCommentRequest(7L,5L,1,"改了内容",List.of(),List.of())));
        String response=json.writeValueAsString(first);
        for(String privateField:List.of("userId","orderId","requestHash","requestKey"))assertFalse(response.contains(privateField));
    }
    @Test void sqlFailureLeavesUnpublishedContentForReconciliation() {
        doThrow(new IllegalStateException("SQL unavailable")).when(writer).commit(any());
        assertThrows(IllegalStateException.class,()->service.create(9L,"同学","Bearer signed",key,input));
        verify(mongo).insert(any(CommentContent.class));
        verify(mongo,never()).remove(any(org.springframework.data.mongodb.core.query.Query.class),eq(CommentContent.class));
    }
    @Test void duplicatePurchaseRejectsBeforeWritingMongo() {
        when(rows.byPurchase(7L,5L)).thenReturn(new CommentRow(key,9L,"同学",7L,5L,2L,5,key,"hash",null));
        assertThrows(BusinessException.class,()->service.create(9L,"同学","Bearer signed",key,input));
        verify(mongo,never()).insert(any(CommentContent.class));
    }

    @Test void persistsImageKeysWhileReturningDisplayUrls() {
        String fileKey="0123456789abcdef0123456789abcdef";
        when(images.requireImageKey(fileKey,9L,"COMMENT_IMAGE",null)).thenReturn(fileKey);
        when(mongo.findById(anyString(),eq(CommentContent.class))).thenAnswer(a ->
                new CommentContent(a.getArgument(0),"正文",List.of(fileKey),List.of(),Instant.now(),true));
        var response=service.create(9L,"同学","Bearer signed",key,
                new CreateCommentRequest(7L,5L,5,"正文",List.of(fileKey.toUpperCase()),List.of()));
        var saved=org.mockito.ArgumentCaptor.forClass(CommentContent.class);
        verify(mongo).insert(saved.capture());
        assertEquals(List.of(fileKey),saved.getValue().imageFileKeys());
        assertEquals(List.of("/api/files/"+fileKey+"/content"),response.images());
    }
}
