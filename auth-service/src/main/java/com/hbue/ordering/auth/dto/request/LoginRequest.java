package com.hbue.ordering.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户登录请求 DTO。
 *
 * <p>只承载客户端提交的用户名和登录密码；字段格式由 Jakarta Validation
 * 校验，DTO 不负责查询用户、比对密码或签发 Token。密码不得写入日志。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Data
public class LoginRequest {

    /**
     * 登录用户名。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64 个字符")
    private String username;

    /**
     * 登录密码。
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过 64 个字符")
    private String password;

    /**
     * 图片验证码标识，由获取验证码接口下发。
     */
    @NotBlank(message = "验证码标识不能为空")
    @Size(max = 64, message = "验证码标识长度不能超过 64 个字符")
    private String captchaId;

    /**
     * 用户从图片中读取并输入的验证码字符。
     */
    @NotBlank(message = "验证码不能为空")
    @Size(max = 8, message = "验证码长度不能超过 8 个字符")
    private String captchaCode;
}
