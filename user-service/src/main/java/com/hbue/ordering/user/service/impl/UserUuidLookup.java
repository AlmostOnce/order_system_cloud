package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.web.security.PublicUserIdentityLookup;
import com.hbue.ordering.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/**
 * 对外公开用户标识到内部数据的只读查询适配器。
 *
 * <p>网关与鉴权组件只持有对外的 {@code userUuid}，需要在每次请求中确认邮箱验证状态、
 * 凭据版本与内部主键。这些查询下沉到用户服务自己的 Mapper，其它模块不直接接触用户库。</p>
 *
 * <p>本类只提供单条按 UUID 查询，不做批量也不缓存：凭据版本用于立即失效旧令牌，
 * 任何缓存都会让"改密后旧令牌仍可用"成为可能。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
@Component
public class UserUuidLookup implements PublicUserIdentityLookup {

    /**
     * 用户数据访问。
     */
    private final UserMapper mapper;

    /**
     * 创建查询适配器。
     *
     * @param mapper 用户数据访问
     */
    public UserUuidLookup(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Boolean isEmailVerified(String userUuid) {
        return mapper.selectEmailVerified(userUuid);
    }

    @Override
    public Long findCredentialVersion(String userUuid) {
        return mapper.selectCredentialVersion(userUuid);
    }

    @Override
    public Long findInternalId(String userUuid) {
        return mapper.selectIdByUuid(userUuid);
    }
}
