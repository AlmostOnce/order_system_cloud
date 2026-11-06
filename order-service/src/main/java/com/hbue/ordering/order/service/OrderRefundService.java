package com.hbue.ordering.order.service;
import com.hbue.ordering.order.dto.request.*;
import com.hbue.ordering.order.vo.*;
/** 售后工作流；商家与经理由受信JWT角色区分，不能在请求体伪造审批范围。 */
public interface OrderRefundService {
    /** 本人按订单明细及份数申请；key绑定本次内容，金额由实付快照计算，返回当前持久化进度。 */
    RefundVO apply(Long userId,Long orderId,String key,RefundApplyRequest request);
    /** 查询本人单一订单的最新20条退款；游标验证归属，拒绝他人编号，不返回全量COUNT。 */
    RefundPageVO listOwn(Long userId,Long orderId,String cursor);
    /** 按已验签角色和既有数据范围查询商家/经理队列；非平台范围必须明确授权窗口。 */
    RefundPageVO queue(Long operatorId,boolean general,boolean appeal,Long windowId,String cursor);
    /** 本地事务记录审批、计数和审计；批准只进入执行队列，不代表资金已经到账。 */
    RefundVO decide(Long operatorId,boolean manager,boolean general,Long refundId,RefundDecisionRequest request);
    /** 商家拒绝后本人升级争议，不改变原金额/份数，也不会自动批准。 */
    RefundVO appeal(Long userId,Long orderId,Long refundId);
    /** 本人关闭尚未批准申请并释放额度；已批准或超过商家处理期不能撤回。 */
    RefundVO withdraw(Long userId,Long orderId,Long refundId);
    /** 仅后台调用，在订单锁内重核PENDING和72小时截止时间，到期自动批准但不自动回库。 */
    void expire(Long refundId);
    /** 仅执行任务调用，资金及必要库存已逐项确认后标成功；重复调用不再累计份数。 */
    void complete(Long refundId);
}
