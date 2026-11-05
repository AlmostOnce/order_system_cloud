package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.common.web.security.JwtUserIdentity;
import com.hbue.ordering.common.web.security.PublicUserIdentityLookup;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
public class UserIdentityInternalController {
    private final InternalScopeTokenVerifier verifier;
    private final PublicUserIdentityLookup lookup;
    private final com.hbue.ordering.user.service.UserService users;
    public UserIdentityInternalController(InternalScopeTokenVerifier verifier, PublicUserIdentityLookup lookup, com.hbue.ordering.user.service.UserService users) { this.verifier = verifier; this.lookup = lookup; this.users = users; }

    @GetMapping("/identities/{userUuid}")
    public ApiResponse<Long> resolve(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable("userUuid") String userUuid,
            @RequestParam(value="credentialVersion", required=false) Long credentialVersion) {
        verifier.verify(token);
        if (!JwtUserIdentity.PUBLIC_UUID.matcher(userUuid).matches()) throw new BusinessException(CommonErrorCode.BAD_REQUEST, "用户标识格式不正确");
        if (credentialVersion != null && (credentialVersion < 0 || !credentialVersion.equals(lookup.findCredentialVersion(userUuid))))
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录密码已重置，请重新登录");
        if(!Boolean.TRUE.equals(lookup.isEmailVerified(userUuid)))
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED,"邮箱尚未验证，请重新登录完成邮箱验证");
        Long id = lookup.findInternalId(userUuid);
        if (id == null) throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在或已停用");
        return ApiResponse.success(id);
    }
    @GetMapping("/avatars")
    public ApiResponse<java.util.Map<Long, String>> avatars(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam("userIds") java.util.List<Long> userIds) {
        verifier.verify(token);
        return ApiResponse.success(users.getAvatarUrls(userIds));
    }
    @GetMapping("/{userId}/basic")
    public ApiResponse<com.hbue.ordering.user.api.vo.UserBasicVO> basic(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable("userId") Long userId) {
        verifier.verify(token);
        return ApiResponse.success(users.getUserById(userId));
    }
}
