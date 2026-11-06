package com.hbue.ordering.order.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.order.model.OrderRefundDO;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;
/** 退款数据库访问：范围查询、锁定及短租约，无远程调用和业务审批。 */
@Mapper public interface OrderRefundMapper extends BaseMapper<OrderRefundDO> {
    /** 所有修改先锁订单后锁退款，保持相同锁顺序，避免相互等待。 */
    @Select("SELECT * FROM order_refunds WHERE refund_id=#{id} FOR UPDATE")
    OrderRefundDO lock(@Param("id") Long id);
    @Select("SELECT * FROM order_refunds WHERE user_id=#{userId} AND request_key=#{key}")
    OrderRefundDO byRequest(@Param("userId") Long userId,@Param("key") String key);
    /** 同一订单行锁内求有效退款金额，关闭的申请释放额度，其余状态防止累计超退。 */
    @Select("SELECT COALESCE(SUM(amount),0) FROM order_refunds WHERE item_id=#{itemId} AND status<>'CLOSED'")
    BigDecimal committedAmount(@Param("itemId") Long itemId);
    /** 每次最多读50个到期ID，有状态/时间组合索引，不扫描全部订单。 */
    @Select("SELECT refund_id FROM order_refunds WHERE status='PENDING' AND due_at<=CURRENT_TIMESTAMP ORDER BY due_at LIMIT 50")
    List<Long> expired();
    /** APPROVED为待执行任务；PROCESSING租约过期后可恢复，服务退出不会丢失退款。 */
    @Select("SELECT refund_id FROM order_refunds WHERE status IN ('APPROVED','PROCESSING') AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY next_attempt_at,refund_id LIMIT 10")
    List<Long> runnable();
    @Update("UPDATE order_refunds SET status='PROCESSING',attempts=attempts+1,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 60 SECOND),updated_at=CURRENT_TIMESTAMP WHERE refund_id=#{id} AND status IN ('APPROVED','PROCESSING') AND next_attempt_at<=CURRENT_TIMESTAMP")
    int claim(@Param("id") Long id);
}
