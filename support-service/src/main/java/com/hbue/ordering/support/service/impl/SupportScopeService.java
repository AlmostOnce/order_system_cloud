package com.hbue.ordering.support.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.client.SupportUserScopeClient;
import com.hbue.ordering.support.client.vo.SupportUserScopeVO;
import com.hbue.ordering.support.model.SupportTicketDO;
import com.hbue.ordering.support.service.SupportActor;
import com.hbue.ordering.support.service.SupportScope;
import com.hbue.ordering.support.support.SupportType;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端工单数据范围与类型权限判定。
 *
 * <p>角色注解只解决「这一类接口能不能进」，本服务再判断「这一条工单能不能看」：
 * 总经理持有全平台范围时可处理两类工单；餐厅经理只能处理授权餐厅的用餐工单；
 * 系统管理员只处理软件工单，不接触任何用餐业务数据。范围查询失败时拒绝操作，
 * 不把空范围解释为全平台放行。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Service
public class SupportScopeService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(SupportScopeService.class);

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
    public SupportScopeService(SupportUserScopeClient userScopeClient,
                               @Value("${internal.scope.token:}") String internalToken) {
        this.userScopeClient = userScopeClient;
        this.internalToken = internalToken;
    }

    /**
     * 读取指定员工的最新数据范围。
     *
     * <p>每次操作前调用，不做本地缓存。用户服务不可用、返回业务失败或数据为空时
     * 一律返回 503，禁止把范围查询失败降级为默认放行或空范围继续处理。</p>
     *
     * @param userId 员工内部用户编号
     * @return 数据范围快照
     */
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
     *
     * <p>类型限制先于范围限制：餐厅经理操作软件工单、系统管理员操作用餐工单
     * 都返回 403，不通过「资源不存在」掩盖角色设计。</p>
     *
     * @param actor  管理端操作者
     * @param ticket 工单
     * @throws BusinessException 角色不允许该工单类型时抛出 403
     */
    public void requireTypeAllowed(SupportActor actor, SupportTicketDO ticket) {
        SupportType type = SupportType.findByCode(ticket.getType());
        if (type == null) {
            // 历史脏数据无法判定类型，禁止任何管理端操作，避免绕过范围约束。
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "工单类型异常，无法处理");
        }
        if (actor.hasRole("SYSTEM_ADMIN") && type != SupportType.SOFTWARE) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "系统管理员只能处理软件问题");
        }
        if (actor.hasRole("RESTAURANT_MANAGER") && type != SupportType.DINING) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "餐厅经理只能处理用餐问题");
        }
    }

    /**
     * 校验操作者是否可访问指定工单。
     *
     * <p>先校验类型角色，再按角色校验数据范围：总经理持有全平台范围可处理两类工单；
     * 系统管理员可处理任意软件工单；其他情况只允许授权餐厅的用餐工单。
     * 类型允许但范围不允许时返回 404，避免通过枚举工单标识探测其他餐厅的工单是否存在。</p>
     *
     * @param actor         管理端操作者
     * @param scope         操作者数据范围
     * @param ticket        待访问工单
     * @throws BusinessException 类型或范围不允许时抛出
     */
    public void requireAccessible(SupportActor actor, SupportScope scope, SupportTicketDO ticket) {
        requireTypeAllowed(actor, ticket);
        if (actor.hasRole("GENERAL_MANAGER") && scope.platform()) {
            return;
        }
        // 系统管理员在类型校验阶段已被限定为软件工单；软件工单没有餐厅归属，
        // 不能落入下面的餐厅范围分支，否则队列可见但详情与处理全部被误判为不存在。
        if (actor.hasRole("SYSTEM_ADMIN")
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
     * 判断当前角色是否可以使用全平台队列查询。
     *
     * <p>系统管理员处理软件工单，本身不依赖餐厅范围，按全平台查询；
     * 总经理仅在确实持有全平台范围时按全平台查询。</p>
     *
     * @param actor 管理端操作者
     * @param scope 数据范围
     * @return 无需餐厅过滤时返回 true
     */
    public boolean usesPlatformQueue(SupportActor actor, SupportScope scope) {
        return actor.hasRole("SYSTEM_ADMIN") || (actor.hasRole("GENERAL_MANAGER") && scope.platform());
    }

    /**
     * 判断当前角色是否只允许查询用餐工单。
     *
     * @param actor 管理端操作者
     * @param scope 数据范围
     * @return 只能看用餐工单时返回 true
     */
    public boolean diningOnly(SupportActor actor, SupportScope scope) {
        if (actor.hasRole("SYSTEM_ADMIN")) {
            return false;
        }
        if (actor.hasRole("RESTAURANT_MANAGER")) {
            return true;
        }
        return actor.hasRole("GENERAL_MANAGER") && !scope.platform();
    }
}
