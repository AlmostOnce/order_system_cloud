package com.hbue.ordering.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.order.dto.request.*;
import com.hbue.ordering.order.feign.PaymentRefundClient;
import com.hbue.ordering.order.mapper.*;
import com.hbue.ordering.order.model.*;
import com.hbue.ordering.order.notification.*;
import com.hbue.ordering.order.service.*;
import com.hbue.ordering.order.vo.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按订单菜品份数的售后编排。所有变更先锁订单再锁退款，金额及数量只取数据库快照。
 * 申请占用额度不暂停制作；只有批准的份数退出履约。资金/库存步骤由独立持久化任务重试，
 * 此处没有跨服务长事务，也不会因为一个远程超时就撤销已经实际到账的资金事实。
 */
@Service
public class OrderRefundServiceImpl implements OrderRefundService {
    private final OrderMapper orders; private final OrderItemMapper items; private final OrderRefundMapper refunds;
    private final RefundActionMapper actions; private final DataScopeService scopes; private final PaymentRefundClient payments;
    private final OrderNotificationOutboxWriter notifications; private final String token; private final boolean notifyEnabled;
    public OrderRefundServiceImpl(OrderMapper orders,OrderItemMapper items,OrderRefundMapper refunds,RefundActionMapper actions,
            DataScopeService scopes,PaymentRefundClient payments,OrderNotificationOutboxWriter notifications,
            @Value("${internal.scope.token:}") String token,@Value("${notification.order-events.enabled:false}") boolean notifyEnabled) {
        this.orders=orders;this.items=items;this.refunds=refunds;this.actions=actions;this.scopes=scopes;
        this.payments=payments;this.notifications=notifications;this.token=token;this.notifyEnabled=notifyEnabled;
    }

    /** 先验证本人，再在锁内确认未接单/已接单分支；同一幂等键携带不同内容返回冲突。 */
    @Override @Transactional(rollbackFor=Exception.class)
    public RefundVO apply(Long userId,Long orderId,String key,RefundApplyRequest request) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,64}") || request==null || request.itemId()==null || request.itemId()<=0
                || request.quantity()==null || request.quantity()<=0 || request.reason()==null || request.reason().isBlank() || request.reason().length()>500) throw bad("退款申请参数无效");
        OrderDO order=ownLocked(userId,orderId);
        OrderRefundDO old=refunds.byRequest(userId,key);
        if(old!=null) {
            if(!orderId.equals(old.getOrderId()) || !request.itemId().equals(old.getItemId()) || !request.quantity().equals(old.getQuantity()) || !request.reason().trim().equals(old.getReason())) throw bad("该请求标识已用于其他退款申请");
            return RefundVO.from(old);
        }
        LocalDateTime now=LocalDateTime.now();
        RefundRules.requireApplicable(order.getStatus(),order.getCompletedAt(),now);
        // 锁内确认支付人、真实余额渠道和实付总额，拒绝仅凭订单状态对MOCK记录发钱。
        var fact=payments.fact(orderId,token);
        if(fact==null || !"COMMON_000".equals(fact.getCode()) || fact.getData()==null || !userId.equals(fact.getData().userId())
                || !"BALANCE".equals(fact.getData().channelCode()) || fact.getData().paidAmount()==null || fact.getData().paidAmount().compareTo(order.getTotalAmount())!=0) throw bad("实际支付记录尚未确认，暂不能申请退款");
        OrderItemDO item=items.selectById(request.itemId());
        if(item==null || !orderId.equals(item.getOrderId())) throw missing();
        // 已支付就是未接单；与接单竞争时同一订单行锁和版本让先提交的状态决定分支。
        boolean direct="已支付".equals(order.getStatus());
        OrderRefundDO row=new OrderRefundDO();row.setRefundNo(UUID.randomUUID().toString().replace("-",""));
        row.setRequestKey(key);row.setOrderId(orderId);row.setUserId(userId);row.setItemId(item.getItemId());
        row.setProductId(item.getProductId());row.setProductName(item.getProductNameSnapshot());row.setQuantity(request.quantity());
        row.setAmount(RefundRules.amount(item.getLineAmount(),item.getQuantity(),item.getRefundReservedQuantity(),refunds.committedAmount(item.getItemId()),request.quantity()));
        row.setReason(request.reason().trim());row.setOriginalStatus(order.getStatus());row.setStatus(direct?"APPROVED":"PENDING");
        row.setRestock(direct?1:0);row.setDueAt(now.plusHours(72));row.setNextAttemptAt(now);row.setAttempts(0);
        row.setCreatedAt(now);row.setUpdatedAt(now);row.setDecisionReason(direct?"未接单，平台直接退款":null);
        // 原子份数条件是最后一道上限防线，和退款单/审计在同库事务一起提交。
        requireOne(items.reserveRefund(item.getItemId(),orderId,request.quantity(),direct?request.quantity():0));
        requireOne(refunds.insert(row));requireOne(orders.bumpRefundVersion(orderId));
        audit(row,userId,"APPLY",row.getReason());
        if(direct) audit(row,null,"APPROVE","未接单，自动同意并回补库存");
        return RefundVO.from(row);
    }

    /** 本人历史按退款主键倒序分页，游标必须也属于本人及同一订单。 */
    @Override public RefundPageVO listOwn(Long userId,Long orderId,String cursor) {
        OrderDO order=orders.selectById(orderId);if(order==null || !userId.equals(order.getUserId())) throw missing();
        Long before=cursor(cursor);
        if(before!=null) {OrderRefundDO c=refunds.selectById(before);if(c==null || !userId.equals(c.getUserId()) || !orderId.equals(c.getOrderId())) throw missing();}
        return page(new QueryWrapper<OrderRefundDO>().eq("user_id",userId).eq("order_id",orderId).lt(before!=null,"refund_id",before));
    }

    /** 店主/食堂经理选择授权店铺；总经理具有平台范围时可查看所有争议，不按查后过滤伪造分页。 */
    @Override public RefundPageVO queue(Long operatorId,boolean general,boolean appeal,Long windowId,String cursor) {
        boolean platform=general && scopes.loadScopes(operatorId).isPlatformScope();
        if(windowId==null && !platform) throw bad("请选择权限范围内的店铺");
        if(windowId!=null && (windowId<=0 || !scopes.canAccessWindow(operatorId,windowId))) throw missing();
        Long before=cursor(cursor);
        if(before!=null) {OrderRefundDO c=refunds.selectById(before);if(c==null) throw missing();OrderDO o=orders.selectById(c.getOrderId());
            if(o==null || (windowId!=null && !windowId.equals(o.getWindowId())) || (!platform && !scopes.canAccessWindow(operatorId,o.getWindowId()))) throw missing();}
        QueryWrapper<OrderRefundDO> q=new QueryWrapper<OrderRefundDO>().eq("status",appeal?"APPEALED":"PENDING").lt(before!=null,"refund_id",before);
        if(windowId!=null) q.apply("order_id IN (SELECT order_id FROM orders WHERE window_id={0})",windowId);
        return page(q);
    }

    /** 商家只处理PENDING，经理只处理APPEALED；重复相同决定幂等，互相冲突的决定不能覆盖。 */
    @Override @Transactional(rollbackFor=Exception.class)
    public RefundVO decide(Long operatorId,boolean manager,boolean general,Long refundId,RefundDecisionRequest request) {
        if(request==null || request.approve()==null || request.restock()==null || request.reason()==null || request.reason().isBlank() || request.reason().length()>500) throw bad("审批参数无效");
        OrderRefundDO initial=refunds.selectById(refundId);if(initial==null) throw missing();
        OrderDO order=orders.lockRefundOrder(initial.getOrderId());
        if(order==null || (general ? !scopes.loadScopes(operatorId).isPlatformScope() : !scopes.canAccessWindow(operatorId,order.getWindowId()))) throw missing();
        OrderRefundDO row=refunds.lock(refundId);
        if(!request.approve() && request.restock()) throw bad("拒绝退款时不能回补库存");
        String target=request.approve()?"APPROVED":manager?"CLOSED":"REJECTED";
        boolean sameOutcome=target.equals(row.getStatus()) || (request.approve() && Set.of("PROCESSING","SUCCESS").contains(row.getStatus()));
        if(operatorId.equals(row.getDecidedBy()) && sameOutcome && request.reason().trim().equals(row.getDecisionReason()) && (request.restock()?1:0)==row.getRestock()) return RefundVO.from(row);
        if(!(manager?"APPEALED":"PENDING").equals(row.getStatus())) throw bad("退款状态已变化，请刷新后处理");
        if("PENDING".equals(row.getStatus()) && !LocalDateTime.now().isBefore(row.getDueAt())) throw bad("商家处理期限已到，系统将自动退款");
        boolean completed="已完成".equals(order.getStatus()) || "已完成".equals(row.getOriginalStatus());
        if(request.approve() && completed && request.restock()) throw bad("已完成订单退款不能回补库存");
        row.setRestock(request.approve() && request.restock()?1:0);row.setStatus(target);row.setDecisionReason(request.reason().trim());row.setDecidedBy(operatorId);
        if(request.approve()) requireOne(items.approveRefund(row.getItemId(),row.getQuantity()));
        else if(manager) requireOne(items.releaseRefund(row.getItemId(),row.getQuantity()));
        save(row);requireOne(orders.bumpRefundVersion(order.getOrderId()));
        audit(row,operatorId,request.approve()?"APPROVE":manager?"MANAGER_REJECT":"REJECT",request.reason().trim());
        if(!request.approve()) signal(order.getOrderId(),"退款申请被拒绝");
        return RefundVO.from(row);
    }

    /** 商家拒绝后由原学生升级争议；不改变金额/数量，也不自动把拒绝转为到账。 */
    @Override @Transactional(rollbackFor=Exception.class)
    public RefundVO appeal(Long userId,Long orderId,Long refundId) {
        ownLocked(userId,orderId);OrderRefundDO row=ownRefund(userId,orderId,refundId);
        if("APPEALED".equals(row.getStatus())) return RefundVO.from(row);
        if(!"REJECTED".equals(row.getStatus())) throw bad("只有商家拒绝的申请可以请求介入");
        row.setStatus("APPEALED");save(row);audit(row,userId,"APPEAL","请求食堂经理或总经理介入");return RefundVO.from(row);
    }

    /** 只能关闭尚未批准的申请；已开始入账后不允许撤回造成“到账但恢复制作”。 */
    @Override @Transactional(rollbackFor=Exception.class)
    public RefundVO withdraw(Long userId,Long orderId,Long refundId) {
        ownLocked(userId,orderId);OrderRefundDO row=ownRefund(userId,orderId,refundId);
        if("CLOSED".equals(row.getStatus())) return RefundVO.from(row);
        if(!Set.of("PENDING","REJECTED","APPEALED").contains(row.getStatus())) throw bad("退款已批准，不能撤回");
        if("PENDING".equals(row.getStatus()) && !LocalDateTime.now().isBefore(row.getDueAt())) throw bad("已超过商家处理期限，不能撤回自动退款");
        requireOne(items.releaseRefund(row.getItemId(),row.getQuantity()));row.setStatus("CLOSED");row.setDecisionReason("学生撤回申请");
        save(row);requireOne(orders.bumpRefundVersion(orderId));audit(row,userId,"WITHDRAW","学生撤回申请");return RefundVO.from(row);
    }

    /** 仅针对商家未处理的72小时到期单；经理介入没有未经确认的自动审批期限。 */
    @Override @Transactional(rollbackFor=Exception.class)
    public void expire(Long refundId) {
        OrderRefundDO initial=refunds.selectById(refundId);if(initial==null) return;
        OrderDO order=orders.lockRefundOrder(initial.getOrderId());if(order==null) throw missing();
        OrderRefundDO row=refunds.lock(refundId);
        if(!"PENDING".equals(row.getStatus()) || LocalDateTime.now().isBefore(row.getDueAt())) return;
        requireOne(items.approveRefund(row.getItemId(),row.getQuantity()));
        row.setStatus("APPROVED");row.setRestock(0);row.setDecisionReason("商家72小时未处理，自动同意退款，不自动回补已接单餐品");
        save(row);requireOne(orders.bumpRefundVersion(order.getOrderId()));audit(row,null,"AUTO_APPROVE",row.getDecisionReason());
    }

    /** 资金与必要库存均确认后才标成功；重复执行先读SUCCESS，不再次累计数量或发通知。 */
    @Override @Transactional(rollbackFor=Exception.class)
    public void complete(Long refundId) {
        OrderRefundDO initial=refunds.selectById(refundId);if(initial==null) throw missing();
        OrderDO order=orders.lockRefundOrder(initial.getOrderId());if(order==null) throw missing();
        OrderRefundDO row=refunds.lock(refundId);if("SUCCESS".equals(row.getStatus())) return;
        if(!Set.of("PROCESSING","APPROVED").contains(row.getStatus())) throw bad("退款状态不允许确认到账");
        requireOne(items.finishRefund(row.getItemId(),row.getQuantity()));row.setStatus("SUCCESS");save(row);
        if(orders.finishRefundedOrder(order.getOrderId())==0) requireOne(orders.bumpRefundVersion(order.getOrderId()));
        audit(row,null,"SUCCESS","资金及所需库存步骤已确认");signal(order.getOrderId(),"退款成功");
    }

    /** 行锁后校验归属，不存在与他人订单统一404，后续状态决策基于锁定事实。 */
    private OrderDO ownLocked(Long userId,Long orderId) {OrderDO o=orders.lockRefundOrder(orderId);if(o==null || !userId.equals(o.getUserId())) throw missing();return o;}
    /** 调用方已经持有订单锁；按一致顺序取得退款锁并同时校验用户与订单。 */
    private OrderRefundDO ownRefund(Long userId,Long orderId,Long refundId) {OrderRefundDO r=refunds.lock(refundId);if(r==null || !userId.equals(r.getUserId()) || !orderId.equals(r.getOrderId())) throw missing();return r;}
    /** 工作流变更清除旧租约，获批可立即执行；重试领取本身由独立原子SQL管理。 */
    private void save(OrderRefundDO row) {row.setUpdatedAt(LocalDateTime.now());row.setNextAttemptAt(LocalDateTime.now());requireOne(refunds.updateById(row));}
    /** 审批轨迹与业务修改同一事务，任何审计写入失败都回滚，不仅打印日志。 */
    private void audit(OrderRefundDO row,Long actor,String action,String detail) {RefundActionDO log=new RefundActionDO();log.setRefundId(row.getRefundId());log.setActorId(actor);log.setAction(action);log.setDetail(detail);log.setCreatedAt(LocalDateTime.now());requireOne(actions.insert(log));}
    /** 复用订单通知Outbox与应用唯一WebSocket，只在本地退款事务提交后通知学生刷新。 */
    private void signal(Long orderId,String status) {if(!notifyEnabled) return;OrderDO o=orders.selectById(orderId);notifications.record(OrderNotificationEvent.of(orderId,o.getVersion(),o.getUserId(),status));}
    /** 多读一条判断是否存在下一页，响应最多20条，不做深OFFSET或全量计数。 */
    private RefundPageVO page(QueryWrapper<OrderRefundDO> query) {List<OrderRefundDO> rows=refunds.selectList(query.orderByDesc("refund_id").last("LIMIT 21"));boolean more=rows.size()>20;List<RefundVO> result=rows.stream().limit(20).map(RefundVO::from).toList();return new RefundPageVO(result,more?result.getLast().refundId().toString():null,more);}
    /** 游标只接受正整数字符串并检查Long溢出；归属由各调用方进一步核验。 */
    private Long cursor(String cursor) {if(cursor==null) return null;try {if(!cursor.matches("[1-9][0-9]{0,18}")) throw bad("退款游标无效");return Long.valueOf(cursor);}catch(NumberFormatException error){throw bad("退款游标无效");}}
    /** 条件更新未命中视为冲突，由本地事务回滚，不能继续拼出部分成功。 */
    private void requireOne(int count) {if(count!=1) throw bad("退款数据已变化，请刷新重试");}
    /** 业务竞争返回409，由客户端刷新处理，避免把状态变化当成资金成功。 */
    private BusinessException bad(String text) {return new BusinessException(CommonErrorCode.CONFLICT,text);}
    /** 隐藏私有订单与退款是否存在，避免越权探测。 */
    private BusinessException missing() {return new BusinessException(CommonErrorCode.NOT_FOUND,"退款或订单不存在");}
}
