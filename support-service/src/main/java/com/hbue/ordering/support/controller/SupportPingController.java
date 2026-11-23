package com.hbue.ordering.support.controller;

import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 反馈服务存活探测接口。
 *
 * <p>仅返回服务运行状态，不访问数据库或远程依赖，供网关、容器编排和本地联调
 * 快速确认服务已启动；安全配置对该路径匿名放行。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@RestController
@RequestMapping("/support")
public class SupportPingController {

    /**
     * 返回服务存活信息。
     *
     * @return 固定运行状态文本
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("support service is running!");
    }
}
