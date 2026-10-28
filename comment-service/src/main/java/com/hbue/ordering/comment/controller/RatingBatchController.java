package com.hbue.ordering.comment.controller;

import com.hbue.ordering.comment.service.RatingBatchService;
import com.hbue.ordering.comment.vo.RatingBatchItemVO;
import com.hbue.ordering.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comments/ratings")
public class RatingBatchController {
    private final RatingBatchService ratings;
    public RatingBatchController(RatingBatchService ratings) { this.ratings = ratings; }
    @GetMapping("/batch")
    public ApiResponse<List<RatingBatchItemVO>> batch(@RequestParam("kind") String kind, @RequestParam("ids") List<Long> ids) {
        return ApiResponse.success(ratings.get(kind, ids));
    }
}
