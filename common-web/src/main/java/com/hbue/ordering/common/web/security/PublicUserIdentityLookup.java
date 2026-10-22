package com.hbue.ordering.common.web.security;

/** 仅 user-service 提供本地实现，其他服务通过内部接口解析身份。 */
public interface PublicUserIdentityLookup {
    Long findInternalId(String userUuid);
}
