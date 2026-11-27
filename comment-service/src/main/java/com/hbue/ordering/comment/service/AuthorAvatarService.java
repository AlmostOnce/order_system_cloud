package com.hbue.ordering.comment.service;

import java.util.Collection;
import java.util.Map;

/**
 * 评价作者头像地址的批量解析。
 *
 * <p>评论库只保存用户编号，头像属于用户服务的权威数据。列表页必须一次性批量解析，
 * 不能按行远程调用；返回 Map 而不是列表，调用方按用户编号取用，缺失的用户直接不出现即可，
 * 前端按无头像兜底渲染。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
public interface AuthorAvatarService {

    /**
     * 批量解析用户头像地址。
     *
     * @param userIds 用户编号集合，可空
     * @return 用户编号到头像地址的映射；解析失败或用户无头像时不包含该键
     */
    Map<Long, String> resolve(Collection<Long> userIds);
}
