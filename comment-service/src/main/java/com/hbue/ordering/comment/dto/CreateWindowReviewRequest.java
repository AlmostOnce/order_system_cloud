package com.hbue.ordering.comment.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateWindowReviewRequest(@NotNull @Positive Long orderId, @Min(1) @Max(5) int score,
        @Size(max = 1000) String content,
        @Size(max = 6) List<@NotBlank @Pattern(regexp = "[a-fA-F0-9]{32}") String> imageFileKeys,
        @Size(max = 5) List<@NotBlank @Size(max = 20) String> tags) {}
