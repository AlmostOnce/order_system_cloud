package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.service.HomeAnnouncementService;
import com.hbue.ordering.product.vo.HomeAnnouncementVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端首页公告接口。
 *
 * <p>与商品菜单接口一致，只放行顾客角色；返回内容由服务端按状态和时间窗口筛选，
 * 客户端不需要传餐厅、分页或筛选参数。没有可展示公告时 data 为 null，
 * 首页据此隐藏公告条。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@RestController
@Tag(name = "首页公告接口")
@RequestMapping("/products")
@PreAuthorize("hasRole('CUSTOMER')")
public class HomeAnnouncementController {

    /**
     * 首页公告业务接口。
     */
    private final HomeAnnouncementService homeAnnouncementService;

    /**
     * 创建首页公告 Controller。
     *
     * @param homeAnnouncementService 首页公告业务接口
     */
    public HomeAnnouncementController(HomeAnnouncementService homeAnnouncementService) {
        this.homeAnnouncementService = homeAnnouncementService;
    }

    /**
     * 查询当前应展示的首页公告。
     *
     * <p>只返回一条最新生效公告；没有生效公告时返回 data = null，
     * 不使用空数组或占位对象，避免客户端把空内容当成一条真实公告。</p>
     *
     * @return 当前公告，没有时 data 为 null
     */
    @GetMapping("/announcements/home")
    public ApiResponse<HomeAnnouncementVO> current() {
        return ApiResponse.success(homeAnnouncementService.currentForHome());
    }
}
