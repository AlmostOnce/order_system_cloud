package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.client.CommentNotificationClient;
import com.hbue.ordering.comment.client.vo.ReviewDiscussionNotificationRequest;
import com.hbue.ordering.comment.dto.CreateDiscussionRequest;
import com.hbue.ordering.comment.dto.UpdateDiscussionRequest;
import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.mapper.ReviewDiscussionMapper;
import com.hbue.ordering.comment.mapper.WindowReviewMapper;
import com.hbue.ordering.comment.model.CommentRowDO;
import com.hbue.ordering.comment.model.ReviewDiscussionDO;
import com.hbue.ordering.comment.model.ReviewType;
import com.hbue.ordering.comment.service.AuthorAvatarService;
import com.hbue.ordering.comment.service.DiscussionEligibilityService;
import com.hbue.ordering.comment.service.DiscussionGuard;
import com.hbue.ordering.comment.service.ReviewAuthorityService;
import com.hbue.ordering.comment.service.ReviewDiscussionService;
import com.hbue.ordering.comment.vo.ReviewDiscussionPageVO;
import com.hbue.ordering.comment.vo.ReviewDiscussionVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.role.RoleCode;
import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 评价自由评论区的实现。
 *
 * <p>写入流程：校验正文与幂等键 → 确认评价可见 → 限流 → 消费准入 → 分布式锁 →
 * 幂等回读 → 单表插入 → 事务提交后发通知。任一步失败都不写库；
 * 通知失败只记日志，不回滚评论。</p>
 *
 * <p>为什么写方法不加 {@code @Transactional}：每次只做一条 INSERT 或 UPDATE，
 * 单语句本身原子，显式事务没有额外收益；同时通知必须在写库之后发送，
 * 包在同一个事务里会把"已提交"和"未提交"混在一起。</p>
 *
 * <p>读取全部只做批量拼装：评价列表一次取数量与预览，楼层列表一次取回复数与预览，
 * 头像统一批量解析，任何附属数据缺失都不影响评论主体展示。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Service
public class ReviewDiscussionServiceImpl implements ReviewDiscussionService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(ReviewDiscussionServiceImpl.class);

    /**
     * 评论正文长度上限，与数据库列和请求校验保持一致。
     */
    private static final int MAX_CONTENT_LENGTH = 500;

    /**
     * 评价列表内嵌的评论预览条数。
     */
    private static final int PREVIEW_LIMIT = 3;

    /**
     * 展示名长度上限，与数据库列一致。
     */
    private static final int MAX_NAME_LENGTH = 64;

    /**
     * 匿名兜底展示名。
     */
    private static final String DEFAULT_NAME = "校园用户";

    /**
     * 评论表读写。
     */
    private final ReviewDiscussionMapper discussions;

    /**
     * 菜品评价元数据读取，用于确认评价存在与其档口、菜品。
     */
    private final CommentMapper comments;

    /**
     * 店铺评价读取，用于确认评价存在与其档口。
     */
    private final WindowReviewMapper windowReviews;

    /**
     * 作者头像批量解析。
     */
    private final AuthorAvatarService authors;

    /**
     * 消费准入校验。
     */
    private final DiscussionEligibilityService eligibility;

    /**
     * 档口范围判定，用于屏蔽操作。
     */
    private final ReviewAuthorityService authority;

    /**
     * 限流与提交锁。
     */
    private final DiscussionGuard guard;

    /**
     * 通知服务内部客户端。
     */
    private final CommentNotificationClient notifications;

    /**
     * 服务间共享内部令牌。
     */
    private final String internalToken;

    /**
     * 创建评论服务。
     *
     * @param discussions   评论表读写
     * @param comments      菜品评价读取
     * @param windowReviews 店铺评价读取
     * @param authors       作者头像批量解析
     * @param eligibility   消费准入校验
     * @param authority     档口范围判定
     * @param guard         限流与提交锁
     * @param notifications 通知服务客户端
     * @param internalToken 服务间共享内部令牌
     */
    public ReviewDiscussionServiceImpl(ReviewDiscussionMapper discussions, CommentMapper comments,
                                       WindowReviewMapper windowReviews, AuthorAvatarService authors,
                                       DiscussionEligibilityService eligibility, ReviewAuthorityService authority,
                                       DiscussionGuard guard, CommentNotificationClient notifications,
                                       @Value("${internal.scope.token:}") String internalToken) {
        this.discussions = discussions;
        this.comments = comments;
        this.windowReviews = windowReviews;
        this.authors = authors;
        this.eligibility = eligibility;
        this.authority = authority;
        this.guard = guard;
        this.notifications = notifications;
        this.internalToken = internalToken;
    }

    /**
     * 发表顶层评论。
     *
     * @param userId     当前登录用户编号
     * @param authorName 作者展示名快照
     * @param roles      角色编码集合
     * @param type       评价类型
     * @param reviewId   评价公开标识
     * @param key        客户端幂等请求键
     * @param input      评论正文
     * @return 新建评论视图
     */
    @Override
    public ReviewDiscussionVO publish(Long userId, String authorName, List<String> roles, ReviewType type,
                                      String reviewId, String key, CreateDiscussionRequest input) {
        CommentServiceImpl.requireUuid(key);
        if (reviewId == null || reviewId.isBlank()) {
            throw bad("评价标识不能为空");
        }
        String content = normalizeContent(input);

        // 先确认评价可见：不可见评价不允许"盲评"，也避免把评论挂到已删除对象上。
        ReviewTarget target = requireTarget(type, reviewId);

        ReviewDiscussionDO existing = discussions.byRequest(userId, key);
        if (existing != null) {
            return replay(existing, content, userId);
        }

        guard.checkRate(userId, reviewId);
        eligibility.requireConsumed(userId, roles, type, target.purchaseTargetId());

        ReviewDiscussionDO saved;
        try (DistributedLockService.LockHandle handle = guard.acquireSubmitLock(userId, reviewId)) {
            // 锁内再次回读：两个并发请求携带不同请求键时，唯一键拦不住，只能靠锁串行化。
            existing = discussions.byRequest(userId, key);
            if (existing != null) {
                return replay(existing, content, userId);
            }
            String id = UUID.randomUUID().toString();
            ReviewDiscussionDO row = new ReviewDiscussionDO(id, type.code(), reviewId, target.windowId(),
                    id, null, 1, userId, roleOf(roles), displayName(authorName), content,
                    1, 1, key, LocalDateTime.now(), null);
            try {
                discussions.insert(row);
            } catch (DuplicateKeyException exception) {
                ReviewDiscussionDO winner = discussions.byRequest(userId, key);
                if (winner != null) {
                    return replay(winner, content, userId);
                }
                throw new BusinessException(CommonErrorCode.CONFLICT, "评论提交冲突，请刷新后重试");
            }
            saved = row;
        }

        notifyRecipient(target.ownerId(), type, reviewId, saved);
        return view(saved, avatar(userId), null, 0, List.of(), userId);
    }

    /**
     * 回复楼层内的某条评论。
     *
     * <p>无论回复的是楼层根还是楼内回复，都归到同一 {@code rootId}、深度固定为 2，
     * 因此对话可以一直继续，但不会产生第三层缩进。</p>
     *
     * @param userId     当前登录用户编号
     * @param authorName 作者展示名快照
     * @param roles      角色编码集合
     * @param parentId   被回复评论的公开标识
     * @param key        客户端幂等请求键
     * @param input      评论正文
     * @return 新建回复视图
     */
    @Override
    public ReviewDiscussionVO reply(Long userId, String authorName, List<String> roles, String parentId,
                                    String key, CreateDiscussionRequest input) {
        CommentServiceImpl.requireUuid(key);
        CommentServiceImpl.requireUuid(parentId);
        String content = normalizeContent(input);

        ReviewDiscussionDO parent = discussions.byId(parentId);
        if (parent == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        if (parent.status() != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该评论已不可回复");
        }
        ReviewDiscussionDO root = parent.depth() == 1 ? parent : discussions.byId(parent.rootId());
        if (root == null || root.status() != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该评论已不可回复");
        }
        ReviewType type = ReviewType.fromCode(root.reviewType());
        if (type == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "评论归属异常，请稍后重试");
        }
        // 重新确认评价仍可见：楼层根还在不代表评价没有被撤回。
        ReviewTarget target = requireTarget(type, root.reviewId());

        ReviewDiscussionDO existing = discussions.byRequest(userId, key);
        if (existing != null) {
            return replay(existing, content, userId);
        }

        guard.checkRate(userId, root.reviewId());
        eligibility.requireConsumed(userId, roles, type, target.purchaseTargetId());

        ReviewDiscussionDO saved;
        try (DistributedLockService.LockHandle handle = guard.acquireSubmitLock(userId, root.reviewId())) {
            existing = discussions.byRequest(userId, key);
            if (existing != null) {
                return replay(existing, content, userId);
            }
            String id = UUID.randomUUID().toString();
            ReviewDiscussionDO row = new ReviewDiscussionDO(id, type.code(), root.reviewId(), root.windowId(),
                    root.discussionId(), parent.discussionId(), 2, userId, roleOf(roles),
                    displayName(authorName), content, 1, 1, key, LocalDateTime.now(), null);
            try {
                discussions.insert(row);
            } catch (DuplicateKeyException exception) {
                ReviewDiscussionDO winner = discussions.byRequest(userId, key);
                if (winner != null) {
                    return replay(winner, content, userId);
                }
                throw new BusinessException(CommonErrorCode.CONFLICT, "评论提交冲突，请刷新后重试");
            }
            saved = row;
        }

        notifyRecipient(parent.authorId(), type, root.reviewId(), saved);
        return view(saved, avatar(userId), parentAuthorName(parent), 0, List.of(), userId);
    }

    /**
     * 编辑自己的评论。
     *
     * <p>只允许作者本人操作；管理角色可以屏蔽但不能改写他人正文。
     * 版本号不匹配说明评论已被其他窗口修改，返回冲突让客户端刷新。</p>
     *
     * @param userId 当前登录用户编号
     * @param id     公开评论标识
     * @param input  新正文与版本号
     * @return 修改后的评论视图
     */
    @Override
    public ReviewDiscussionVO revise(Long userId, String id, UpdateDiscussionRequest input) {
        CommentServiceImpl.requireUuid(id);
        if (input == null) {
            throw bad("修改参数不能为空");
        }
        String content = normalizeContent(input.content());
        ReviewDiscussionDO row = requireRow(id);
        requireAuthor(row, userId);
        if (row.status() != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该评论已不可操作");
        }
        // 内容未变化时直接返回，不递增版本，避免无意义的写库。
        if (!content.equals(row.content())) {
            int updated = discussions.updateContent(id, userId, input.version(), content);
            if (updated != 1) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "评论已变更，请刷新后重试");
            }
            row = requireRow(id);
        }
        return view(row, avatar(row.authorId()), parentAuthorName(row), replyCount(row), List.of(), userId);
    }

    /**
     * 撤回自己的评论。
     *
     * @param userId 当前登录用户编号
     * @param id     公开评论标识
     */
    @Override
    public void revoke(Long userId, String id) {
        CommentServiceImpl.requireUuid(id);
        ReviewDiscussionDO row = requireRow(id);
        requireAuthor(row, userId);
        // 重复撤回幂等成功；已屏蔽的评论不允许作者用撤回覆盖屏蔽记录。
        if (row.status() == 2) {
            return;
        }
        if (row.status() != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该评论已不可操作");
        }
        if (discussions.revoke(id, userId) != 1) {
            ReviewDiscussionDO latest = discussions.byId(id);
            if (latest != null && latest.status() == 2) {
                return;
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "该评论已变更，请刷新后重试");
        }
    }

    /**
     * 管理员屏蔽评论。
     *
     * <p>只做状态置位，不删除正文；重复屏蔽幂等成功。屏蔽不产生通知。</p>
     *
     * @param userId 当前登录用户编号
     * @param roles  角色编码集合
     * @param id     公开评论标识
     */
    @Override
    public void hide(Long userId, List<String> roles, String id) {
        CommentServiceImpl.requireUuid(id);
        ReviewDiscussionDO row = requireRow(id);
        if (!isManager(roles)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "没有屏蔽评论的权限");
        }
        // 系统管理员与总经理覆盖全平台，其余管理角色必须在其数据范围内。
        if (!isPlatformManager(roles) && !authority.coversWindow(userId, row.windowId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "没有管理该档口评价的权限");
        }
        if (row.status() == 3) {
            return;
        }
        if (row.status() != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该评论已不可操作");
        }
        if (discussions.hide(id) != 1) {
            ReviewDiscussionDO latest = discussions.byId(id);
            if (latest != null && latest.status() == 3) {
                return;
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "该评论已变更，请刷新后重试");
        }
        log.info("管理员屏蔽评论，operatorId={}, discussionId={}, windowId={}", userId, id, row.windowId());
    }

    /**
     * 分页读取顶层评论。
     *
     * @param type     评价类型
     * @param reviewId 评价公开标识
     * @param cursor   游标
     * @param limit    每页条数
     * @param viewerId 当前登录用户编号
     * @return 顶层评论分页
     */
    @Override
    public ReviewDiscussionPageVO pageRoots(ReviewType type, String reviewId, String cursor, int limit, Long viewerId) {
        if (limit < 1 || limit > 50) {
            throw bad("分页参数不正确");
        }
        // 这里只校验评价仍可见；分页本身不需要评价的档口与作者信息。
        requireTarget(type, reviewId);
        requireRootCursor(type, reviewId, cursor);

        List<ReviewDiscussionDO> found = discussions.pageRoots(type.code(), reviewId, cursor, limit + 1);
        boolean more = found.size() > limit;
        List<ReviewDiscussionDO> visible = more ? found.subList(0, limit) : found;
        if (visible.isEmpty()) {
            return new ReviewDiscussionPageVO(List.of(), null, false);
        }

        // 一次解析楼层作者头像；预览回复作者稍后合并到同一次批量查询，避免逐条远程调用。
        List<Long> userIds = new ArrayList<>();
        for (ReviewDiscussionDO row : visible) {
            userIds.add(row.authorId());
        }
        List<String> rootIds = new ArrayList<>();
        for (ReviewDiscussionDO row : visible) {
            rootIds.add(row.discussionId());
        }
        List<ReviewDiscussionDO> previewReplies = discussions.previewRepliesByRoots(rootIds, PREVIEW_LIMIT);
        for (ReviewDiscussionDO reply : previewReplies) {
            userIds.add(reply.authorId());
        }
        Map<Long, String> avatars = resolveAvatars(userIds);

        Map<String, Integer> replyCounts = new HashMap<>();
        List<ReviewDiscussionMapper.RootCount> counts = discussions.countRepliesByRoots(rootIds);
        for (ReviewDiscussionMapper.RootCount count : counts) {
            replyCounts.put(count.rootId(), count.total());
        }
        Map<String, List<ReviewDiscussionDO>> groupedReplies = new HashMap<>();
        for (ReviewDiscussionDO reply : previewReplies) {
            groupedReplies.computeIfAbsent(reply.rootId(), key -> new ArrayList<>()).add(reply);
        }

        List<ReviewDiscussionVO> items = new ArrayList<>();
        for (ReviewDiscussionDO row : visible) {
            List<ReviewDiscussionDO> replyRows = groupedReplies.get(row.discussionId());
            List<ReviewDiscussionVO> replies = new ArrayList<>();
            if (replyRows != null) {
                for (ReviewDiscussionDO reply : replyRows) {
                    replies.add(view(reply, avatars.getOrDefault(reply.authorId(), ""), null, 0, List.of(), viewerId));
                }
            }
            items.add(view(row, avatars.getOrDefault(row.authorId(), ""), null,
                    replyCounts.getOrDefault(row.discussionId(), 0), replies, viewerId));
        }
        String nextCursor = more ? items.get(items.size() - 1).discussionId() : null;
        return new ReviewDiscussionPageVO(items, nextCursor, more);
    }

    /**
     * 分页读取楼层内回复。
     *
     * @param discussionId 楼层根或楼内任意一条评论的公开标识
     * @param cursor       游标
     * @param limit        每页条数
     * @param viewerId     当前登录用户编号
     * @return 楼层回复分页
     */
    @Override
    public ReviewDiscussionPageVO pageReplies(String discussionId, String cursor, int limit, Long viewerId) {
        if (limit < 1 || limit > 100) {
            throw bad("分页参数不正确");
        }
        CommentServiceImpl.requireUuid(discussionId);
        ReviewDiscussionDO anchor = discussions.byId(discussionId);
        if (anchor == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        ReviewDiscussionDO root = anchor.depth() == 1 ? anchor : discussions.byId(anchor.rootId());
        if (root == null || root.status() != 1) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        requireReplyCursor(root.discussionId(), cursor);

        List<ReviewDiscussionDO> found = discussions.pageReplies(root.discussionId(), cursor, limit + 1);
        boolean more = found.size() > limit;
        List<ReviewDiscussionDO> visible = more ? found.subList(0, limit) : found;
        if (visible.isEmpty()) {
            return new ReviewDiscussionPageVO(List.of(), null, false);
        }

        List<Long> userIds = new ArrayList<>();
        List<String> parentIds = new ArrayList<>();
        for (ReviewDiscussionDO row : visible) {
            userIds.add(row.authorId());
            if (row.parentId() != null && !parentIds.contains(row.parentId())) {
                parentIds.add(row.parentId());
            }
        }
        Map<Long, String> avatars = resolveAvatars(userIds);
        Map<String, String> parentNames = new HashMap<>();
        if (!parentIds.isEmpty()) {
            List<ReviewDiscussionMapper.ParentRef> refs = discussions.findParentRefs(parentIds);
            for (ReviewDiscussionMapper.ParentRef ref : refs) {
                // 父评论已撤回或屏蔽时不给真实名称，避免通过"回复某某"反推出已隐藏内容。
                parentNames.put(ref.discussionId(), ref.status() == 1 ? ref.authorName() : "已删除的评论");
            }
        }

        List<ReviewDiscussionVO> items = new ArrayList<>();
        for (ReviewDiscussionDO row : visible) {
            items.add(view(row, avatars.getOrDefault(row.authorId(), ""), parentNames.get(row.parentId()),
                    0, List.of(), viewerId));
        }
        String nextCursor = more ? items.get(items.size() - 1).discussionId() : null;
        return new ReviewDiscussionPageVO(items, nextCursor, more);
    }

    /**
     * 批量汇总评价的评论数与预览。
     *
     * @param type      评价类型
     * @param reviewIds 评价公开标识集合
     * @param viewerId  当前登录用户编号
     * @return 评价标识到评论汇总的映射
     */
    @Override
    public Map<String, DiscussionSummary> summarize(ReviewType type, List<String> reviewIds, Long viewerId) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }
        List<String> distinct = new ArrayList<>();
        for (String id : reviewIds) {
            if (id != null && !id.isBlank() && !distinct.contains(id)) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> counts = new HashMap<>();
        List<ReviewDiscussionMapper.ReviewCount> countRows = discussions.countRootsByReviews(type.code(), distinct);
        for (ReviewDiscussionMapper.ReviewCount row : countRows) {
            counts.put(row.reviewId(), row.total());
        }
        List<ReviewDiscussionDO> previewRows = discussions.previewRoots(type.code(), distinct, PREVIEW_LIMIT);

        List<Long> userIds = new ArrayList<>();
        for (ReviewDiscussionDO row : previewRows) {
            userIds.add(row.authorId());
        }
        Map<Long, String> avatars = resolveAvatars(userIds);

        Map<String, List<ReviewDiscussionVO>> grouped = new LinkedHashMap<>();
        for (ReviewDiscussionDO row : previewRows) {
            List<ReviewDiscussionVO> list = grouped.computeIfAbsent(row.reviewId(), key -> new ArrayList<>());
            list.add(view(row, avatars.getOrDefault(row.authorId(), ""), null, 0, List.of(), viewerId));
        }

        Map<String, DiscussionSummary> result = new HashMap<>();
        for (String reviewId : distinct) {
            int count = counts.getOrDefault(reviewId, 0);
            List<ReviewDiscussionVO> previews = grouped.get(reviewId);
            if (count > 0 || previews != null) {
                result.put(reviewId, new DiscussionSummary(count, previews == null ? List.of() : previews));
            }
        }
        return result;
    }

    /**
     * 拼装单条评论的公开视图。
     *
     * @param row              评论行
     * @param avatarUrl        作者头像
     * @param parentAuthorName 被回复人展示名，可为空
     * @param replyCount       楼层回复数
     * @param replies          楼层预览回复
     * @param viewerId         当前登录用户编号
     * @return 公开视图
     */
    private ReviewDiscussionVO view(ReviewDiscussionDO row, String avatarUrl, String parentAuthorName,
                                    int replyCount, List<ReviewDiscussionVO> replies, Long viewerId) {
        boolean mine = viewerId != null && viewerId.equals(row.authorId());
        boolean official = !RoleCode.CUSTOMER.getCode().equals(row.authorRole());
        return new ReviewDiscussionVO(row.discussionId(), row.reviewType(), row.reviewId(), row.rootId(),
                row.parentId(), row.depth(), row.authorName(), avatarUrl, row.authorRole(), official,
                parentAuthorName, row.content(), row.version(), row.createdAt(), row.updatedAt(),
                replyCount, replies, mine);
    }

    /**
     * 幂等重放：原记录仍发布且内容一致时返回原视图，否则返回冲突。
     *
     * @param row     已有评论
     * @param content 本次归一化后的正文
     * @param viewerId 当前用户编号
     * @return 原评论视图
     */
    private ReviewDiscussionVO replay(ReviewDiscussionDO row, String content, Long viewerId) {
        if (row.status() != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该请求已用于已撤回或不可用的评论，请重新发起");
        }
        if (!content.equals(row.content())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请求标识已用于其他评论内容");
        }
        return view(row, avatar(row.authorId()), parentAuthorName(row), replyCount(row), List.of(), viewerId);
    }

    /**
     * 确认评价存在且可见，返回评论需要的档口、准入目标与作者。
     *
     * @param type     评价类型
     * @param reviewId 评价公开标识
     * @return 评价目标信息
     */
    private ReviewTarget requireTarget(ReviewType type, String reviewId) {
        if (type == null) {
            throw bad("评价类型不正确");
        }
        if (type == ReviewType.COMMENT) {
            CommentRowDO row = comments.byId(reviewId);
            if (row == null) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND, "评价不存在");
            }
            return new ReviewTarget(row.windowId(), row.productId(), row.userId());
        }
        WindowReviewMapper.Row row = windowReviews.byId(reviewId);
        if (row == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评价不存在");
        }
        return new ReviewTarget(row.windowId(), row.windowId(), row.userId());
    }

    /**
     * 读取评论，不存在时返回 404。
     *
     * @param id 公开评论标识
     * @return 评论行
     */
    private ReviewDiscussionDO requireRow(String id) {
        ReviewDiscussionDO row = discussions.byId(id);
        if (row == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        return row;
    }

    /**
     * 校验操作者是评论作者。
     *
     * @param row    评论行
     * @param userId 当前用户编号
     */
    private void requireAuthor(ReviewDiscussionDO row, Long userId) {
        if (userId == null || !userId.equals(row.authorId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能操作自己的评论");
        }
    }

    /**
     * 校验顶层评论游标属于当前评价且仍可见。
     *
     * @param type     评价类型
     * @param reviewId 评价公开标识
     * @param cursor   游标
     */
    private void requireRootCursor(ReviewType type, String reviewId, String cursor) {
        if (cursor == null) {
            return;
        }
        CommentServiceImpl.requireUuid(cursor);
        ReviewDiscussionDO row = discussions.byId(cursor);
        if (row == null || row.depth() != 1 || row.status() != 1
                || !type.code().equals(row.reviewType()) || !reviewId.equals(row.reviewId())) {
            throw bad("分页游标无效");
        }
    }

    /**
     * 校验楼层回复游标属于当前楼层且仍可见。
     *
     * @param rootId 楼层根评论标识
     * @param cursor 游标
     */
    private void requireReplyCursor(String rootId, String cursor) {
        if (cursor == null) {
            return;
        }
        CommentServiceImpl.requireUuid(cursor);
        ReviewDiscussionDO row = discussions.byId(cursor);
        if (row == null || row.depth() != 2 || row.status() != 1 || !rootId.equals(row.rootId())) {
            throw bad("分页游标无效");
        }
    }

    /**
     * 读取单个用户头像。
     *
     * @param userId 用户编号
     * @return 头像地址，缺失时为空串
     */
    private String avatar(Long userId) {
        return resolveAvatars(List.of(userId)).getOrDefault(userId, "");
    }

    /**
     * 批量解析头像。
     *
     * <p>空集合直接返回空映射，不发起远程调用；失败由实现方兜底为空串，
     * 不影响评论主体展示。</p>
     *
     * @param userIds 用户编号集合
     * @return 用户编号到头像地址的映射
     */
    private Map<Long, String> resolveAvatars(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return authors.resolve(userIds);
    }

    /**
     * 读取父评论的展示名。
     *
     * @param row 评论行
     * @return 被回复人展示名；顶层评论或父评论不可见时返回 null 或占位文案
     */
    private String parentAuthorName(ReviewDiscussionDO row) {
        if (row.parentId() == null) {
            return null;
        }
        ReviewDiscussionDO parent = discussions.byId(row.parentId());
        if (parent == null) {
            return "已删除的评论";
        }
        return parent.status() == 1 ? parent.authorName() : "已删除的评论";
    }

    /**
     * 读取楼层下的可见回复数。
     *
     * @param row 评论行
     * @return 楼层回复数；非顶层评论返回 0
     */
    private int replyCount(ReviewDiscussionDO row) {
        if (row.depth() != 1) {
            return 0;
        }
        List<ReviewDiscussionMapper.RootCount> counts = discussions.countRepliesByRoots(List.of(row.discussionId()));
        if (counts.isEmpty()) {
            return 0;
        }
        return counts.get(0).total();
    }

    /**
     * 归一化并校验正文。
     *
     * @param input 请求体
     * @return 去除首尾空白后的正文
     */
    private String normalizeContent(CreateDiscussionRequest input) {
        if (input == null || input.content() == null) {
            throw bad("评论内容不能为空");
        }
        return normalizeContent(input.content());
    }

    /**
     * 归一化并校验正文。
     *
     * @param raw 原始正文
     * @return 去除首尾空白后的正文
     */
    private String normalizeContent(String raw) {
        if (raw == null) {
            throw bad("评论内容不能为空");
        }
        String content = raw.trim();
        if (content.isEmpty()) {
            throw bad("评论内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw bad("评论内容不能超过500字");
        }
        return content;
    }

    /**
     * 生成展示名快照。
     *
     * @param authorName 令牌中的用户名
     * @return 截断后的展示名
     */
    private String displayName(String authorName) {
        if (authorName == null || authorName.isBlank()) {
            return DEFAULT_NAME;
        }
        return authorName.substring(0, Math.min(MAX_NAME_LENGTH, authorName.length()));
    }

    /**
     * 从角色集合中选出用于快照的角色编码。
     *
     * <p>多角色时按管理权限从高到低取第一个，保证商家身份不会被 CUSTOMER 覆盖；
     * 缺失角色按普通顾客处理。</p>
     *
     * @param roles 角色编码集合
     * @return 角色编码
     */
    private String roleOf(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return RoleCode.CUSTOMER.getCode();
        }
        if (roles.contains(RoleCode.SYSTEM_ADMIN.getCode())) {
            return RoleCode.SYSTEM_ADMIN.getCode();
        }
        if (roles.contains(RoleCode.GENERAL_MANAGER.getCode())) {
            return RoleCode.GENERAL_MANAGER.getCode();
        }
        if (roles.contains(RoleCode.RESTAURANT_MANAGER.getCode())) {
            return RoleCode.RESTAURANT_MANAGER.getCode();
        }
        if (roles.contains(RoleCode.STALL_MANAGER.getCode())) {
            return RoleCode.STALL_MANAGER.getCode();
        }
        return RoleCode.CUSTOMER.getCode();
    }

    /**
     * 判断是否属于管理角色。
     *
     * @param roles 角色编码集合
     * @return 属于任一管理角色时返回 true
     */
    private boolean isManager(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.contains(RoleCode.STALL_MANAGER.getCode())
                || roles.contains(RoleCode.RESTAURANT_MANAGER.getCode())
                || roles.contains(RoleCode.GENERAL_MANAGER.getCode())
                || roles.contains(RoleCode.SYSTEM_ADMIN.getCode());
    }

    /**
     * 判断是否属于覆盖全平台的管理角色。
     *
     * @param roles 角色编码集合
     * @return 系统管理员或总经理返回 true
     */
    private boolean isPlatformManager(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.contains(RoleCode.SYSTEM_ADMIN.getCode())
                || roles.contains(RoleCode.GENERAL_MANAGER.getCode());
    }

    /**
     * 发送评论通知。
     *
     * <p>自己评论自己不通知；通知失败只记日志，不影响已提交的评论。
     * 事件号带版本号，同一版本重试由通知表唯一键去重。</p>
     *
     * @param recipientId 接收人内部用户编号
     * @param type        评价类型
     * @param reviewId    评价公开标识
     * @param row         已提交的评论
     */
    private void notifyRecipient(Long recipientId, ReviewType type, String reviewId, ReviewDiscussionDO row) {
        if (recipientId == null || recipientId.equals(row.authorId())) {
            return;
        }
        boolean official = !RoleCode.CUSTOMER.getCode().equals(row.authorRole());
        String action = row.depth() == 1 ? "评论了你的评价" : "回复了你的评论";
        String title = (official ? "商家" : "有人") + action;
        String content = row.authorName() + "：" + row.content();
        String eventId = "review-discussion:" + row.discussionId() + ":v" + row.version();
        try {
            ApiResponse<Void> response = notifications.sendReviewDiscussion(internalToken,
                    new ReviewDiscussionNotificationRequest(recipientId, eventId, title, content,
                            type.code(), reviewId, row.discussionId()));
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
                log.warn("评论通知发送失败，discussionId={}, recipientId={}", row.discussionId(), recipientId);
            }
        } catch (RuntimeException exception) {
            // 通知属于最终一致环节，失败不回滚评论，也不向用户报错。
            log.warn("评论通知调用异常，discussionId={}, recipientId={}", row.discussionId(), recipientId, exception);
        }
    }

    /**
     * 构造 400 业务异常。
     *
     * @param message 提示文案
     * @return 业务异常
     */
    private static BusinessException bad(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }

    /**
     * 评价目标信息。
     *
     * @param windowId          评价所属档口编号
     * @param purchaseTargetId  消费准入查询使用的目标编号：菜品评价取菜品，店铺评价取档口
     * @param ownerId           评价作者内部用户编号，通知接收人
     */
    private record ReviewTarget(Long windowId, Long purchaseTargetId, Long ownerId) {
    }
}
