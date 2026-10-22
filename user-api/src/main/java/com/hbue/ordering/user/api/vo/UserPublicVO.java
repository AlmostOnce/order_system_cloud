package com.hbue.ordering.user.api.vo;

/** 用户侧公开视图，禁止包含内部数据库主键。 */
public record UserPublicVO(String userUuid, String username, String roleCode) {
    public static UserPublicVO from(UserBasicVO user) { return new UserPublicVO(user.getUserUuid(), user.getUsername(), user.getRoleCode()); }
}
