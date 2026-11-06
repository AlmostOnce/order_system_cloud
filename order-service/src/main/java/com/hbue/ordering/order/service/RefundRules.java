package com.hbue.ordering.order.service;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;
/** 退款纯规则，按订单实付行金额分摊，不采信前端金额；申请额度包含其他处理中申请。 */
public final class RefundRules {
    private RefundRules() {}
    /** 只接收允许售后的状态；完成时间固定，不随售后更新时间变化，精确满72小时拒绝。 */
    public static void requireApplicable(String status,LocalDateTime completedAt,LocalDateTime now) {
        if(!Set.of("已支付","制作中","待取货","已完成").contains(status)) throw bad("当前订单不能申请退款");
        if("已完成".equals(status) && (completedAt==null || !now.isBefore(completedAt.plusHours(72)))) throw bad("已超过完成后72小时申请期限");
    }
    /** 最后剩余数量承接分摊余分；已有申请占用金额一起计入，任何组合均不超实付。 */
    public static BigDecimal amount(BigDecimal lineAmount,int total,int reserved,BigDecimal committed,int requested) {
        if(total<=0 || requested<=0 || reserved<0 || requested>total-reserved || lineAmount==null || lineAmount.signum()<=0)
            throw bad("可退款数量不足");
        BigDecimal remaining=lineAmount.subtract(committed);
        BigDecimal value=requested==total-reserved ? remaining : lineAmount.divide(BigDecimal.valueOf(total),2,RoundingMode.DOWN).multiply(BigDecimal.valueOf(requested));
        if(value.signum()<=0 || value.compareTo(remaining)>0) throw bad("可退款金额不足");
        return value.setScale(2,RoundingMode.UNNECESSARY);
    }
    private static BusinessException bad(String text) {return new BusinessException(CommonErrorCode.CONFLICT,text);}
}
