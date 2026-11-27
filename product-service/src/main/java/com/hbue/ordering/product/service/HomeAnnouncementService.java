package com.hbue.ordering.product.service;

import com.hbue.ordering.product.dto.request.HomeAnnouncementSaveRequest;
import com.hbue.ordering.product.vo.HomeAnnouncementAdminVO;
import com.hbue.ordering.product.vo.HomeAnnouncementVO;

import java.util.List;

/**
 * 首页公告业务接口。
 *
 * <p>学生端只读取"当前应展示的一条"公告；管理端负责草稿、发布、编辑和下线。
 * 是否生效完全由服务端按状态与时间窗口判断，客户端不做二次筛选。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
public interface HomeAnnouncementService {

    /**
     * 查询学生端首页当前应展示的公告。
     *
     * <p>筛选条件：已发布、开始时间已到、结束时间为空或尚未结束；多条同时满足时
     * 取发布时间最新的一条。没有任何满足条件的公告时返回 null，页面隐藏公告条。</p>
     *
     * @return 当前公告；没有可展示公告时返回 null
     */
    HomeAnnouncementVO currentForHome();

    /**
     * 查询管理端公告列表，包含草稿与已下线记录。
     *
     * <p>按修改时间倒序返回，最近维护的公告排在前面，便于运营找到刚编辑的记录。</p>
     *
     * @return 公告管理视图列表
     */
    List<HomeAnnouncementAdminVO> listForAdmin();

    /**
     * 新建公告，初始状态固定为草稿。
     *
     * @param operatorId 当前操作者用户编号，仅用于审计字段
     * @param request    标题、正文与生效窗口
     * @return 新建后的公告管理视图
     */
    HomeAnnouncementAdminVO create(Long operatorId, HomeAnnouncementSaveRequest request);

    /**
     * 更新公告内容与生效窗口。
     *
     * <p>已发布的公告被编辑后刷新发布时间，使修改后的内容重新成为首页展示的
     * "最新一条"；草稿与已下线公告只更新内容，不改变状态。</p>
     *
     * @param operatorId     当前操作者用户编号
     * @param announcementId 公告编号
     * @param request        标题、正文与生效窗口
     * @return 更新后的公告管理视图
     */
    HomeAnnouncementAdminVO update(
            Long operatorId,
            Long announcementId,
            HomeAnnouncementSaveRequest request
    );

    /**
     * 发布公告。
     *
     * <p>发布只改变状态与发布时间，是否真正展示仍由开始/结束时间决定，
     * 因此允许提前发布一条将来生效的公告。</p>
     *
     * @param operatorId     当前操作者用户编号
     * @param announcementId 公告编号
     * @return 发布后的公告管理视图
     */
    HomeAnnouncementAdminVO publish(Long operatorId, Long announcementId);

    /**
     * 下线公告，使其立即从学生端首页消失。
     *
     * <p>下线是可逆状态：记录保留，可再次发布；重复下线按幂等处理，不报错。</p>
     *
     * @param operatorId     当前操作者用户编号
     * @param announcementId 公告编号
     * @return 下线后的公告管理视图
     */
    HomeAnnouncementAdminVO offline(Long operatorId, Long announcementId);
}
