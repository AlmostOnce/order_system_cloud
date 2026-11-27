package com.hbue.ordering.comment.client;

import com.hbue.ordering.comment.client.vo.CommentUserScopeVO;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 用户数据范围内部客户端。
 *
 * <p>评价的修改与删除需要判断"操作者是否覆盖该评价所属档口"。范围事实只存在于用户服务，
 * 评论服务按操作者实时查询，不在本地长期保存授权结果；短期缓存见
 * {@code com.hbue.ordering.comment.scope.DefaultDataScopeService}。</p>
 *
 * <p>该路径不在网关白名单内，只能携带服务间共享内部令牌调用，不接收客户端 JWT。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@FeignClient(name = "user-service", contextId = "commentUserScopeClient")
public interface CommentScopeClient {

    /**
     * 查询指定员工的数据范围。
     *
     * @param userId        员工内部用户编号
     * @param internalToken 服务间共享内部令牌
     * @return 平台、档口与餐厅范围；用户不存在时由用户服务返回业务失败
     */
    @GetMapping("/internal/users/{userId}/scopes")
    ApiResponse<CommentUserScopeVO> getScopes(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
