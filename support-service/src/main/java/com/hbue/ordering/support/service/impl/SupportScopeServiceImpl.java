package com.hbue.ordering.support.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.client.SupportUserScopeClient;
import com.hbue.ordering.support.client.vo.SupportUserScopeVO;
import com.hbue.ordering.support.model.SupportTicketDO;
import com.hbue.ordering.support.service.SupportActor;
import com.hbue.ordering.support.service.SupportScope;
import com.hbue.ordering.support.service.SupportScopeService;
import com.hbue.ordering.support.support.SupportRole;
import com.hbue.ordering.support.support.SupportType;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端工单数据范围与类型权限判定实现。
 *
 * <p>角色编码统一取自 {@link SupportRole}；判定顺序固定为"类型 → 范围"，且范围查询失败直接
 * 返回 503，绝不把失败降级为放行。</p>
 */
@Service
public class SupportScopeServiceImpl implements SupportScopeService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(SupportScopeServiceImpl.class);

    /**
     * 用户数据范围内部客户端。
     */
    private final SupportUserScopeClient userScopeClient;

    /**
     * 服务间共享内部令牌。
     */
    private final String internalToken;

    /**
     * 创建范围服务。
     *
     * @param userScopeClient 用户数据范围内部客户端
     * @param internalToken   服务间共享内部令牌
     */
    public SupportScopeServiceImpl(SupportUserScopeClient userScopeClient,
                                   @Value("${internal.scope.token:}") String internalToken) {
        this.userScopeClient = userScopeClient;
        this.internalToken = internalToken;
    }

    /**
     * 读取指定员工的最新数据范围，不做本地缓存。
     *
     * <p>用户服务不可用、返回业务失败或数据为空时一律返回 503，禁止把范围查询失败降级为
     * 默认放行或空范围继续处理。</p>
     */
    @Override
    public SupportScope resolve(Long userId) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "内部调用凭据未配置");
        }
        ApiResponse<SupportUserScopeVO> response;
        try {
            response = userScopeClient.getScopes(userId, internalToken);
        } catch (FeignException exception) {
            log.warn("查询用户数据范围失败，userId={}, httpStatus={}", userId, exception.status());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认数据范围，请稍后重试");
        }
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认数据范围，请稍后重试");
        }
        SupportUserScopeVO data = response.getData();
        return new SupportScope(data.platform(),
                data.restaurantIds() == null ? List.of() : List.copyOf(data.restaurantIds()));
    }

    /**
     * 校验当前管理角色是否可见该工单类型。
     */
    @Override
    public void requireTypeAllowed(SupportActor actor, SupportTicketDO ticket) {
        SupportType type = SupportType.findByCode(ticket.getType());
        if (type == null) {
            // 历史脏数据无法判定类型，禁止任何管理端操作，避免绕过范围约束。
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "工单类型异常，无法处理");
        }
        if (actor.hasRole(SupportRole.SYSTEM_ADMIN) && type != SupportType.SOFTWARE) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "系统管理员只能处理软件问题");
        }
        if (actor.hasRole(SupportRole.RESTAURANT_MANAGER) && type != SupportType.DINING) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "餐厅经理只能处理用餐问题");
        }
    }

    /**
     * 校验操作者是否可访问指定工单。
     */
    @Override
    public void requireAccessible(SupportActor actor, SupportScope scope, SupportTicketDO ticket) {
        requireTypeAllowed(actor, ticket);
        if (actor.hasRole(SupportRole.GENERAL_MANAGER) && scope.platform()) {
            return;
        }
        // 系统管理员在类型校验阶段已被限定为软件工单；软件工单没有餐厅归属，
        // 不能落入下面的餐厅范围分支，否则队列可见但详情与处理全部被误判为不存在。
        if (actor.hasRole(SupportRole.SYSTEM_ADMIN)
                && SupportType.SOFTWARE.getCode().equals(ticket.getType())) {
            return;
        }
        // 非全平台范围只允许访问授权餐厅的用餐工单；软件工单无餐厅归属，天然不可见。
        if (SupportType.DINING.getCode().equals(ticket.getType())
                && ticket.getRestaurantId() != null
                && scope.restaurantIds().contains(ticket.getRestaurantId())) {
            return;
        }
        throw new BusinessException(CommonErrorCode.NOT_FOUND, "工单不存在");
    }

    /**
     * 校验队列查询的类型筛选条件是否越权。
     *
     * <p>与 {@link #requireTypeAllowed} 使用同一组角色常量，避免队列与详情两条路径的规则漂移。</p>
     */
    @Override
    public void requireTypeFilterAllowed(SupportActor actor, SupportScope scope, SupportType typeFilter) {
        if (typeFilter == null) {
            return;
        }
        if (actor.hasRole(SupportRole.SYSTEM_ADMIN) && typeFilter != SupportType.SOFTWARE) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "系统管理员只能查看软件问题");
        }
        if (diningOnly(actor, scope) && typeFilter != SupportType.DINING) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "当前范围只能查看用餐问题");
        }
    }

    /**
     * 判断当前角色是否可以使用全平台队列查询。
     */
    @Override
    public boolean usesPlatformQueue(SupportActor actor, SupportScope scope) {
        return actor.hasRole(SupportRole.SYSTEM_ADMIN)
                || (actor.hasRole(SupportRole.GENERAL_MANAGER) && scope.platform());
    }

    /**
     * 判断当前角色是否只允许查询用餐工单。
     */
    @Override
    public boolean diningOnly(SupportActor actor, SupportScope scope) {
        if (actor.hasRole(SupportRole.SYSTEM_ADMIN)) {
            return false;
        }
        if (actor.hasRole(SupportRole.RESTAURANT_MANAGER)) {
            return true;
        }
        return actor.hasRole(SupportRole.GENERAL_MANAGER) && !scope.platform();
    }
}
