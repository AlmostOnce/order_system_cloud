package com.hbue.ordering.payment.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模拟渠道确认支付请求。
 *
 * <p>参数全部可选：缺省按支付成功全额处理；
 * paidAmount 专门用于演练金额不符场景。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
public class MockPayRequest {

    /** 期望结果：SUCCESS 或 FAILED，缺省 SUCCESS。 */
    @Pattern(regexp = "SUCCESS|FAILED", message = "结果仅支持 SUCCESS 或 FAILED")
    private String outcome;

    /** 实收金额原串，缺省为支付单应付金额。 */
    @Size(max = 16, message = "金额长度超限")
    private String paidAmount;
}
