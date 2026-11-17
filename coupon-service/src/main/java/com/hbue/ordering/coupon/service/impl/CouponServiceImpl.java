package com.hbue.ordering.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.coupon.api.dto.CouponLockRequest;
import com.hbue.ordering.coupon.api.dto.CouponPaidEvent;
import com.hbue.ordering.coupon.api.vo.CouponLockVO;
import com.hbue.ordering.coupon.dto.CouponMineRow;
import com.hbue.ordering.coupon.dto.CouponTemplateCreateRequest;
import com.hbue.ordering.coupon.dto.CouponTemplateRow;
import com.hbue.ordering.coupon.enums.CouponTemplateStatus;
import com.hbue.ordering.coupon.enums.UserCouponStatus;
import com.hbue.ordering.coupon.mapper.CouponAdminActionMapper;
import com.hbue.ordering.coupon.mapper.CouponTemplateMapper;
import com.hbue.ordering.coupon.mapper.CouponTemplateWindowMapper;
import com.hbue.ordering.coupon.mapper.CouponUseRecordMapper;
import com.hbue.ordering.coupon.mapper.UserCouponMapper;
import com.hbue.ordering.coupon.model.CouponAdminActionDO;
import com.hbue.ordering.coupon.model.CouponTemplateDO;
import com.hbue.ordering.coupon.model.CouponUseRecordDO;
import com.hbue.ordering.coupon.model.UserCouponDO;
import com.hbue.ordering.coupon.service.CouponAuthorizationService;
import com.hbue.ordering.coupon.service.CouponService;
import com.hbue.ordering.coupon.vo.CouponTemplateVO;
import com.hbue.ordering.coupon.vo.UserCouponVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 优惠券业务实现；数据库条件更新与唯一索引承担并发控制，不使用缓存作为库存事实。 */
@Service
public class CouponServiceImpl implements CouponService {
    /** 模板及可领取窗口关系访问入口。 */
    private final CouponTemplateMapper templateMapper;
    private final CouponTemplateWindowMapper templateWindowMapper;
    /** 学生持券状态访问入口。 */
    private final UserCouponMapper userCouponMapper;
    /** 锁定、核销、释放的金额审计流水访问入口。 */
    private final CouponUseRecordMapper useRecordMapper;
    /** 管理动作审计访问入口。 */
    private final CouponAdminActionMapper adminActionMapper;
    /** 负责总经理平台数据范围确认。 */
    private final CouponAuthorizationService authorizationService;

    /** 注入本服务依赖，所有数据写入边界留在当前 coupon_db。 */
    public CouponServiceImpl(
            CouponTemplateMapper templateMapper,
            CouponTemplateWindowMapper templateWindowMapper,
            UserCouponMapper userCouponMapper,
            CouponUseRecordMapper useRecordMapper,
            CouponAdminActionMapper adminActionMapper,
            CouponAuthorizationService authorizationService
    ) {
        this.templateMapper = templateMapper;
        this.templateWindowMapper = templateWindowMapper;
        this.userCouponMapper = userCouponMapper;
        this.useRecordMapper = useRecordMapper;
        this.adminActionMapper = adminActionMapper;
        this.authorizationService = authorizationService;
    }

    /** 查询仍在领取时间内且有余量的模板；数据库查询把结果限制在前 100 个模板内。 */
    @Override
    @Transactional(readOnly = true)
    public List<CouponTemplateVO> listClaimable() {
        return mergeTemplateRows(templateMapper.listClaimableRows());
    }

    /** 先原子占用一份发行量，再插入用户券；任何插入失败都会回滚发行量。 */
    @Override
    @Transactional
    public UserCouponVO claim(Long userId, Long templateId) {
        requirePositiveId(userId, "用户");
        requirePositiveId(templateId, "优惠券模板");
        CouponTemplateDO template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "优惠券不存在");
        }
        if (!CouponTemplateStatus.PUBLISHED.name().equals(template.getStatus())) {
            throw conflict("优惠券当前不接受领取");
        }
        if (templateMapper.reserveClaim(templateId) != 1) {
            throw conflict("优惠券已停止领取或库存不足");
        }

        LocalDateTime now = LocalDateTime.now();
        UserCouponDO userCoupon = new UserCouponDO();
        userCoupon.setCouponTemplateId(templateId);
        userCoupon.setUserId(userId);
        userCoupon.setStatus(UserCouponStatus.AVAILABLE.name());
        userCoupon.setValidFrom(template.getValidFrom());
        userCoupon.setValidUntil(template.getValidUntil());
        userCoupon.setClaimedAt(now);
        try {
            // uk_user_coupon_template 是每人限领的最终并发防线；重复请求异常会使库存预占一并回滚。
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException exception) {
            throw conflict("你已领取过这张优惠券");
        }
        return toUserCoupon(userCoupon, template, readWindowIds(templateId));
    }

    /** 只读取当前 JWT 用户自己的持券；过期券在同一事务内惰性迁移到 EXPIRED。 */
    @Override
    @Transactional
    public List<UserCouponVO> listMine(Long userId, String status) {
        requirePositiveId(userId, "用户");
        String requestedStatus = normalizeCouponStatus(status);
        userCouponMapper.expireAvailable(userId);
        return mergeMineRows(userCouponMapper.listMineRows(userId, requestedStatus));
    }

    /** 总经理管理列表；角色判断在 Controller，平台范围再向 user-service 复核。 */
    @Override
    @Transactional(readOnly = true)
    public List<CouponTemplateVO> listAdminTemplates(Long operatorId) {
        authorizationService.requirePlatformScope(operatorId);
        return mergeTemplateRows(templateMapper.listAdminRows());
    }

    /** 创建仅由平台发行、平台预算承担的草稿模板，并在同一事务留下审计记录。 */
    @Override
    @Transactional
    public CouponTemplateVO createTemplate(Long operatorId, CouponTemplateCreateRequest request) {
        authorizationService.requirePlatformScope(operatorId);
        validateTemplateRequest(request);
        List<Long> windowIds = normalizedWindowIds(request.getWindowIds());
        authorizationService.requireExistingWindows(windowIds);

        CouponTemplateDO template = new CouponTemplateDO();
        template.setTitle(request.getTitle().trim());
        template.setMinSpend(request.getMinSpend());
        template.setDiscountAmount(request.getDiscountAmount());
        template.setTotalQuantity(request.getTotalQuantity());
        template.setClaimedQuantity(0L);
        template.setClaimStartAt(request.getClaimStartAt());
        template.setClaimEndAt(request.getClaimEndAt());
        template.setValidFrom(request.getValidFrom());
        template.setValidUntil(request.getValidUntil());
        template.setScopeType(windowIds.isEmpty() ? "CAMPUS" : "WINDOWS");
        template.setIssuerType("PLATFORM");
        template.setFundingParty("PLATFORM");
        template.setStatus(CouponTemplateStatus.DRAFT.name());
        template.setCreatedBy(operatorId);
        if (templateMapper.insert(template) != 1 || template.getCouponTemplateId() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "优惠券模板创建失败");
        }
        if (!windowIds.isEmpty()) {
            templateWindowMapper.insertWindows(template.getCouponTemplateId(), windowIds);
        }
        recordAdminAction(
                template.getCouponTemplateId(), operatorId, "CREATE", null,
                CouponTemplateStatus.DRAFT.name()
        );
        return toTemplateVO(template, windowIds, template.getTotalQuantity());
    }

    /** 只允许从草稿发布；发布、审计写入在一个本地事务内完成。 */
    @Override
    @Transactional
    public void publishTemplate(Long operatorId, Long templateId) {
        authorizationService.requirePlatformScope(operatorId);
        CouponTemplateDO template = requireTemplateForUpdate(templateId);
        if (CouponTemplateStatus.PUBLISHED.name().equals(template.getStatus())) {
            return;
        }
        if (!CouponTemplateStatus.DRAFT.name().equals(template.getStatus())) {
            throw conflict("当前模板状态不能发布");
        }
        transitionTemplate(template, operatorId, "PUBLISH", CouponTemplateStatus.PUBLISHED.name());
    }

    /** 停止新增领取；已经发出的券仍可按有效期和订单窗口继续使用。 */
    @Override
    @Transactional
    public void stopClaims(Long operatorId, Long templateId) {
        authorizationService.requirePlatformScope(operatorId);
        CouponTemplateDO template = requireTemplateForUpdate(templateId);
        if (CouponTemplateStatus.CLAIM_STOPPED.name().equals(template.getStatus())
                || CouponTemplateStatus.STOPPED.name().equals(template.getStatus())) {
            return;
        }
        if (!CouponTemplateStatus.PUBLISHED.name().equals(template.getStatus())) {
            throw conflict("当前模板状态不能停止领取");
        }
        transitionTemplate(template, operatorId, "STOP_CLAIMS", CouponTemplateStatus.CLAIM_STOPPED.name());
    }

    /** 停止新锁券并撤销尚未进入订单的券；已锁定订单保留，避免覆盖支付中的事实。 */
    @Override
    @Transactional
    public void stopUse(Long operatorId, Long templateId) {
        authorizationService.requirePlatformScope(operatorId);
        CouponTemplateDO template = requireTemplateForUpdate(templateId);
        if (CouponTemplateStatus.STOPPED.name().equals(template.getStatus())) {
            return;
        }
        if (!CouponTemplateStatus.PUBLISHED.name().equals(template.getStatus())
                && !CouponTemplateStatus.CLAIM_STOPPED.name().equals(template.getStatus())
                && !CouponTemplateStatus.DRAFT.name().equals(template.getStatus())) {
            throw conflict("当前模板状态不能停止使用");
        }
        String previousStatus = template.getStatus();
        template.setStatus(CouponTemplateStatus.STOPPED.name());
        if (templateMapper.updateById(template) != 1) {
            throw conflict("优惠券状态已变化，请刷新后重试");
        }
        // 与锁券使用相同的模板先行锁顺序，防止管理停用与下单锁券互相死锁。
        userCouponMapper.revokeAvailable(templateId);
        recordAdminAction(
                templateId, operatorId, "STOP_USE", previousStatus,
                CouponTemplateStatus.STOPPED.name()
        );
    }

    /** 按模板→用户券的固定行锁顺序检查规则，防止停用与锁券竞态放入已停用券。 */
    @Override
    @Transactional
    public CouponLockVO lock(CouponLockRequest request) {
        requirePositiveId(request.getUserCouponId(), "用户优惠券");
        requirePositiveId(request.getUserId(), "用户");
        requirePositiveId(request.getWindowId(), "窗口");
        if (request.getOrderNo() == null || request.getOrderNo().isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "订单号不能为空");
        }
        if (!isMoney(request.getEligibleAmount()) || request.getEligibleAmount().signum() <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "适用商品金额必须为正数且最多两位小数");
        }

        UserCouponDO snapshot = userCouponMapper.selectById(request.getUserCouponId());
        if (snapshot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户优惠券不存在");
        }
        CouponTemplateDO template = templateMapper.findByIdForUpdate(snapshot.getCouponTemplateId());
        UserCouponDO userCoupon = userCouponMapper.findByIdForUpdate(request.getUserCouponId());
        if (template == null || userCoupon == null
                || !template.getCouponTemplateId().equals(userCoupon.getCouponTemplateId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户优惠券不存在");
        }
        if (!userCoupon.getUserId().equals(request.getUserId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "该优惠券不属于当前订单用户");
        }

        if (UserCouponStatus.LOCKED.name().equals(userCoupon.getStatus())
                && request.getOrderNo().equals(userCoupon.getLockedOrderNo())) {
            CouponUseRecordDO existing = useRecordMapper.findByCouponAndOrder(
                    userCoupon.getUserCouponId(), request.getOrderNo()
            );
            if (existing == null || !"LOCKED".equals(existing.getStatus())) {
                throw conflict("订单锁券记录状态不一致");
            }
            return toLockVO(userCoupon, template, existing.getDiscountAmount());
        }
        if (useRecordMapper.findByOrderNo(request.getOrderNo()) != null) {
            throw conflict("该订单已存在优惠券锁定或释放记录");
        }
        if (!UserCouponStatus.AVAILABLE.name().equals(userCoupon.getStatus())) {
            throw conflict("该优惠券已使用、锁定、过期或被撤销");
        }
        if (!CouponTemplateStatus.PUBLISHED.name().equals(template.getStatus())
                && !CouponTemplateStatus.CLAIM_STOPPED.name().equals(template.getStatus())) {
            throw conflict("该优惠券已停止使用");
        }

        LocalDateTime now = LocalDateTime.now();
        if (userCoupon.getValidFrom().isAfter(now) || !userCoupon.getValidUntil().isAfter(now)) {
            throw conflict("该优惠券当前不在有效期内");
        }
        if (request.getEligibleAmount().compareTo(template.getMinSpend()) < 0) {
            throw conflict("订单金额未达到优惠券使用门槛");
        }
        if ("WINDOWS".equals(template.getScopeType())
                && templateWindowMapper.countWindow(template.getCouponTemplateId(), request.getWindowId()) == 0) {
            throw conflict("该优惠券不适用于当前窗口");
        }
        if (!"CAMPUS".equals(template.getScopeType()) && !"WINDOWS".equals(template.getScopeType())) {
            throw conflict("优惠券适用范围配置无效");
        }

        int locked = userCouponMapper.update(null, new UpdateWrapper<UserCouponDO>()
                .eq("user_coupon_id", userCoupon.getUserCouponId())
                .eq("user_id", request.getUserId())
                .eq("status", UserCouponStatus.AVAILABLE.name())
                .apply("valid_from <= NOW() AND valid_until > NOW()")
                .set("status", UserCouponStatus.LOCKED.name())
                .set("locked_order_no", request.getOrderNo()));
        if (locked != 1) {
            throw conflict("优惠券状态已变化，请重新选择");
        }

        CouponUseRecordDO record = new CouponUseRecordDO();
        record.setUserCouponId(userCoupon.getUserCouponId());
        record.setCouponTemplateId(template.getCouponTemplateId());
        record.setUserId(userCoupon.getUserId());
        record.setOrderNo(request.getOrderNo());
        record.setStatus("LOCKED");
        record.setDiscountAmount(template.getDiscountAmount());
        record.setPlatformFundedAmount(template.getDiscountAmount());
        record.setWindowFundedAmount(BigDecimal.ZERO.setScale(2));
        record.setLockedAt(now);
        if (useRecordMapper.insert(record) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "优惠券锁定流水写入失败");
        }
        return toLockVO(userCoupon, template, template.getDiscountAmount());
    }

    /** 订单已确认 CLOSED 后由订单服务调用；订单无券或重复释放均不产生错误。 */
    @Override
    @Transactional
    public void release(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "订单号不能为空");
        }
        UserCouponDO snapshot = userCouponMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserCouponDO>()
                        .eq("locked_order_no", orderNo)
                        .last("LIMIT 1")
        );
        if (snapshot == null) {
            return;
        }
        CouponTemplateDO template = templateMapper.findByIdForUpdate(snapshot.getCouponTemplateId());
        if (template == null) {
            throw conflict("优惠券模板缺失，无法安全释放锁定状态");
        }
        UserCouponDO userCoupon = userCouponMapper.findByLockedOrderForUpdate(orderNo);
        if (userCoupon == null) {
            return;
        }
        if (!UserCouponStatus.LOCKED.name().equals(userCoupon.getStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String nextStatus;
        if (!userCoupon.getValidUntil().isAfter(now)) {
            nextStatus = UserCouponStatus.EXPIRED.name();
        } else if (CouponTemplateStatus.STOPPED.name().equals(template.getStatus())) {
            nextStatus = UserCouponStatus.REVOKED.name();
        } else {
            nextStatus = UserCouponStatus.AVAILABLE.name();
        }
        int changed = userCouponMapper.update(null, new UpdateWrapper<UserCouponDO>()
                .eq("user_coupon_id", userCoupon.getUserCouponId())
                .eq("status", UserCouponStatus.LOCKED.name())
                .eq("locked_order_no", orderNo)
                .set("status", nextStatus)
                .set("locked_order_no", null));
        if (changed != 1 || useRecordMapper.markReleased(
                userCoupon.getUserCouponId(), orderNo, now
        ) != 1) {
            throw conflict("优惠券释放流水状态不一致");
        }
    }

    /** 支付确认事件只接受对应订单当前锁定的券；按 orderNo 幂等，异常抛出交由消息重试。 */
    @Override
    @Transactional
    public void markUsed(CouponPaidEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.orderNo() == null || event.orderNo().isBlank()
                || event.userCouponId() == null || event.userCouponId() <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "优惠券核销事件缺少订单或用户券标识");
        }
        UserCouponDO snapshot = userCouponMapper.selectById(event.userCouponId());
        if (snapshot == null) {
            throw conflict("支付事件关联的用户券不存在");
        }
        // 与管理停用及锁券保持相同模板优先的锁顺序，避免状态迁移互相死锁。
        if (templateMapper.findByIdForUpdate(snapshot.getCouponTemplateId()) == null) {
            throw conflict("支付事件关联的优惠券模板不存在");
        }
        UserCouponDO userCoupon = userCouponMapper.findByIdForUpdate(event.userCouponId());
        if (userCoupon == null || !event.orderNo().equals(userCoupon.getLockedOrderNo())) {
            throw conflict("支付事件与优惠券当前锁定订单不匹配");
        }
        if (UserCouponStatus.USED.name().equals(userCoupon.getStatus())) {
            return;
        }
        if (!UserCouponStatus.LOCKED.name().equals(userCoupon.getStatus())) {
            throw conflict("支付事件关联的优惠券并非锁定状态");
        }

        LocalDateTime now = LocalDateTime.now();
        int changed = userCouponMapper.update(null, new UpdateWrapper<UserCouponDO>()
                .eq("user_coupon_id", userCoupon.getUserCouponId())
                .eq("status", UserCouponStatus.LOCKED.name())
                .eq("locked_order_no", event.orderNo())
                .set("status", UserCouponStatus.USED.name())
                .set("used_at", now));
        if (changed != 1 || useRecordMapper.markUsed(
                userCoupon.getUserCouponId(), event.orderNo(), now
        ) != 1) {
            throw conflict("优惠券核销流水状态不一致");
        }
    }

    /** 读取模板行并确认存在，FOR UPDATE 让管理状态变更与锁券按同一模板顺序串行化。 */
    private CouponTemplateDO requireTemplateForUpdate(Long templateId) {
        requirePositiveId(templateId, "优惠券模板");
        CouponTemplateDO template = templateMapper.findByIdForUpdate(templateId);
        if (template == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "优惠券模板不存在");
        }
        return template;
    }

    /** 使用条件更新切换模板状态，并在同一事务内追加管理审计。 */
    private void transitionTemplate(
            CouponTemplateDO template,
            Long operatorId,
            String action,
            String nextStatus
    ) {
        String previousStatus = template.getStatus();
        int changed = templateMapper.update(null, new UpdateWrapper<CouponTemplateDO>()
                .eq("coupon_template_id", template.getCouponTemplateId())
                .eq("status", previousStatus)
                .set("status", nextStatus));
        if (changed != 1) {
            throw conflict("优惠券状态已变化，请刷新后重试");
        }
        recordAdminAction(
                template.getCouponTemplateId(), operatorId, action,
                previousStatus, nextStatus
        );
    }

    /** 记录管理动作；状态写入失败时事务回滚，不留下无审计的管理结果。 */
    private void recordAdminAction(
            Long templateId,
            Long operatorId,
            String action,
            String previousStatus,
            String nextStatus
    ) {
        CouponAdminActionDO record = new CouponAdminActionDO();
        record.setCouponTemplateId(templateId);
        record.setOperatorId(operatorId);
        record.setActionType(action);
        record.setPreviousStatus(previousStatus);
        record.setNewStatus(nextStatus);
        record.setCreatedAt(LocalDateTime.now());
        if (adminActionMapper.insert(record) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "优惠券管理审计记录写入失败");
        }
    }

    /** 读取窗口关系；全校券返回空集合。 */
    private List<Long> readWindowIds(Long templateId) {
        return List.copyOf(templateWindowMapper.listWindowIds(templateId));
    }

    /** 把模板与窗口联查行按模板聚合，保持查询的稳定顺序并剔除 LEFT JOIN 空窗口。 */
    private List<CouponTemplateVO> mergeTemplateRows(List<CouponTemplateRow> rows) {
        Map<Long, TemplateAccumulator> grouped = new LinkedHashMap<>();
        for (CouponTemplateRow row : rows) {
            TemplateAccumulator group = grouped.computeIfAbsent(
                    row.getTemplateId(), ignored -> new TemplateAccumulator(row)
            );
            if (row.getWindowId() != null) {
                group.windowIds.add(row.getWindowId());
            }
        }
        List<CouponTemplateVO> result = new ArrayList<>(grouped.size());
        for (TemplateAccumulator group : grouped.values()) {
            CouponTemplateRow row = group.row;
            result.add(CouponTemplateVO.builder()
                    .templateId(row.getTemplateId())
                    .title(row.getTitle())
                    .minSpend(row.getMinSpend())
                    .discountAmount(row.getDiscountAmount())
                    .scopeType(row.getScopeType())
                    .windowIds(List.copyOf(group.windowIds))
                    .validFrom(row.getValidFrom())
                    .validUntil(row.getValidUntil())
                    .status(row.getStatus())
                    .build());
        }
        return List.copyOf(result);
    }

    /** 将我的券多窗口联查结果按用户券 ID 聚合，确保每张券只返回一次。 */
    private List<UserCouponVO> mergeMineRows(List<CouponMineRow> rows) {
        Map<Long, MineAccumulator> grouped = new LinkedHashMap<>();
        for (CouponMineRow row : rows) {
            MineAccumulator group = grouped.computeIfAbsent(
                    row.getUserCouponId(), ignored -> new MineAccumulator(row)
            );
            if (row.getWindowId() != null) {
                group.windowIds.add(row.getWindowId());
            }
        }
        List<UserCouponVO> result = new ArrayList<>(grouped.size());
        for (MineAccumulator group : grouped.values()) {
            CouponMineRow row = group.row;
            result.add(UserCouponVO.builder()
                    .userCouponId(row.getUserCouponId())
                    .templateId(row.getTemplateId())
                    .title(row.getTitle())
                    .minSpend(row.getMinSpend())
                    .discountAmount(row.getDiscountAmount())
                    .scopeType(row.getScopeType())
                    .windowIds(List.copyOf(group.windowIds))
                    .claimStartAt(row.getClaimStartAt())
                    .claimEndAt(row.getClaimEndAt())
                    .validFrom(row.getValidFrom())
                    .validUntil(row.getValidUntil())
                    .totalQuantity(row.getTotalQuantity())
                    .remainingQuantity(row.getRemainingQuantity())
                    .status(row.getStatus())
                    .build());
        }
        return List.copyOf(result);
    }

    /** 返回创建成功后的券视图。 */
    private CouponTemplateVO toTemplateVO(
            CouponTemplateDO template,
            List<Long> windowIds,
            Long remainingQuantity
    ) {
        return CouponTemplateVO.builder()
                .templateId(template.getCouponTemplateId())
                .title(template.getTitle())
                .minSpend(template.getMinSpend())
                .discountAmount(template.getDiscountAmount())
                .scopeType(template.getScopeType())
                .windowIds(List.copyOf(windowIds))
                .claimStartAt(template.getClaimStartAt())
                .claimEndAt(template.getClaimEndAt())
                .validFrom(template.getValidFrom())
                .validUntil(template.getValidUntil())
                .totalQuantity(template.getTotalQuantity())
                .remainingQuantity(remainingQuantity)
                .status(template.getStatus())
                .build();
    }

    /** 为学生领券响应提供模板规则和适用窗口快照。 */
    private UserCouponVO toUserCoupon(
            UserCouponDO userCoupon,
            CouponTemplateDO template,
            List<Long> windowIds
    ) {
        return UserCouponVO.builder()
                .userCouponId(userCoupon.getUserCouponId())
                .templateId(template.getCouponTemplateId())
                .title(template.getTitle())
                .minSpend(template.getMinSpend())
                .discountAmount(template.getDiscountAmount())
                .scopeType(template.getScopeType())
                .windowIds(List.copyOf(windowIds))
                .validFrom(userCoupon.getValidFrom())
                .validUntil(userCoupon.getValidUntil())
                .status(userCoupon.getStatus())
                .build();
    }

    /** 构造订单服务保存金额和标题快照所需的锁券结果。 */
    private CouponLockVO toLockVO(
            UserCouponDO userCoupon,
            CouponTemplateDO template,
            BigDecimal discountAmount
    ) {
        return new CouponLockVO(
                userCoupon.getUserCouponId(),
                template.getCouponTemplateId(),
                template.getTitle(),
                discountAmount
        );
    }

    /** 拒绝超出数据库 DECIMAL(12,2) 精度的金额，避免数据库静默舍入改变券规则。 */
    private boolean isMoney(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() <= 2
                && value.precision() <= 12;
    }

    /** 管理模板只能保存两位小数且折扣小于门槛，以确保订单仍保留正数应付金额。 */
    private void validateTemplateRequest(CouponTemplateCreateRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "优惠券配置不能为空");
        }
        if (!isMoney(request.getMinSpend()) || request.getMinSpend().signum() <= 0
                || !isMoney(request.getDiscountAmount()) || request.getDiscountAmount().signum() <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "门槛和优惠金额必须为正数且最多两位小数");
        }
        if (request.getDiscountAmount().compareTo(request.getMinSpend()) >= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "优惠金额必须小于满减门槛");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getTitle().trim().length() > 120) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "优惠券标题不能为空且不能超过 120 个字符");
        }
        if (request.getTotalQuantity() == null || request.getTotalQuantity() <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "发行数量必须大于 0");
        }
        if (request.getClaimStartAt() == null || request.getClaimEndAt() == null
                || request.getValidFrom() == null || request.getValidUntil() == null
                || !request.getClaimStartAt().isBefore(request.getClaimEndAt())
                || !request.getValidFrom().isBefore(request.getValidUntil())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "领取和使用时间范围无效");
        }
    }

    /** 清理重复窗口 ID 并拒绝无效范围，避免同一模板写入关系主键冲突。 */
    private List<Long> normalizedWindowIds(List<Long> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        if (requested.size() > 100 || requested.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "窗口范围数量或 ID 无效");
        }
        Set<Long> distinct = new LinkedHashSet<>(requested);
        if (distinct.size() != requested.size()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "窗口范围不能重复");
        }
        return List.copyOf(distinct);
    }

    /** 只接受已定义的学生券状态，避免将任意查询字符串拼入持久层过滤语义。 */
    private String normalizeCouponStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserCouponStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "优惠券状态参数无效");
        }
    }

    /** 通用正数 ID 验证用于服务层内部接口，不能只依赖 HTTP Bean Validation。 */
    private void requirePositiveId(Long id, String label) {
        if (id == null || id <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, label + " ID 无效");
        }
    }

    /** 统一表达不可执行的券状态或资格冲突，由订单调用方据此中止当前事务。 */
    private BusinessException conflict(String message) {
        return new BusinessException(CommonErrorCode.CONFLICT, message);
    }

    /** 持有一行模板数据以及按 ID 去重后的适用窗口集合。 */
    private static final class TemplateAccumulator {
        private final CouponTemplateRow row;
        private final Set<Long> windowIds = new LinkedHashSet<>();

        private TemplateAccumulator(CouponTemplateRow row) {
            this.row = row;
        }
    }

    /** 持有一行学生券数据以及按 ID 去重后的适用窗口集合。 */
    private static final class MineAccumulator {
        private final CouponMineRow row;
        private final Set<Long> windowIds = new LinkedHashSet<>();

        private MineAccumulator(CouponMineRow row) {
            this.row = row;
        }
    }
}
