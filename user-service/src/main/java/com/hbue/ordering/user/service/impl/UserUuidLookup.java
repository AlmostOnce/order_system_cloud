package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.web.security.PublicUserIdentityLookup;
import com.hbue.ordering.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

@Component
public class UserUuidLookup implements PublicUserIdentityLookup {
    private final UserMapper mapper;
    public UserUuidLookup(UserMapper mapper) { this.mapper = mapper; }
    @Override public Long findInternalId(String userUuid) { return mapper.selectIdByUuid(userUuid); }
}
