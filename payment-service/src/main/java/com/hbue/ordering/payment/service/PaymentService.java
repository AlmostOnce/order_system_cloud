package com.hbue.ordering.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.dto.request.MockPayRequest;
import com.hbue.ordering.payment.dto.request.PaymentCreateRequest;
import com.hbue.ordering.payment.model.PaymentDO;
import com.hbue.ordering.payment.vo.CheckoutInfoVO;
import com.hbue.ordering.payment.vo.PaymentCreateVO;
import com.hbue.ordering.payment.vo.PaymentStatusVO;
import com.hbue.ordering.common.response.ApiResponse;

/**
 * 支付业务服务契约。
 *
 * <p>对 Controller 暴露支付单创建、归属校验后的状态查询及渠道回调处理能力；
 * 具体状态机、渠道路由、幂等控制和跨服务订单推进由实现类编排，Controller 不直接访问 Mapper。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
public interface PaymentService extends IService<PaymentDO> {

    /**
     * 为待支付订单创建支付单。
     *
     * <p>金额取订单快照，绝不取客户端输入；渠道按请求中的 channelCode 路由，
     * 缺省为 MOCK。余额渠道（BALANCE）在创建时同步完成扣款并直接置为成功，
     * 其余渠道返回收银台凭证等待异步回调。
     * 同一订单已有进行中支付单时幂等返回。</p>
     *
     * @param userId 当前登录顾客 ID
     * @param request 创建支付单请求（含渠道标识与余额渠道的支付密码）
     * @return 支付单创建结果
     * @throws com.hbue.ordering.common.core.exception.BusinessException 订单不存在、非本人订单、
     *         状态冲突或依赖服务不可用时抛出
     */
    PaymentCreateVO createPayment(Long userId, PaymentCreateRequest request);

    /**
     * 查询支付单状态（校验归属，非本人按不存在处理）。
     *
     * @param userId 当前登录顾客 ID
     * @param paymentId 支付单 ID
     * @return 支付单状态视图
     * @throws com.hbue.ordering.common.core.exception.BusinessException 支付单不存在或不属于当前用户时按 404 处理
     */
    PaymentStatusVO getPaymentStatus(Long userId, Long paymentId);

    /**
     * 按订单查询最近一笔支付单（前端轮询用，校验归属）。
     *
     * @param userId 当前登录顾客 ID
     * @param orderId 订单 ID
     * @return 支付单状态视图
     * @throws com.hbue.ordering.common.core.exception.BusinessException 订单无支付单或支付单不属于当前用户时按 404 处理
     */
    PaymentStatusVO getLatestByOrder(Long userId, Long orderId);

    /**
     * 处理渠道异步通知：验签、金额校验、幂等状态机、订单推进。
     *
     * @param request 渠道通知请求体
     * @param signature 渠道侧签名
     * @return 统一响应；COMMON_000 表示已受理（渠道可停止重试）
     * @throws com.hbue.ordering.common.core.exception.BusinessException 处理流程需要以异常中止时抛出；
     *         验签、金额等拒绝通过统一响应返回
     */
    ApiResponse<Void> handleNotify(
            ChannelNotifyRequest request,
            String signature
    );

    /**
     * 查询模拟收银台页面信息。
     *
     * @param paymentNo 业务支付单号
     * @return 收银台信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException 支付单不存在时抛出
     */
    CheckoutInfoVO getCheckoutInfo(String paymentNo);

    /**
     * 模拟渠道确认支付：生成渠道交易号并异步发起回调。
     *
     * @param paymentNo 业务支付单号
     * @param request 确认支付请求（缺省按成功全额处理）
     * @return 渠道交易号
     * @throws com.hbue.ordering.common.core.exception.BusinessException 支付单不存在或已处理时抛出
     */
    String confirmMockPayment(String paymentNo, MockPayRequest request);
}
