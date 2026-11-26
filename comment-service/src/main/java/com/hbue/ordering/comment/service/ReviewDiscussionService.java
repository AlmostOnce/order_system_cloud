package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.dto.CreateDiscussionRequest;
import com.hbue.ordering.comment.dto.UpdateDiscussionRequest;
import com.hbue.ordering.comment.model.ReviewType;
import com.hbue.ordering.comment.vo.ReviewDiscussionPageVO;
import com.hbue.ordering.comment.vo.ReviewDiscussionVO;

import java.util.List;

/**
 * 评价自由评论区的业务边界。
 *
 * <p>一条评价对应任意多条评论：顶层评论构成楼层，楼层内的回复可以继续对话，
 * 但不增加缩进层级。发表与回复要求"消费过"（管理角色豁免），编辑与撤回只认作者本人，
 * 屏蔽要求档口管理范围。</p>
 *
 * <p>评论不参与评分、排序、推荐与评分 MQ，写操作只落 {@code review_discussion} 单表。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
public interface ReviewDiscussionService {

    /**
     * 发表顶层评论。
     *
     * @param userId     当前登录用户编号
     * @param authorName 作者展示名快照
     * @param roles      当前登录用户的角色编码集合
     * @param type       评价类型
     * @param reviewId   评价公开标识
     * @param key        客户端幂等请求键
     * @param input      评论正文
     * @return 新建评论视图
     */
    ReviewDiscussionVO publish(Long userId, String authorName, List<String> roles, ReviewType type,
                               String reviewId, String key, CreateDiscussionRequest input);

    /**
     * 回复楼层内的某条评论。
     *
     * <p>{@code parentId} 可以是楼层根，也可以是楼内任意一条回复；服务端统一把
     * {@code rootId} 归到楼层根，深度固定为 2，回复条数不限。</p>
     *
     * @param userId     当前登录用户编号
     * @param authorName 作者展示名快照
     * @param roles      当前登录用户的角色编码集合
     * @param parentId   被回复评论的公开标识
     * @param key        客户端幂等请求键
     * @param input      评论正文
     * @return 新建回复视图
     */
    ReviewDiscussionVO reply(Long userId, String authorName, List<String> roles, String parentId,
                             String key, CreateDiscussionRequest input);

    /**
     * 编辑自己的评论。
     *
     * @param userId 当前登录用户编号
     * @param id     公开评论标识
     * @param input  新正文与客户端版本号
     * @return 修改后的评论视图
     */
    ReviewDiscussionVO revise(Long userId, String id, UpdateDiscussionRequest input);

    /**
     * 撤回自己的评论（软删除）。
     *
     * @param userId 当前登录用户编号
     * @param id     公开评论标识
     */
    void revoke(Long userId, String id);

    /**
     * 管理员屏蔽评论。
     *
     * @param userId 当前登录用户编号
     * @param roles  当前登录用户的角色编码集合
     * @param id     公开评论标识
     */
    void hide(Long userId, List<String> roles, String id);

    /**
     * 分页读取某条评价的顶层评论。
     *
     * @param type     评价类型
     * @param reviewId 评价公开标识
     * @param cursor   上一页最后一条的公开评论标识
     * @param limit    每页条数
     * @param viewerId 当前登录用户编号，用于计算"是否本人"
     * @return 顶层评论分页
     */
    ReviewDiscussionPageVO pageRoots(ReviewType type, String reviewId, String cursor, int limit, Long viewerId);

    /**
     * 分页读取某楼层内的回复。
     *
     * @param discussionId 楼层根或楼内任意一条评论的公开标识
     * @param cursor       上一页最后一条的公开评论标识
     * @param limit        每页条数
     * @param viewerId     当前登录用户编号，用于计算"是否本人"
     * @return 楼层回复分页
     */
    ReviewDiscussionPageVO pageReplies(String discussionId, String cursor, int limit, Long viewerId);

    /**
     * 批量汇总多条评价的评论数与预览，供评价列表一次拼装。
     *
     * @param type      评价类型
     * @param reviewIds 评价公开标识集合
     * @param viewerId  当前登录用户编号，可为空
     * @return 评价标识到汇总的映射；无评论的评价不出现在映射中
     */
    java.util.Map<String, DiscussionSummary> summarize(ReviewType type, List<String> reviewIds, Long viewerId);

    /**
     * 单条评价的评论汇总。
     *
     * @param count    顶层评论数
     * @param previews 最新若干条顶层评论
     */
    record DiscussionSummary(int count, List<ReviewDiscussionVO> previews) {
    }
}
