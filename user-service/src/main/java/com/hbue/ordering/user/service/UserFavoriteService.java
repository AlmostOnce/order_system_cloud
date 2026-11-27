package com.hbue.ordering.user.service;

import com.hbue.ordering.user.model.FavoriteTargetType;
import com.hbue.ordering.user.vo.FavoriteStatusVO;
import com.hbue.ordering.user.vo.UserFavoritePageVO;

import java.util.List;

/**
 * CUSTOMER 收藏用例的业务契约。
 *
 * <p>调用方必须传入从 JWT 解析出的当前用户 ID；实现不允许用客户端提供的用户号
 * 替代身份上下文，也不跨库读取商品服务数据。</p>
 */
public interface UserFavoriteService {

    /**
     * 幂等收藏一个窗口或菜品；重复调用不会新增重复关系。
     *
     * @param userId     从当前已验签 JWT 的 sub 解析出的用户内部 ID
     * @param targetType 允许的收藏类型，仅 WINDOW 或 PRODUCT
     * @param targetId   商品服务中的窗口或菜品内部 ID
     */
    void add(Long userId, FavoriteTargetType targetType, Long targetId);

    /**
     * 幂等取消本人对指定窗口或菜品的收藏；目标关系不存在时也正常返回。
     *
     * @param userId     从当前已验签 JWT 的 sub 解析出的用户内部 ID
     * @param targetType 允许的收藏类型，仅 WINDOW 或 PRODUCT
     * @param targetId   商品服务中的窗口或菜品内部 ID
     */
    void remove(Long userId, FavoriteTargetType targetType, Long targetId);

    /**
     * 批量查询当前页可见目标中，哪些 ID 已被该用户收藏。
     *
     * @param userId     从当前已验签 JWT 的 sub 解析出的用户内部 ID
     * @param targetType 需要查询的目标类型
     * @param targetIds  当前页面的目标 ID，不能为空且最多100项
     * @return 只包含已收藏目标 ID 的结果
     */
    FavoriteStatusVO findStatuses(Long userId, FavoriteTargetType targetType, List<Long> targetIds);

    /**
     * 使用收藏 ID 游标读取当前用户指定类型的收藏。
     *
     * @param userId     从当前已验签 JWT 的 sub 解析出的用户内部 ID
     * @param targetType 收藏目标类型
     * @param beforeId   上页末条收藏 ID；首屏传 null
     * @param limit      页长，必须为1到50
     * @return 收藏记录、下一游标和是否还有后续页
     */
    UserFavoritePageVO list(Long userId, FavoriteTargetType targetType, Long beforeId, int limit);
}
