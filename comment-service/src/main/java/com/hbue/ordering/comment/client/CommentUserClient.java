package com.hbue.ordering.comment.client;

import com.hbue.ordering.common.response.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", contextId = "commentUserAvatars")
public interface CommentUserClient {
    @GetMapping("/internal/users/avatars")
    ApiResponse<Map<Long, String>> avatars(@RequestParam("userIds") List<Long> userIds,
            @RequestHeader("X-Internal-Token") String internalToken);
}
