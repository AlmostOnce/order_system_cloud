package com.hbue.ordering.order.notification;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface CancellationNotificationTcc {
    @TwoPhaseBusinessAction(name = "cancelOrderNotificationV1", commitMethod = "confirm", rollbackMethod = "cancel")
    boolean prepare(@BusinessActionContextParameter("orderId") Long orderId,
            @BusinessActionContextParameter("orderVersion") Integer orderVersion,
            @BusinessActionContextParameter("recipientId") Long recipientId);
    boolean confirm(BusinessActionContext context);
    boolean cancel(BusinessActionContext context);
}
