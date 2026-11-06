package com.hbue.ordering.comment.service;
import com.hbue.ordering.comment.api.*;
import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommentValidationTest {
    @Test void rejectsInvalidScoresAndOversizedContent() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for(int score:new int[]{0,6})assertFalse(validator.validate(new CreateCommentRequest(1L,2L,score,"好吃",List.of(),List.of())).isEmpty());
            assertFalse(validator.validate(new CreateCommentRequest(1L,2L,5,"好".repeat(1001),List.of(),List.of())).isEmpty());
            assertFalse(validator.validate(new CreateCommentRequest(1L,2L,5,"好吃",List.of("invalid"),List.of())).isEmpty());
            assertTrue(validator.validate(new CreateCommentRequest(1L,2L,5,"好吃",List.of(),List.of())).isEmpty());
        }
    }
    /** 店铺与单道菜分别校验三张边界，空列表继续支持只评分。 */
    @Test void acceptsThreeImagesAndRejectsFourthForEachReviewTarget() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var keys = java.util.stream.IntStream.rangeClosed(1, 4)
                    .mapToObj(index -> String.format("%032x", index)).toList();
            for (int count : new int[] {0, 3, 4}) {
                var images = keys.subList(0, count);
                assertEquals(count <= 3, validator.validate(new CreateCommentRequest(
                        1L, 2L, 5, "", images, List.of())).isEmpty());
                assertEquals(count <= 3, validator.validate(new CreateWindowReviewRequest(
                        1L, 5, "", images, List.of())).isEmpty());
            }
        }
    }
    @Test void ratingSnapshotHasBoundedScoresAndEmptyRatingIsNotZeroStars() {
        String id="a1111111-1111-4111-8111-111111111111";
        assertTrue(new ProductRatingChangedEvent(1,id,1L,2,2,9).valid());
        assertFalse(new ProductRatingChangedEvent(1,id,1L,2,2,11).valid());
        assertNull(ProductRatingVO.of(1L,0,0).averageScore());
        assertEquals("4.50",ProductRatingVO.of(1L,2,9).averageScore().toPlainString());
    }
}
