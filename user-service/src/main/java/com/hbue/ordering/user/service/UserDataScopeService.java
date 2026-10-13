package com.hbue.ordering.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.user.model.UserDataScopeDO;

import java.util.List;

/**
 * 用户数据范围业务服务。
 *
 * <p>数据范围由 restaurant / general manager 授予，是授权的唯一事实来源；
 * 角色分配不走本服务。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public interface UserDataScopeService extends IService<UserDataScopeDO> {

    /**
     * 查询指定用户持有的全部数据范围。
     *
     * @param userId 用户 ID
     * @return 数据范围列表
     */
    List<UserDataScopeDO> listByUserId(Long userId);

    /**
     * 查询指定用户的数据范围快照。
     *
     * @param userId 用户 ID
     * @return 范围快照；用户无任何范围时返回空快照
     */
    UserScopeSnapshot loadSnapshot(Long userId);

    /**
     * 覆盖式替换指定用户的全部数据范围。
     *
     * <p>写入后立即删除该用户的 Redis 范围缓存，使变更即时生效。</p>
     *
     * @param userId 用户 ID
     * @param scopes 新的范围列表；为空表示清空该用户的全部范围
     */
    void replaceScopes(Long userId, List<UserDataScopeDO> scopes);

    /**
     * 撤销指定用户的单条数据范围。
     *
     * <p>写入后立即删除该用户的 Redis 范围缓存，使变更即时生效。</p>
     *
     * @param userId 用户 ID
     * @param scopeId 范围记录 ID
     */
    void revokeScope(Long userId, Long scopeId);

    /**
     * 授予全平台范围。
     *
     * <p>仅用于引导期信任根：初始总经理必须带外获得全平台范围，
     * 之后的下沉授权由其发起。重复授予时保持幂等，不产生重复记录。</p>
     *
     * @param userId 用户 ID
     */
    void grantPlatformScope(Long userId);
}
