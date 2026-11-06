package com.hbue.ordering.order.task;
import com.hbue.ordering.order.feign.PaymentRefundClient;
import com.hbue.ordering.order.mapper.*;
import com.hbue.ordering.order.service.OrderRefundService;
import com.hbue.ordering.stock.api.client.StockRefundClient;
import com.hbue.ordering.stock.api.dto.StockRefundRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 仅扫描有索引的退款到期及待执行任务，不扫描全订单。每笔任务取得短租约后才远程调用，
 * 不持数据库事务跨服务等待；进程退出或超时由同一退款号重试，两个下游都独立幂等。
 */
@Component
public class OrderRefundTask {
    private static final Logger LOG=LoggerFactory.getLogger(OrderRefundTask.class);
    private final OrderRefundMapper refunds; private final OrderMapper orders; private final OrderRefundService workflow;
    private final PaymentRefundClient payments; private final StockRefundClient stocks; private final String token;
    public OrderRefundTask(OrderRefundMapper refunds,OrderMapper orders,OrderRefundService workflow,PaymentRefundClient payments,
            StockRefundClient stocks,@Value("${internal.scope.token:}") String token) {
        this.refunds=refunds;this.orders=orders;this.workflow=workflow;this.payments=payments;this.stocks=stocks;this.token=token;
    }
    /** 审批到期转换是短本地事务；单笔错误不阻塞后续到期申请。 */
    @Scheduled(fixedDelay=30000,initialDelay=15000)
    public void expire() {for(Long id:refunds.expired()) {try {workflow.expire(id);}catch(RuntimeException e){LOG.warn("退款自动审批暂未完成，refundId={}，错误类型={}",id,e.getClass().getSimpleName());}}}
    /** 批量上限固定为10，单笔失败不阻塞同批后续项，查询由状态/租约索引承接。 */
    @Scheduled(fixedDelay=5000,initialDelay=15000)
    public void execute() {for(Long id:refunds.runnable()) executeOne(id);}
    /** 先退资金，再处理批准的库存回补；任一步未确认都保留PROCESSING，绝不标成成功。 */
    public void executeOne(Long id) {
        try {
            // 领取失败也由本笔捕获，不中断同批其他退款；未领到租约不能发起远程写入。
            if(refunds.claim(id)!=1) return;
            var row=refunds.selectById(id);var order=orders.selectById(row.getOrderId());
            if(order==null) throw new IllegalStateException("退款订单缺失");
            var paid=payments.refund(row.getOrderId(),token,new PaymentRefundClient.Command(row.getRefundNo(),row.getAmount()));
            if(paid==null || !"COMMON_000".equals(paid.getCode()) || !row.getRefundNo().equals(paid.getData())) throw new IllegalStateException("退款资金未确认");
            if(Integer.valueOf(1).equals(row.getRestock())) {
                var restored=stocks.restore(token,new StockRefundRequest(order.getOrderNo(),row.getRefundNo(),order.getWindowId(),row.getProductId(),row.getQuantity()));
                if(restored==null || !"COMMON_000".equals(restored.getCode()) || !row.getRefundNo().equals(restored.getData())) throw new IllegalStateException("退款库存未确认");
            }
            workflow.complete(id);
        } catch(RuntimeException e) {
            // 不把远程错误正文或令牌写日志；租约到期自动重试，已入账步骤不会重复加款。
            LOG.warn("退款执行待重试，refundId={}，错误类型={}",id,e.getClass().getSimpleName());
        }
    }
}
