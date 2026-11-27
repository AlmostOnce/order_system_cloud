package com.hbue.ordering.comment.controller;

import com.hbue.ordering.comment.dto.CreateDiscussionRequest;
import com.hbue.ordering.comment.dto.UpdateDiscussionRequest;
import com.hbue.ordering.comment.model.ReviewType;
import com.hbue.ordering.comment.service.ReviewDiscussionService;
import com.hbue.ordering.comment.vo.ReviewDiscussionPageVO;
import com.hbue.ordering.comment.vo.ReviewDiscussionVO;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评价自由评论区接口。
 *
 * <p>菜品评价与店铺评价共用同一套评论能力，只有顶层评论的入口按评价类型分路径：
 * 菜品评价走 {@code /comments/{commentId}/discussions}，店铺评价走
 * {@code /comments/window-reviews/{reviewId}/discussions}。楼层回复、编辑、撤回、
 * 屏蔽与分页统一走 {@code /comments/discussions/**}。</p>
 *
 * <p>读取接口也要求登录：{@code /comments/**} 在安全配置中统一要求认证，
 * 当前用户编号用于计算 {@code mine} 字段，决定前端是否显示编辑入口。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@RestController
@RequestMapping("/comments")
@Validated
public class ReviewDiscussionController {

    /**
     * 评论业务服务。
     */
    private final ReviewDiscussionService discussions;

    /**
     * 创建评论区控制器。
     *
     * @param discussions 评论业务服务
     */
    public ReviewDiscussionController(ReviewDiscussionService discussions) {
        this.discussions = discussions;
    }

    /**
     * 对一条菜品评价发表顶层评论。
     *
     * @param jwt   当前用户令牌
     * @param key   客户端幂等请求键
     * @param id    菜品评价公开标识
     * @param body  评论正文
     * @return 新建评论
     */
    @PostMapping("/{commentId}/discussions")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<ReviewDiscussionVO> publishForComment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable("commentId") String id,
            @Valid @RequestBody CreateDiscussionRequest body) {
        return ApiResponse.success(discussions.publish(userId(jwt), authorName(jwt), roles(jwt),
                ReviewType.COMMENT, id, key, body));
    }

    /**
     * 对一条店铺评价发表顶层评论。
     *
     * @param jwt   当前用户令牌
     * @param key   客户端幂等请求键
     * @param id    店铺评价公开标识
     * @param body  评论正文
     * @return 新建评论
     */
    @PostMapping("/window-reviews/{reviewId}/discussions")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<ReviewDiscussionVO> publishForWindowReview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable("reviewId") String id,
            @Valid @RequestBody CreateDiscussionRequest body) {
        return ApiResponse.success(discussions.publish(userId(jwt), authorName(jwt), roles(jwt),
                ReviewType.WINDOW_REVIEW, id, key, body));
    }

    /**
     * 回复楼层内的某条评论。
     *
     * @param jwt   当前用户令牌
     * @param key   客户端幂等请求键
     * @param id    被回复评论的公开标识
     * @param body  回复正文
     * @return 新建回复
     */
    @PostMapping("/discussions/{discussionId}/replies")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<ReviewDiscussionVO> reply(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable("discussionId") String id,
            @Valid @RequestBody CreateDiscussionRequest body) {
        return ApiResponse.success(discussions.reply(userId(jwt), authorName(jwt), roles(jwt), id, key, body));
    }

    /**
     * 编辑自己的评论。
     *
     * @param jwt  当前用户令牌
     * @param id   公开评论标识
     * @param body 新正文与版本号
     * @return 修改后的评论
     */
    @PutMapping("/discussions/{discussionId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<ReviewDiscussionVO> revise(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("discussionId") String id,
            @Valid @RequestBody UpdateDiscussionRequest body) {
        return ApiResponse.success(discussions.revise(userId(jwt), id, body));
    }

    /**
     * 撤回自己的评论。
     *
     * @param jwt 当前用户令牌
     * @param id  公开评论标识
     * @return 成功响应
     */
    @DeleteMapping("/discussions/{discussionId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<Void> revoke(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("discussionId") String id) {
        discussions.revoke(userId(jwt), id);
        return ApiResponse.success(null);
    }

    /**
     * 管理员屏蔽评论。
     *
     * @param jwt 当前用户令牌
     * @param id  公开评论标识
     * @return 成功响应
     */
    @PostMapping("/discussions/{discussionId}/hide")
    @PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<Void> hide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("discussionId") String id) {
        discussions.hide(userId(jwt), roles(jwt), id);
        return ApiResponse.success(null);
    }

    /**
     * 分页读取菜品评价的顶层评论。
     *
     * @param jwt    当前用户令牌
     * @param id     菜品评价公开标识
     * @param cursor 游标
     * @param limit  每页条数
     * @return 顶层评论分页
     */
    @GetMapping("/{commentId}/discussions")
    public ApiResponse<ReviewDiscussionPageVO> pageForComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("commentId") String id,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResponse.success(discussions.pageRoots(ReviewType.COMMENT, id, cursor, limit, userId(jwt)));
    }

    /**
     * 分页读取店铺评价的顶层评论。
     *
     * @param jwt    当前用户令牌
     * @param id     店铺评价公开标识
     * @param cursor 游标
     * @param limit  每页条数
     * @return 顶层评论分页
     */
    @GetMapping("/window-reviews/{reviewId}/discussions")
    public ApiResponse<ReviewDiscussionPageVO> pageForWindowReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("reviewId") String id,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResponse.success(discussions.pageRoots(ReviewType.WINDOW_REVIEW, id, cursor, limit, userId(jwt)));
    }

    /**
     * 分页读取楼层内的回复。
     *
     * @param jwt    当前用户令牌
     * @param id     楼层根或楼内任意一条评论的公开标识
     * @param cursor 游标
     * @param limit  每页条数
     * @return 楼层回复分页
     */
    @GetMapping("/discussions/{discussionId}/replies")
    public ApiResponse<ReviewDiscussionPageVO> pageReplies(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("discussionId") String id,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ApiResponse.success(discussions.pageReplies(id, cursor, limit, userId(jwt)));
    }

    /**
     * 读取当前用户编号。
     *
     * @param jwt 当前用户令牌
     * @return 内部用户编号
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /**
     * 读取当前用户展示名。
     *
     * @param jwt 当前用户令牌
     * @return 用户名，可能为空
     */
    private static String authorName(Jwt jwt) {
        return jwt.getClaimAsString("username");
    }

    /**
     * 读取当前用户角色编码。
     *
     * <p>角色声明由认证服务签发并经资源服务器验签，属于服务端事实；
     * 缺失时返回空集合，由服务层按普通顾客处理。</p>
     *
     * @param jwt 当前用户令牌
     * @return 角色编码集合
     */
    private static List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
