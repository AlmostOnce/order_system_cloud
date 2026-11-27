package com.hbue.ordering.support.service;

import com.hbue.ordering.support.dto.AddSupportMessageRequest;
import com.hbue.ordering.support.dto.CreateSupportTicketRequest;
import com.hbue.ordering.support.dto.SupportCloseRequest;
import com.hbue.ordering.support.vo.SupportTicketDetailVO;
import com.hbue.ordering.support.vo.SupportTicketPageVO;

/**
 * 学生端反馈工单业务边界。
 *
 * <p>只服务于 CUSTOMER 角色。所有方法的 {@code customerId} 必须来自已验签 JWT 的
 * subject，不接受客户端传入他人身份；实现类必须在 SQL 或归属校验中再次绑定该编号，
 * 越权访问统一表现为「反馈不存在」，不泄露他人工单是否存在。</p>
 *
 * <p>创建与补充说明要求幂等键；实现类需保证同一键重放返回首次结果，同键不同内容
 * 不得静默覆盖。具体事务边界、跨服务调用顺序与异常映射由实现类负责。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
public interface SupportTicketService {

    /**
     * 提交反馈工单。
     *
     * @param customerId    当前学生内部用户编号，取自 JWT
     * @param authorization 学生原始 Bearer 凭据，用于订单服务校验归属
     * @param request       提交内容
     * @param requestKey    请求头幂等键，8-64 位字母、数字、下划线或短横线
     * @return 新建或幂等命中的工单详情
     */
    SupportTicketDetailVO create(Long customerId, String authorization,
                                 CreateSupportTicketRequest request, String requestKey);

    /**
     * 查询学生本人的反馈列表。
     *
     * @param customerId 当前学生内部用户编号
     * @param cursor     上一页最后一条工单的公开标识，首屏可空
     * @param limit      每页条数，实现类需限制最大值
     * @return 游标分页结果
     */
    SupportTicketPageVO page(Long customerId, String cursor, int limit);

    /**
     * 查询学生本人的反馈详情。
     *
     * <p>读取成功后需清空新回复提示；清零失败不得阻止详情返回，下次查看继续尝试。</p>
     *
     * @param customerId 当前学生内部用户编号
     * @param ticketId   公开工单标识
     * @return 工单详情
     */
    SupportTicketDetailVO detail(Long customerId, String ticketId);

    /**
     * 学生补充说明。
     *
     * <p>仅未关闭工单可补充；同一工单内相同幂等键重放返回首次结果，不重复追加消息。</p>
     *
     * @param customerId 当前学生内部用户编号
     * @param ticketId   公开工单标识
     * @param requestKey 请求头幂等键
     * @param request    补充正文与图片
     * @return 补充后的工单详情
     */
    SupportTicketDetailVO addMessage(Long customerId, String ticketId,
                                     String requestKey, AddSupportMessageRequest request);

    /**
     * 学生关闭反馈。
     *
     * <p>重复关闭按幂等成功处理；首次关闭需写入系统消息说明结果。
     * 关闭只影响工单状态，不改变订单或退款状态。</p>
     *
     * @param customerId 当前学生内部用户编号
     * @param ticketId   公开工单标识
     * @param request    关闭原因，可空
     * @return 关闭后的工单详情
     */
    SupportTicketDetailVO close(Long customerId, String ticketId, SupportCloseRequest request);
}
