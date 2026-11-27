package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.client.OrderDeletionBlockerClient;
import com.hbue.ordering.user.client.SupportDeletionBlockerClient;
import com.hbue.ordering.user.client.vo.OrderDeletionBlockersVO;
import com.hbue.ordering.user.client.vo.SupportDeletionBlockersVO;
import com.hbue.ordering.user.vo.AccountDeletionBlockerVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合订单与工单服务的账号注销阻断项。
 *
 * <p>订单、退款和工单数据分别属于 order-service 与 support-service，用户服务不能直接
 * 查询它们的数据库，只能通过内部接口获取计数。任何下游失败、响应为空或非成功码都
 * 视为“当前无法确认是否可注销”，按 fail-closed 抛出服务不可用，绝不在不确定时
 * 放行注销或到期删除。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Service
public class AccountDeletionBlockerService {

    /**
     * 日志对象；只记录用户 ID 和失败类型，不记录内部令牌。
     */
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionBlockerService.class);

    /**
     * 订单服务内部客户端。
     */
    private final OrderDeletionBlockerClient orderClient;

    /**
     * 工单服务内部客户端。
     */
    private final SupportDeletionBlockerClient supportClient;

    /**
     * 服务间共享内部令牌；未配置时拒绝发起远程调用。
     */
    private final String internalToken;

    /**
     * 创建阻断项聚合服务。
     *
     * @param orderClient   订单服务客户端
     * @param supportClient 工单服务客户端
     * @param internalToken 内部调用令牌
     */
    public AccountDeletionBlockerService(OrderDeletionBlockerClient orderClient,
                                         SupportDeletionBlockerClient supportClient,
                                         @Value("${internal.scope.token:}") String internalToken) {
        this.orderClient = orderClient;
        this.supportClient = supportClient;
        this.internalToken = internalToken;
    }

    /**
     * 查询指定用户的全部注销阻断计数。
     *
     * @param userId 用户 ID
     * @return 阻断计数汇总，不返回明细
     * @throws BusinessException 内部令牌缺失或任一远程查询失败时抛出 503
     */
    public BlockerSummary load(Long userId) {
        // 内部令牌缺失说明部署配置不完整，直接失败，避免无凭据调用下游。
        if (internalToken == null || internalToken.isBlank()) {
            log.error("账号注销阻断项查询失败：内部调用令牌未配置");
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "暂时无法核对注销条件，请稍后重试");
        }

        try {
            // 依次查询两个下游；两者互不依赖，但任一方失败都需要整体失败。
            ApiResponse<OrderDeletionBlockersVO> orderResponse =
                    orderClient.getBlockers(userId, internalToken);
            ApiResponse<SupportDeletionBlockersVO> supportResponse =
                    supportClient.getBlockers(userId, internalToken);

            // 任一响应缺失或非成功码都表示条件无法确认，不能当作 0 处理。
            if (!isSuccess(orderResponse) || orderResponse.getData() == null
                    || !isSuccess(supportResponse) || supportResponse.getData() == null) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "暂时无法核对注销条件，请稍后重试");
            }

            OrderDeletionBlockersVO order = orderResponse.getData();
            SupportDeletionBlockersVO support = supportResponse.getData();
            return new BlockerSummary(
                    order.getUnfinishedOrders(),
                    order.getUnsettledRefunds(),
                    order.getRejectedRefunds(),
                    support.getOpenTickets()
            );
        } catch (BusinessException exception) {
            // 业务异常保持原始提示，不重复包装。
            throw exception;
        } catch (RuntimeException exception) {
            // Feign 连接失败、超时或反序列化失败统一按服务不可用处理。
            log.warn("账号注销阻断项查询失败，userId={}，原因={}", userId, exception.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "暂时无法核对注销条件，请稍后重试");
        }
    }

    /**
     * 将阻断计数转换为前端展示列表。
     *
     * <p>余额上限由用户服务本地判断，不在此方法内重复计算；被拒退款只作为提示，
     * 不作为阻断项。数量为 0 的项不返回，保证列表只包含真正阻止注销的原因。</p>
     *
     * @param summary 阻断计数汇总
     * @return 阻断项列表，可能为空
     */
    public List<AccountDeletionBlockerVO> toBlockers(BlockerSummary summary) {
        List<AccountDeletionBlockerVO> blockers = new ArrayList<>(3);
        if (summary.unfinishedOrders() > 0) {
            blockers.add(new AccountDeletionBlockerVO(
                    "UNFINISHED_ORDERS", "还有未完成的订单，请先完成取餐或取消", summary.unfinishedOrders()));
        }
        if (summary.unsettledRefunds() > 0) {
            blockers.add(new AccountDeletionBlockerVO(
                    "UNSETTLED_REFUNDS", "还有未结案的退款申请，请等待处理完成", summary.unsettledRefunds()));
        }
        if (summary.openTickets() > 0) {
            blockers.add(new AccountDeletionBlockerVO(
                    "OPEN_TICKETS", "还有未关闭的问题工单，请等待处理完成", summary.openTickets()));
        }
        return blockers;
    }

    /**
     * 判断统一响应是否为成功。
     *
     * @param response 下游响应
     * @return 响应存在且业务码为成功时返回 true
     */
    private boolean isSuccess(ApiResponse<?> response) {
        return response != null && CommonErrorCode.SUCCESS.code().equals(response.getCode());
    }

    /**
     * 阻断计数汇总。
     *
     * @param unfinishedOrders 未完成订单数
     * @param unsettledRefunds 未结案退款数
     * @param rejectedRefunds  被拒退款数（仅提示）
     * @param openTickets      未关闭工单数
     */
    public record BlockerSummary(long unfinishedOrders, long unsettledRefunds,
                                 long rejectedRefunds, long openTickets) {

        /**
         * 是否存在硬阻断项。
         *
         * @return 未完成订单、未结案退款或未关闭工单任一大于 0 时返回 true
         */
        public boolean hasBlocker() {
            return unfinishedOrders > 0 || unsettledRefunds > 0 || openTickets > 0;
        }
    }
}
