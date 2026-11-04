package com.hbue.ordering.payment.service.impl;
import com.hbue.ordering.payment.mapper.OrderPaymentGateMapper;
import com.hbue.ordering.payment.service.PaymentOrderGateService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
@Service
public class PaymentOrderGateServiceImpl implements PaymentOrderGateService {
    private final OrderPaymentGateMapper mapper;
    private final TransactionTemplate transactions;
    public PaymentOrderGateServiceImpl(OrderPaymentGateMapper mapper,org.springframework.transaction.PlatformTransactionManager manager) {
        this.mapper=mapper;this.transactions=new TransactionTemplate(manager);
    }
    public <T> T whileOpen(Long id,Supplier<T> action) {
        return transactions.execute(status->{
            mapper.ensure(id);
            if(!Integer.valueOf(0).equals(mapper.lock(id)))
                throw new BusinessException(CommonErrorCode.CONFLICT,"订单支付入口已关闭");
            return action.get();
        });
    }
    public void beginClose(Long id) {
        transactions.executeWithoutResult(status->{mapper.ensure(id);mapper.lock(id);mapper.beginClose(id);});
    }
}
