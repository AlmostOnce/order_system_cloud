package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.service.UserDirectoryService;
import com.hbue.ordering.user.vo.AdminUserPageVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端账号检索接口。
 *
 * <p>两个查询共享同一路径前缀但角色边界不同，因此使用方法级 {@code @PreAuthorize}：
 * 账号目录只交给系统管理员，数据范围候选只交给餐厅经理与总经理。服务层仍会
 * 按操作者的范围快照收敛候选集合，前端隐藏入口不作为安全边界。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
@Validated
@RestController
@Tag(name = "账号目录")
@RequestMapping("/users/admin")
public class UserDirectoryAdministrationController {

    /**
     * 管理端账号检索业务服务。
     */
    private final UserDirectoryService userDirectoryService;

    /**
     * 创建管理端账号检索接口。
     *
     * @param userDirectoryService 管理端账号检索业务服务
     */
    public UserDirectoryAdministrationController(
            UserDirectoryService userDirectoryService
    ) {
        this.userDirectoryService = userDirectoryService;
    }

    /**
     * 系统管理员检索账号目录。
     *
     * @param jwt     当前登录操作者 JWT
     * @param keyword 关键词，匹配用户名、脱敏邮箱或完整用户号
     * @param roleCode 角色编码筛选
     * @param status  状态筛选：1 启用、0 禁用
     * @param cursor  上一页返回的 nextCursor，首屏不传
     * @param size    每页条数
     * @return 游标分页的账号摘要
     */
    @GetMapping("/directory")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<AdminUserPageVO> directory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "roleCode", required = false) String roleCode,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.success(userDirectoryService.listDirectory(
                operatorId(jwt),
                keyword,
                roleCode,
                status,
                cursor,
                size
        ));
    }

    /**
     * 业务角色检索可授予数据范围的候选账号。
     *
     * @param jwt     当前登录操作者 JWT
     * @param keyword 关键词，匹配用户名、脱敏邮箱或完整用户号
     * @param cursor  上一页返回的 nextCursor，首屏不传
     * @param size    每页条数
     * @return 游标分页的候选账号摘要
     */
    @GetMapping("/scope-candidates")
    @PreAuthorize("hasAnyRole('RESTAURANT_MANAGER','GENERAL_MANAGER')")
    public ApiResponse<AdminUserPageVO> scopeCandidates(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.success(userDirectoryService.listScopeCandidates(
                operatorId(jwt),
                keyword,
                cursor,
                size
        ));
    }

    /**
     * 从已通过资源服务器校验的 JWT 中取出当前操作者 ID。
     *
     * @param jwt 当前登录操作者 JWT
     * @return 操作者用户 ID
     */
    private Long operatorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
