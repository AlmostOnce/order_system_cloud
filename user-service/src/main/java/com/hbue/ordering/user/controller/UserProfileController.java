package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.UpdateProfileRequest;
import com.hbue.ordering.user.service.UserService;
import com.hbue.ordering.user.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/profile")
@PreAuthorize("isAuthenticated()")
public class UserProfileController {
    private final UserService users;

    public UserProfileController(UserService users) {
        this.users = users;
    }

    @PutMapping
    public ApiResponse<UserProfileVO> update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(users.updateOwnProfile(Long.valueOf(jwt.getSubject()), request));
    }
}
