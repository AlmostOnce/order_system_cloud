package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.web.media.FileReferences;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.media.ImageFileRequest;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import com.hbue.ordering.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/avatar")
@PreAuthorize("isAuthenticated()")
public class UserAvatarController {
    private final ManagedImageClient images;
    private final UserService users;

    public UserAvatarController(ManagedImageClient images, UserService users) {
        this.images = images;
        this.users = users;
    }

    @PutMapping
    public ApiResponse<String> update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImageFileRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        String fileKey = images.requireImageKey(request.fileKey(), userId, "USER_AVATAR", null);
        users.updateOwnAvatar(userId, fileKey);
        return ApiResponse.success(FileReferences.url(fileKey));
    }
}
