package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.dto.request.HomeAnnouncementSaveRequest;
import com.hbue.ordering.product.service.HomeAnnouncementService;
import com.hbue.ordering.product.vo.HomeAnnouncementAdminVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页公告管理接口。
 *
 * <p>只放行总经理与系统管理员：公告是全平台内容，不属于餐厅经理的数据范围，
 * 因此这里不使用餐厅数据范围校验，也不能通过前端隐藏入口代替服务端门禁。
 * 每次写操作都从 JWT 取出操作者编号写入审计字段，客户端不能指定发布人。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Validated
@RestController
@Tag(name = "首页公告管理接口")
@RequestMapping("/products/admin/announcements")
@PreAuthorize("hasAnyRole('GENERAL_MANAGER','SYSTEM_ADMIN')")
public class HomeAnnouncementAdministrationController {

    /**
     * 首页公告业务接口。
     */
    private final HomeAnnouncementService homeAnnouncementService;

    /**
     * 创建公告管理 Controller。
     *
     * @param homeAnnouncementService 首页公告业务接口
     */
    public HomeAnnouncementAdministrationController(
            HomeAnnouncementService homeAnnouncementService
    ) {
        this.homeAnnouncementService = homeAnnouncementService;
    }

    /**
     * 查询全部公告，包括草稿与已下线记录。
     *
     * @return 按修改时间倒序排列的公告列表
     */
    @GetMapping
    public ApiResponse<List<HomeAnnouncementAdminVO>> list() {
        return ApiResponse.success(homeAnnouncementService.listForAdmin());
    }

    /**
     * 新建公告，初始状态为草稿。
     *
     * @param jwt     当前登录操作者 JWT
     * @param request 标题、正文与生效窗口
     * @return 新建后的公告
     */
    @PostMapping
    public ApiResponse<HomeAnnouncementAdminVO> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody HomeAnnouncementSaveRequest request
    ) {
        return ApiResponse.success(
                homeAnnouncementService.create(operatorId(jwt), request)
        );
    }

    /**
     * 更新公告内容与生效窗口。
     *
     * @param jwt            当前登录操作者 JWT
     * @param announcementId 公告编号
     * @param request        标题、正文与生效窗口
     * @return 更新后的公告
     */
    @PutMapping("/{announcementId}")
    public ApiResponse<HomeAnnouncementAdminVO> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("announcementId") @Positive Long announcementId,
            @Valid @RequestBody HomeAnnouncementSaveRequest request
    ) {
        return ApiResponse.success(
                homeAnnouncementService.update(
                        operatorId(jwt),
                        announcementId,
                        request
                )
        );
    }

    /**
     * 发布公告；开始时间未到则发布后仍不展示，到点自然生效。
     *
     * @param jwt            当前登录操作者 JWT
     * @param announcementId 公告编号
     * @return 发布后的公告
     */
    @PostMapping("/{announcementId}/publish")
    public ApiResponse<HomeAnnouncementAdminVO> publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("announcementId") @Positive Long announcementId
    ) {
        return ApiResponse.success(
                homeAnnouncementService.publish(operatorId(jwt), announcementId)
        );
    }

    /**
     * 下线公告，使其立即从学生端首页消失；记录保留，可重新发布。
     *
     * @param jwt            当前登录操作者 JWT
     * @param announcementId 公告编号
     * @return 下线后的公告
     */
    @PostMapping("/{announcementId}/offline")
    public ApiResponse<HomeAnnouncementAdminVO> offline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("announcementId") @Positive Long announcementId
    ) {
        return ApiResponse.success(
                homeAnnouncementService.offline(operatorId(jwt), announcementId)
        );
    }

    /**
     * 从 JWT 中取出当前操作者用户编号，仅用于审计字段。
     *
     * @param jwt 当前登录操作者 JWT
     * @return 操作者用户编号
     */
    private Long operatorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
