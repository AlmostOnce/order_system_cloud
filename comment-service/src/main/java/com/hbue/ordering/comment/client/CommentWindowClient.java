package com.hbue.ordering.comment.client;

import com.hbue.ordering.comment.client.vo.CommentWindowInfoVO;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 档口内部查询客户端。
 *
 * <p>餐厅经理的数据范围是餐厅编号集合，而评价只保存档口编号。判断餐厅经理能否管理某条评价时，
 * 需要先把档口换算为所属餐厅，这一步由商品服务提供权威映射。</p>
 *
 * <p>该路径不在网关白名单内，只能携带服务间共享内部令牌调用。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@FeignClient(name = "product-service", contextId = "commentWindowClient")
public interface CommentWindowClient {

    /**
     * 查询档口所属餐厅。
     *
     * @param windowId      档口编号
     * @param internalToken 服务间共享内部令牌
     * @return 档口信息；档口不存在时 {@code data} 为 null
     */
    @GetMapping("/internal/windows/{windowId}")
    ApiResponse<CommentWindowInfoVO> getWindow(
            @PathVariable("windowId") Long windowId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
