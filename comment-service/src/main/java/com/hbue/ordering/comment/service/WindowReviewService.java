package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import com.hbue.ordering.comment.dto.UpdateWindowReviewRequest;
import com.hbue.ordering.comment.vo.WindowReviewPageVO;
import com.hbue.ordering.comment.vo.WindowReviewVO;

import java.util.List;

/**
 * 店铺评价的创建、查询、修改、删除与汇总。
 *
 * <p>创建前必须验证"确实买过"：用调用方原始 Bearer 凭据向订单服务确认该用户在该店铺有已完成订单，
 * 评论服务不自行判定消费资格。幂等键由客户端提供并做长度与字符集校验，同键不同内容不会被静默覆盖。</p>
 *
 * <p>修改与删除在服务层做细粒度鉴权：作者本人、系统管理员，或数据范围覆盖该档口的管理角色。
 * 删除为软删除，记录保留但不再出现在列表、评分与汇总中。</p>
 *
 * <p>列表使用有界游标分页，评分筛选只允许 1 或 5（前端只提供好评/差评两档），
 * 非法筛选值直接返回参数错误而不是退化成全量查询。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
public interface WindowReviewService {

    /**
     * 创建一条店铺评价。
     *
     * @param userId  当前登录用户编号
     * @param author  展示用作者名快照
     * @param bearer  原始 Authorization 头，用于校验消费资格
     * @param key     客户端幂等键
     * @param request 评分、内容与图片
     * @return 新建评价视图
     */
    WindowReviewVO create(Long userId, String author, String bearer, String key, CreateWindowReviewRequest request);

    /**
     * 查询某订单对当前店铺的已有评价，供"去评价"入口回显。
     *
     * @param userId  当前登录用户编号
     * @param orderId 订单主键
     * @return 评价视图；未评价时由实现返回空态
     */
    WindowReviewVO forOrder(Long userId, Long orderId);

    /**
     * score为空查询全部，只允许1或5；hasImages=true仅查有图，采用有界游标分页，用户身份由认证主体提供。
     *
     * @param userId   非空时只查该用户的评价
     * @param windowId 非空时只查该档口的评价
     * @param cursor   游标
     * @param limit    每页条数
     * @param score    评分筛选
     * @param hasImages 是否只看带图
     * @return 评价分页结果
     */
    WindowReviewPageVO page(Long userId, Long windowId, String cursor, int limit, Integer score, Boolean hasImages);

    /**
     * 修改一条已发布的店铺评价。
     *
     * <p>不改变所评价的订单与档口，只允许改评分、正文、图片与标签；评分变化会立即反映到
     * 档口评分与概览统计中。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param reviewId   公开评价标识
     * @param request    修改参数
     * @return 修改后的评价视图
     */
    WindowReviewVO revise(Long operatorId, List<String> roles, String reviewId, UpdateWindowReviewRequest request);

    /**
     * 删除一条已发布的店铺评价（软删除）。
     *
     * <p>删除后立即从列表、评分与汇总中消失，数据库保留原记录以便追责；
     * 由于存在"一个订单只能评价一次"的唯一键，删除后不能再对同一订单重新评价。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param reviewId   公开评价标识
     */
    void remove(Long operatorId, List<String> roles, String reviewId);

    /**
     * 查询档口评分统计。
     *
     * @param windowId 档口编号
     * @return 评分统计
     */
    com.hbue.ordering.comment.vo.WindowRatingVO rating(Long windowId);

    /**
     * 返回目标全部可见评价统计，不受当前页或当前筛选影响。
     *
     * @param windowId 档口编号
     * @return 评价概览
     */
    com.hbue.ordering.comment.vo.ReviewSummaryVO summary(Long windowId);
}
