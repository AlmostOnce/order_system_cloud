package com.hbue.ordering.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.dto.request.MockPayRequest;
import com.hbue.ordering.payment.model.PaymentDO;
import com.hbue.ordering.payment.vo.CheckoutInfoVO;
import com.hbue.ordering.payment.vo.PaymentCreateVO;
import com.hbue.ordering.payment.vo.PaymentStatusVO;
import com.hbue.ordering.common.response.ApiResponse;

/**
 * 支付业务服务。
 *
 * @author order-system
 * @date 2026-10-12
 */
public interface PaymentService extends IService<PaymentDO> {

    /**
     * 为待支付订单创建支付单。
     *
     * <p>金额取订单快照，绝不取客户端输入；
     * 同一订单已有进行中支付单时幂等返回。</p>
     *
     * @param userId 当前登录顾客 ID
     * @param orderId 订单 ID
     * @return 支付单创建结果（含模拟收银台地址）
     */
    PaymentCreateVO createPayment(Long userId, Long orderId);

    /**
     * 查询支付单状态（校验归属，非本人按不存在处理）。
     *
     * @param userId 当前登录顾客 ID
     * @param paymentId 支付单 ID
     * @return 支付单状态视图
     */
    PaymentStatusVO getPaymentStatus(Long userId, Long paymentId);

    /**
     * 按订单查询最近一笔支付单（前端轮询用，校验归属）。
     *
     * @param userId 当前登录顾客 ID
     * @param orderId 订单 ID
     * @return 支付单状态视图
     */
    PaymentStatusVO getLatestByOrder(Long userId, Long orderId);

    /**
     * 处理渠道异步通知：验签、金额校验、幂等状态机、订单推进。
     *
     * @param request 渠道通知请求体
     * @param signature 渠道侧签名
     * @return 统一响应；COMMON_000 表示已受理（渠道可停止重试）
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
     */
    CheckoutInfoVO getCheckoutInfo(String paymentNo);

    /**
     * 模拟渠道确认支付：生成渠道交易号并异步发起回调。
     *
     * @param paymentNo 业务支付单号
     * @param request 确认支付请求（缺省按成功全额处理）
     * @return 渠道交易号
     */
    String confirmMockPayment(String paymentNo, MockPayRequest request);
}
