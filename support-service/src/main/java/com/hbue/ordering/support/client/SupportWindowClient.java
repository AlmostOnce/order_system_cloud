package com.hbue.ordering.support.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.client.vo.SupportWindowInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 窗口内部查询客户端。
 *
 * <p>用餐问题需要窗口所属餐厅来落实管理端数据范围，同时保存窗口名称快照。
 * 该接口不在网关放行范围内，只能由服务携带内部令牌调用。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@FeignClient(name = "product-service", contextId = "supportWindowClient")
public interface SupportWindowClient {

    /**
     * 查询窗口信息。
     *
     * @param windowId      窗口编号
     * @param internalToken 服务间共享密钥
     * @return 窗口信息；窗口不存在时 data 为 null
     */
    @GetMapping("/internal/windows/{windowId}")
    ApiResponse<SupportWindowInfoVO> getWindow(
            @PathVariable("windowId") Long windowId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
