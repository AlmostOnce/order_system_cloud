package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.product.dto.request.HomeAnnouncementSaveRequest;
import com.hbue.ordering.product.mapper.HomeAnnouncementMapper;
import com.hbue.ordering.product.model.HomeAnnouncementDO;
import com.hbue.ordering.product.service.HomeAnnouncementService;
import com.hbue.ordering.product.vo.HomeAnnouncementAdminVO;
import com.hbue.ordering.product.vo.HomeAnnouncementVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页公告业务实现。
 *
 * <p>公告是平台级内容，不存在餐厅数据范围；角色门禁由 Controller 的
 * {@code @PreAuthorize} 完成，本类只处理状态流转、时间窗口校验和读写。</p>
 *
 * <p>可见性规则集中在 {@link #currentForHome()}：状态必须为已发布，且当前时间落在
 * [startAt, endAt) 区间；endAt 为空表示长期有效。因此"到期自动消失"不需要定时任务，
 * 查询条件本身就会把过期记录过滤掉。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Service
public class HomeAnnouncementServiceImpl implements HomeAnnouncementService {

    /**
     * 草稿状态：可反复编辑，学生端不可见。
     */
    private static final int STATUS_DRAFT = 0;

    /**
     * 已发布状态：可见性再由开始/结束时间决定。
     */
    private static final int STATUS_PUBLISHED = 1;

    /**
     * 已下线状态：学生端不可见，记录保留，可重新发布。
     */
    private static final int STATUS_OFFLINE = 2;

    /**
     * 标题最大长度，与数据库 VARCHAR(60) 保持一致。
     */
    private static final int MAX_TITLE_LENGTH = 60;

    /**
     * 正文最大长度，与数据库 VARCHAR(500) 保持一致。
     */
    private static final int MAX_CONTENT_LENGTH = 500;

    /**
     * 公告数据 Mapper。
     */
    private final HomeAnnouncementMapper homeAnnouncementMapper;

    /**
     * 创建公告业务实现。
     *
     * @param homeAnnouncementMapper 公告数据 Mapper
     */
    public HomeAnnouncementServiceImpl(HomeAnnouncementMapper homeAnnouncementMapper) {
        this.homeAnnouncementMapper = homeAnnouncementMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HomeAnnouncementVO currentForHome() {
        // 以服务端时间作为唯一判定基准，避免客户端时钟偏差导致公告提前出现或滞留。
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<HomeAnnouncementDO> query = new QueryWrapper<>();
        query.eq("status", STATUS_PUBLISHED)
                .le("start_at", now)
                // 结束时间为空代表长期有效；非空时必须尚未到结束时间。
                .and(window -> window.isNull("end_at").or().gt("end_at", now))
                .orderByDesc("published_at")
                // 发布时间相同时用自增编号兜底，保证结果稳定，不出现随机取一条。
                .orderByDesc("announcement_id")
                // 首页只展示一条，直接让数据库限制返回行数，不先查全量再截断。
                .last("LIMIT 1");
        List<HomeAnnouncementDO> rows = homeAnnouncementMapper.selectList(query);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return toPublicVO(rows.get(0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<HomeAnnouncementAdminVO> listForAdmin() {
        QueryWrapper<HomeAnnouncementDO> query = new QueryWrapper<>();
        query.orderByDesc("updated_at").orderByDesc("announcement_id");
        List<HomeAnnouncementDO> rows = homeAnnouncementMapper.selectList(query);
        // 管理端列表规模很小；逐行转换为视图对象，不暴露持久化对象。
        List<HomeAnnouncementAdminVO> results = new ArrayList<>();
        if (rows != null) {
            for (HomeAnnouncementDO row : rows) {
                results.add(toAdminVO(row));
            }
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public HomeAnnouncementAdminVO create(
            Long operatorId,
            HomeAnnouncementSaveRequest request
    ) {
        requireRequest(request);
        String title = requireText(request.getTitle(), "公告标题", MAX_TITLE_LENGTH);
        String content = requireText(request.getContent(), "公告正文", MAX_CONTENT_LENGTH);
        validateWindow(request.getStartAt(), request.getEndAt());

        HomeAnnouncementDO announcement = new HomeAnnouncementDO();
        announcement.setTitle(title);
        announcement.setContent(content);
        // 新建一律进入草稿，发布必须走独立接口，审计上能区分"创建"和"对外发布"。
        announcement.setStatus(STATUS_DRAFT);
        announcement.setStartAt(request.getStartAt());
        announcement.setEndAt(request.getEndAt());
        announcement.setPublishedAt(null);
        announcement.setCreatedBy(operatorId);
        announcement.setUpdatedBy(operatorId);
        homeAnnouncementMapper.insert(announcement);
        return toAdminVO(requireAnnouncement(announcement.getAnnouncementId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public HomeAnnouncementAdminVO update(
            Long operatorId,
            Long announcementId,
            HomeAnnouncementSaveRequest request
    ) {
        validateId(announcementId);
        requireRequest(request);
        String title = requireText(request.getTitle(), "公告标题", MAX_TITLE_LENGTH);
        String content = requireText(request.getContent(), "公告正文", MAX_CONTENT_LENGTH);
        validateWindow(request.getStartAt(), request.getEndAt());
        HomeAnnouncementDO existing = requireAnnouncement(announcementId);

        UpdateWrapper<HomeAnnouncementDO> update = new UpdateWrapper<>();
        update.eq("announcement_id", announcementId)
                .set("title", title)
                .set("content", content)
                .set("start_at", request.getStartAt())
                // 置空表示改为长期有效；UpdateWrapper 会生成 end_at = NULL。
                .set("end_at", request.getEndAt())
                .set("updated_by", operatorId);
        // 已发布公告被编辑后刷新发布时间：让修正后的内容立即成为首页的"最新一条"，
        // 而不是继续展示旧内容直到下一条公告发布。
        if (Integer.valueOf(STATUS_PUBLISHED).equals(existing.getStatus())) {
            update.set("published_at", LocalDateTime.now());
        }
        homeAnnouncementMapper.update(null, update);
        return toAdminVO(requireAnnouncement(announcementId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public HomeAnnouncementAdminVO publish(Long operatorId, Long announcementId) {
        validateId(announcementId);
        HomeAnnouncementDO existing = requireAnnouncement(announcementId);
        // 复用与新建/编辑相同的窗口校验：历史数据或手工改库也可能留下非法区间。
        validateWindow(existing.getStartAt(), existing.getEndAt());

        UpdateWrapper<HomeAnnouncementDO> update = new UpdateWrapper<>();
        update.eq("announcement_id", announcementId)
                .set("status", STATUS_PUBLISHED)
                // 重复发布会刷新发布时间，使这条公告重新排到"最新"。
                .set("published_at", LocalDateTime.now())
                .set("updated_by", operatorId);
        homeAnnouncementMapper.update(null, update);
        return toAdminVO(requireAnnouncement(announcementId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public HomeAnnouncementAdminVO offline(Long operatorId, Long announcementId) {
        validateId(announcementId);
        HomeAnnouncementDO existing = requireAnnouncement(announcementId);
        // 重复下线直接返回当前状态，避免运营连点产生冲突提示。
        if (Integer.valueOf(STATUS_OFFLINE).equals(existing.getStatus())) {
            return toAdminVO(existing);
        }

        UpdateWrapper<HomeAnnouncementDO> update = new UpdateWrapper<>();
        update.eq("announcement_id", announcementId)
                .set("status", STATUS_OFFLINE)
                // 保留下线前的发布时间，便于追溯曾经对外展示过。
                .set("updated_by", operatorId);
        homeAnnouncementMapper.update(null, update);
        return toAdminVO(requireAnnouncement(announcementId));
    }

    /**
     * 校验请求对象非空；Servlet 层通常已拦截空请求体，这里防止内部调用绕过。
     *
     * @param request 请求对象
     */
    private void requireRequest(HomeAnnouncementSaveRequest request) {
        if (request == null) {
            throw badRequest("请求参数不能为空");
        }
    }

    /**
     * 校验生效窗口：结束时间可空；非空时必须晚于开始时间。
     *
     * <p>数据库 CHECK 约束是最后一道防线，这里提前返回可读的业务错误，
     * 避免把约束冲突暴露成 500。开始时间允许为将来，实现定时生效。</p>
     *
     * @param startAt 开始时间
     * @param endAt   结束时间，可为空
     */
    private void validateWindow(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null) {
            throw badRequest("生效开始时间不能为空");
        }
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw badRequest("生效结束时间必须晚于开始时间");
        }
    }

    /**
     * 校验文本必填与长度，并返回去掉首尾空白后的值。
     *
     * <p>Bean Validation 已在 Controller 层拦截大部分非法输入；此处仍做一次
     * 服务层校验，防止将来内部调用绕过 Controller 时写入空白标题。</p>
     *
     * @param value     原始文本
     * @param fieldName 字段中文名，用于错误提示
     * @param maxLength 最大长度
     * @return 去除首尾空白后的文本
     */
    private String requireText(String value, String fieldName, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw badRequest(fieldName + "不能为空");
        }
        if (trimmed.length() > maxLength) {
            throw badRequest(fieldName + "不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }

    /**
     * 按编号查询公告，不存在时返回统一 404，不区分"已删除"与"从未存在"。
     *
     * @param announcementId 公告编号
     * @return 公告持久化对象
     */
    private HomeAnnouncementDO requireAnnouncement(Long announcementId) {
        HomeAnnouncementDO announcement = homeAnnouncementMapper.selectById(announcementId);
        if (announcement == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "公告不存在");
        }
        return announcement;
    }

    /**
     * 校验路径中的公告编号为正整数。
     *
     * @param announcementId 公告编号
     */
    private void validateId(Long announcementId) {
        if (announcementId == null || announcementId <= 0) {
            throw badRequest("公告 ID 必须为正整数");
        }
    }

    /**
     * 构造参数错误业务异常。
     *
     * @param message 提示信息
     * @return 参数错误异常
     */
    private BusinessException badRequest(String message) {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, message);
    }

    /**
     * 将持久化对象转换为学生端视图，只暴露展示字段。
     *
     * @param announcement 公告持久化对象
     * @return 学生端公告视图
     */
    private HomeAnnouncementVO toPublicVO(HomeAnnouncementDO announcement) {
        return new HomeAnnouncementVO(
                announcement.getAnnouncementId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getPublishedAt()
        );
    }

    /**
     * 将持久化对象转换为管理端视图，包含状态与审计字段。
     *
     * @param announcement 公告持久化对象
     * @return 管理端公告视图
     */
    private HomeAnnouncementAdminVO toAdminVO(HomeAnnouncementDO announcement) {
        return new HomeAnnouncementAdminVO(
                announcement.getAnnouncementId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getStatus(),
                announcement.getStartAt(),
                announcement.getEndAt(),
                announcement.getPublishedAt(),
                announcement.getCreatedBy(),
                announcement.getUpdatedBy(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt()
        );
    }
}
