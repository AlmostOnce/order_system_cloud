package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 支付密码设置请求。
 *
 * <p>首次设置（尚未持有支付密码）时必须提供正确的登录密码作为身份二次确认；
 * 修改已有支付密码时必须提供原支付密码。服务端根据当前账户是否已有支付密码
 * 决定使用哪种凭据，不能由客户端指定模式。密码只用于本次校验并且不得写入日志。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Data
public class PayPasswordSetRequest {

    /**
     * 登录密码，仅首次设置支付密码时必填。
     */
    private String loginPassword;

    /**
     * 原支付密码，仅修改已有支付密码时必填。
     */
    private String oldPayPassword;

    /**
     * 新支付密码。
     */
    @NotBlank(message = "支付密码不能为空")
    @Size(min = 6, max = 20, message = "支付密码长度必须为 6 到 20 位")
    private String newPayPassword;
}
