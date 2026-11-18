package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.FavoriteStatusRequest;
import com.hbue.ordering.user.model.FavoriteTargetType;
import com.hbue.ordering.user.service.UserFavoriteService;
import com.hbue.ordering.user.vo.FavoriteStatusVO;
import com.hbue.ordering.user.vo.UserFavoritePageVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 学生个人收藏 API。
 *
 * <p>所有接口均要求 CUSTOMER；当前用户只能从资源服务器已验签的 JWT `sub` 读取，
 * URL 与请求体均不接受 userId，防止越权查看或修改其他人的收藏。</p>
 */
@Validated
@RestController
@Tag(name = "学生收藏")
@RequestMapping("/users/me/favorites")
@PreAuthorize("hasRole('CUSTOMER')")
public class UserFavoriteController {

    /**
     * 收藏业务编排入口。
     */
    private final UserFavoriteService favoriteService;

    /**
     * 注入收藏关系服务，HTTP 层只负责身份/参数边界，不直接访问 Mapper。
     *
     * @param favoriteService 处理收藏幂等规则与查询的用例服务
     */
    public UserFavoriteController(UserFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * 收藏窗口或菜品；重复调用保持成功，客户端可安全重试。
     *
     * @param jwt        已认证的当前用户令牌
     * @param targetType 目标类型，仅 WINDOW 或 PRODUCT
     * @param targetId   商品服务中的窗口或菜品 ID
     * @return 无业务载荷的成功响应
     */
    @PutMapping("/{targetType}/{targetId}")
    public ApiResponse<Void> add(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable("targetType") FavoriteTargetType targetType,
                                 @PathVariable("targetId") @Positive Long targetId) {
        favoriteService.add(currentUserId(jwt), targetType, targetId);
        return ApiResponse.success(null);
    }

    /**
     * 取消收藏；记录不存在时仍成功，符合幂等删除语义。
     *
     * @param jwt        已认证的当前用户令牌
     * @param targetType 目标类型
     * @param targetId   商品服务中的窗口或菜品 ID
     * @return 无业务载荷的成功响应
     */
    @DeleteMapping("/{targetType}/{targetId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable("targetType") FavoriteTargetType targetType,
                                    @PathVariable("targetId") @Positive Long targetId) {
        favoriteService.remove(currentUserId(jwt), targetType, targetId);
        return ApiResponse.success(null);
    }

    /**
     * 批量读取当前页面中目标的收藏状态，避免在每张窗口或菜品卡片上单独请求。
     *
     * @param jwt     已认证的当前用户令牌
     * @param request 目标类型和最多100个目标 ID
     * @return 已收藏的目标 ID 列表
     */
    @PostMapping("/status")
    public ApiResponse<FavoriteStatusVO> statuses(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody FavoriteStatusRequest request) {
        return ApiResponse.success(favoriteService.findStatuses(
                currentUserId(jwt), request.targetType(), request.targetIds()));
    }

    /**
     * 按目标类型读取本人收藏，使用收藏 ID 游标按需加载较早记录。
     *
     * @param jwt        已认证的当前用户令牌
     * @param targetType WINDOW 或 PRODUCT
     * @param beforeId   上一页末条收藏 ID，首屏省略
     * @param limit      当前页数量，默认20、最大50
     * @return 当前页、下一游标与是否还有记录
     */
    @GetMapping
    public ApiResponse<UserFavoritePageVO> list(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam("targetType") FavoriteTargetType targetType,
                                                @RequestParam(name = "beforeId", required = false) @Positive Long beforeId,
                                                @RequestParam(name = "limit", defaultValue = "20")
                                                @Positive @Max(50) int limit) {
        return ApiResponse.success(favoriteService.list(
                currentUserId(jwt), targetType, beforeId, limit));
    }

    /**
     * 从可信 JWT 主体提取数据库用户主键，不允许客户端指定用户归属。
     *
     * @param jwt Spring Security 已验证的访问令牌
     * @return 与 user_db.users 主键一致的当前用户 ID
     */
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
