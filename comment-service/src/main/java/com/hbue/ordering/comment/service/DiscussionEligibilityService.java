package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.model.ReviewType;

import java.util.List;

/**
 * 评论的消费准入校验。
 *
 * <p>发表评论要求操作者消费过被评论的对象：菜品评价看"是否买过该菜品"，
 * 店铺评价看"是否在该档口消费过"。判定只使用服务端事实，不采信客户端参数。</p>
 *
 * <p>管理角色（档口负责人及以上）跳过消费校验，否则商家无法参与本档口评价的讨论。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
public interface DiscussionEligibilityService {

    /**
     * 校验操作者具备评论资格。
     *
     * @param userId   当前登录用户内部编号
     * @param roles    当前登录用户的角色编码集合
     * @param type     评价类型，决定消费事实的查询维度
     * @param targetId 菜品编号或档口编号
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         未消费时抛出 403；无法确认消费事实时抛出 503
     */
    void requireConsumed(Long userId, List<String> roles, ReviewType type, Long targetId);
}
