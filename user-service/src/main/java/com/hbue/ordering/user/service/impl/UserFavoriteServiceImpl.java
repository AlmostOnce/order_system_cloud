package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.mapper.FavoriteEventOutboxMapper;
import com.hbue.ordering.user.mapper.UserFavoriteMapper;
import com.hbue.ordering.user.model.FavoriteTargetType;
import com.hbue.ordering.user.model.UserFavoriteDO;
import com.hbue.ordering.user.service.UserFavoriteService;
import com.hbue.ordering.user.vo.FavoriteStatusVO;
import com.hbue.ordering.user.vo.UserFavoriteItemVO;
import com.hbue.ordering.user.vo.UserFavoritePageVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户收藏关系的业务实现。
 *
 * <p>收藏只维护用户库内关系，不同步复制商品目录；目标下架后关系仍可读取和删除。
 * 去重由数据库唯一键保障；真实关系变更与推荐事件在同一本地事务中提交。</p>
 */
@Service
public class UserFavoriteServiceImpl implements UserFavoriteService {
    /**
     * 批量状态查询的最大目标数，限制动态 IN 查询的 SQL 与参数规模。
     */
    private static final int MAX_STATUS_IDS = 100;
    /**
     * 单页收藏数量上限，防止客户端要求无界结果集。
     */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 收藏关系唯一数据访问入口。
     */
    private final UserFavoriteMapper favoriteMapper;
    /**
     * 事务内登记可重试的收藏变更事件，不直接访问消息队列。
     */
    private final FavoriteEventOutboxMapper outboxMapper;

    /**
     * 注入收藏事实和本地发件箱访问接口，确保两者使用同一用户库事务。
     *
     * @param favoriteMapper 收藏关系数据访问接口
     * @param outboxMapper   收藏变更发件箱接口
     */
    public UserFavoriteServiceImpl(UserFavoriteMapper favoriteMapper, FavoriteEventOutboxMapper outboxMapper) {
        this.favoriteMapper = favoriteMapper;
        this.outboxMapper = outboxMapper;
    }

    /**
     * 收藏一个窗口或菜品；唯一键冲突是重复操作而不是业务失败。
     *
     * @param userId     JWT 主体解析出的当前用户内部 ID
     * @param targetType 收藏类型
     * @param targetId   商品服务的窗口或菜品 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, FavoriteTargetType targetType, Long targetId) {
        validateIdentityAndTarget(userId, targetType, targetId);
        try {
            // 普通 INSERT 的唯一键冲突用于识别重复请求，不受 MySQL affected/found rows 设置影响。
            if (favoriteMapper.insertIfAbsent(userId, targetType.name(), targetId) != 1) {
                throw new IllegalStateException("收藏关系写入失败");
            }
        } catch (DuplicateKeyException exception) {
            // 现有唯一键限定用户、类型、目标；重复点击视为成功，不重复发布变化。
            return;
        }
        // 发件箱失败向外抛出，使关系写入一起回滚；持锁到提交保证同一目标的事件 ID 与状态顺序一致。
        if (outboxMapper.insert(userId, targetType.name(), targetId, true) != 1) {
            throw new IllegalStateException("收藏变更事件登记失败");
        }
    }

    /**
     * 取消收藏；目标不存在时仍作为幂等成功，便于客户端安全重试。
     *
     * @param userId     JWT 主体解析出的当前用户内部 ID
     * @param targetType 收藏类型
     * @param targetId   商品服务的窗口或菜品 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, FavoriteTargetType targetType, Long targetId) {
        validateIdentityAndTarget(userId, targetType, targetId);
        // 删除条件同时限定 user_id，伪造其他用户的目标 ID 也不能触及他人收藏。
        int deleted = favoriteMapper.deleteFavorite(userId, targetType.name(), targetId);
        // 仅实际删除才登记取消墓碑；重复删除不触发下游刷新。
        if (deleted == 1 && outboxMapper.insert(userId, targetType.name(), targetId, false) != 1) {
            throw new IllegalStateException("取消收藏事件登记失败");
        }
    }

    /**
     * 查询当前页面一批目标的收藏状态。
     *
     * @param userId     JWT 主体解析出的当前用户内部 ID
     * @param targetType 收藏类型
     * @param targetIds  当前页面的目标 ID 集合，最多 100 项
     * @return 仅包含已收藏目标 ID 的结果
     */
    @Override
    public FavoriteStatusVO findStatuses(Long userId,
                                         FavoriteTargetType targetType,
                                         List<Long> targetIds) {
        if (userId == null || userId <= 0 || targetType == null
                || targetIds == null || targetIds.isEmpty() || targetIds.size() > MAX_STATUS_IDS
                || targetIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw badRequest("收藏状态查询参数不正确");
        }
        // 先去重再查询，减少重复占用 IN 参数，并保持结果只由数据库中已存在关系决定。
        List<Long> uniqueIds = targetIds.stream().distinct().toList();
        List<Long> favoriteIds = favoriteMapper.selectFavoriteTargetIds(userId, targetType.name(), uniqueIds);
        return new FavoriteStatusVO(favoriteIds == null ? List.of() : favoriteIds);
    }

    /**
     * 按 favorite_id 倒序分页，新增收藏不会改变已经发出的旧页游标。
     *
     * @param userId     JWT 主体解析出的当前用户内部 ID
     * @param targetType 收藏类型
     * @param beforeId   上一页末条的收藏 ID；首屏时为空
     * @param limit      页面希望读取的记录数，范围 1 至 50
     * @return 当前页与下一游标
     */
    @Override
    public UserFavoritePageVO list(Long userId,
                                   FavoriteTargetType targetType,
                                   Long beforeId,
                                   int limit) {
        if (userId == null || userId <= 0 || targetType == null || limit < 1 || limit > MAX_PAGE_SIZE
                || (beforeId != null && beforeId <= 0)) {
            throw badRequest("收藏列表查询参数不正确");
        }
        // 多取一条只用于判断 hasMore，不把探测行暴露给客户端。
        List<UserFavoriteDO> rows = favoriteMapper.selectPage(
                userId, targetType.name(), beforeId, limit + 1);
        boolean hasMore = rows.size() > limit;
        int visibleCount = Math.min(rows.size(), limit);
        List<UserFavoriteItemVO> items = new ArrayList<>(visibleCount);
        for (int index = 0; index < visibleCount; index++) {
            UserFavoriteDO row = rows.get(index);
            items.add(new UserFavoriteItemVO(row.getFavoriteId(), targetType,
                    row.getTargetId(), row.getCreatedAt()));
        }
        // 只有确实存在下一页才返回末条可见记录作为 cursor。
        Long nextCursor = hasMore && !items.isEmpty()
                ? items.get(items.size() - 1).favoriteId()
                : null;
        return new UserFavoritePageVO(items, nextCursor, hasMore);
    }

    /**
     * 验证服务边界的身份和目标参数，避免内部调用绕过 Controller 校验。
     */
    private void validateIdentityAndTarget(Long userId, FavoriteTargetType targetType, Long targetId) {
        if (userId == null || userId <= 0 || targetType == null || targetId == null || targetId <= 0) {
            throw badRequest("收藏目标参数不正确");
        }
    }

    /**
     * 把输入边界错误统一映射为项目标准业务 400 响应。
     *
     * @param message 面向调用方的参数错误说明
     * @return 由全局异常处理器转换为标准 API 错误响应的异常
     */
    private BusinessException badRequest(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }
}
