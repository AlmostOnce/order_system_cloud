package com.hbue.ordering.support.service;

import com.hbue.ordering.support.model.SupportTicketDO;
import com.hbue.ordering.support.support.SupportType;

/**
 * 管理端工单的数据范围与类型权限判定。
 *
 * <p>角色注解只解决"这一类接口能不能进"，本服务再判断"这一条工单能不能看"：总经理持有全平台
 * 范围时可处理两类工单；餐厅经理只能处理授权餐厅的用餐工单；系统管理员只处理软件工单，
 * 不接触任何用餐业务数据。</p>
 *
 * <p>范围查询失败一律拒绝操作，禁止把"查不到范围"降级为"默认放行"或"空范围继续处理"。</p>
 */
public interface SupportScopeService {

    /**
     * 读取指定员工的最新数据范围。
     *
     * <p>每次操作前调用，不做本地缓存，保证范围调整立即生效。</p>
     *
     * @param userId 员工内部用户编号
     * @return 数据范围快照
     */
    SupportScope resolve(Long userId);

    /**
     * 校验当前管理角色是否可见该工单类型。
     *
     * <p>类型限制先于范围限制：餐厅经理操作软件工单、系统管理员操作用餐工单都返回 403，
     * 不通过"资源不存在"掩盖角色设计。</p>
     *
     * @param actor  管理端操作者
     * @param ticket 工单
     */
    void requireTypeAllowed(SupportActor actor, SupportTicketDO ticket);

    /**
     * 校验操作者是否可访问指定工单。
     *
     * <p>先校验类型角色，再按角色校验数据范围；类型允许但范围不允许时返回 404，
     * 避免通过枚举工单标识探测其他餐厅的工单是否存在。</p>
     *
     * @param actor  管理端操作者
     * @param scope  操作者数据范围
     * @param ticket 待访问工单
     */
    void requireAccessible(SupportActor actor, SupportScope scope, SupportTicketDO ticket);

    /**
     * 校验队列查询的类型筛选条件是否越权。
     *
     * <p>队列分页与详情使用同一套角色规则，集中在此处避免列表与详情口径漂移
     * （例如列表放行、点进详情却 403）。</p>
     *
     * @param actor      管理端操作者
     * @param scope      操作者数据范围
     * @param typeFilter 客户端指定的类型筛选，可空
     */
    void requireTypeFilterAllowed(SupportActor actor, SupportScope scope, SupportType typeFilter);

    /**
     * 判断当前角色是否可以使用全平台队列查询。
     *
     * @param actor 管理端操作者
     * @param scope 数据范围
     * @return 无需餐厅过滤时返回 true
     */
    boolean usesPlatformQueue(SupportActor actor, SupportScope scope);

    /**
     * 判断当前角色是否只允许查询用餐工单。
     *
     * @param actor 管理端操作者
     * @param scope 数据范围
     * @return 只能看用餐工单时返回 true
     */
    boolean diningOnly(SupportActor actor, SupportScope scope);
}
