package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.dto.UpdateCommentRequest;
import com.hbue.ordering.comment.vo.CommentPageVO;
import com.hbue.ordering.comment.vo.CommentVO;

import java.util.List;

/**
 * 菜品评价的业务边界，供评论控制器调用。
 *
 * <p>查询类方法按公开范围或本人范围返回；写操作（创建、修改、删除）都在服务层做
 * 细粒度鉴权：创建要求已完成的购买事实，修改与删除要求"作者本人、系统管理员或
 * 数据范围覆盖该档口的管理角色"。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
public interface CommentService {

    /**
     * 创建菜品评价。
     *
     * @param userId     当前登录用户编号
     * @param authorName 作者昵称快照
     * @param bearer     原始 Authorization 头，用于向订单服务确认消费资格
     * @param key        客户端幂等键
     * @param input      评分、正文、图片与标签
     * @return 新建评价视图
     */
    CommentVO create(Long userId, String authorName, String bearer, String key, CreateCommentRequest input);

    /**
     * 查询某订单下本人已提交的菜品评价。
     *
     * @param userId  当前登录用户编号
     * @param orderId 订单主键
     * @return 评价视图列表，按提交顺序
     */
    List<CommentVO> forOrder(Long userId, Long orderId);

    /**
     * 查询单条评价详情。
     *
     * @param id 公开评价标识
     * @return 评价视图
     */
    CommentVO detail(String id);

    /**
     * score为空查询全部，只允许1或5；hasImages=true仅查有图，采用有界游标分页，用户身份由认证主体提供。
     *
     * @param userId    非空时只查该用户的评价
     * @param productId 非空时只查该菜品的评价
     * @param before    游标
     * @param limit     每页条数
     * @param score     评分筛选
     * @param hasImages 是否只看带图
     * @return 评价分页结果
     */
    CommentPageVO page(Long userId, Long productId, String before, int limit, Integer score, Boolean hasImages);

    /**
     * 修改一条已发布的菜品评价。
     *
     * <p>不改变评价对象（订单、菜品、档口），只允许改评分、正文、图片与标签。
     * 评分变化会同步调整菜品评分汇总并向下游推送新快照。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param id         公开评价标识
     * @param input      修改参数
     * @return 修改后的评价视图
     */
    CommentVO revise(Long operatorId, List<String> roles, String id, UpdateCommentRequest input);

    /**
     * 删除一条已发布的菜品评价（软删除）。
     *
     * <p>删除后评价立即从列表、评分与汇总中消失，数据库保留原记录以便追责；
     * 扣减评分汇总时若该菜品已无其它评价，会推送零值快照让评分投影回到"暂无评分"。</p>
     *
     * @param operatorId 当前登录用户编号
     * @param roles      当前登录用户的角色编码集合
     * @param id         公开评价标识
     */
    void remove(Long operatorId, List<String> roles, String id);

    /**
     * 返回目标全部可见评价统计，不受当前页或当前筛选影响。
     *
     * @param productId 菜品编号
     * @return 评价概览
     */
    com.hbue.ordering.comment.vo.ReviewSummaryVO summary(Long productId);
}
