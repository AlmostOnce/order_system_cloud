package com.hbue.ordering.user.vo;

import java.time.LocalDateTime;

/** 仅供已认证用户本人查看，不返回密码及其哈希。 */
public record UserProfileVO(Long userId, String username, String roleCode, String email,
                            String phone, String avatarUrl, LocalDateTime createdAt) {
}
